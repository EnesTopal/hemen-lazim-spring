package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.Device;
import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.RequestLimits;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import com.tpl.hemen_lazim.util.ChatRooms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // Uygulamanın strings.xml'indeki anahtarlar (her dilde ayrı metin); adları değiştirilmemeli
    static final String SUPPLY_OFFER_TITLE_KEY = "notif_supply_offer_title";
    static final String SUPPLY_OFFER_BODY_KEY = "notif_supply_offer_body";

    private final FirebaseService firebaseService;
    private final DeviceService deviceService;
    private final MaterialRequestRepository requestRepository;

    /**
     * Bir tedarikçi bir talep için talep açık kaldığı sürece tek bildirim gönderebilir; değer
     * talebin bitiş zamanı. Bellekte durduğu için sunucu yeniden başlayınca sıfırlanır.
     * Kalıcı teklif kaydı (offers tablosu) gelene kadar spam'i kesmeye yetiyor.
     */
    private final Map<OfferKey, Instant> notifiedOffers = new ConcurrentHashMap<>();

    private record OfferKey(UUID supplierId, UUID requestId) {}

    public NotificationService(FirebaseService firebaseService,
            DeviceService deviceService,
            MaterialRequestRepository requestRepository) {
        this.firebaseService = firebaseService;
        this.deviceService = deviceService;
        this.requestRepository = requestRepository;
    }

    /**
     * Send supply offer notification to the requester on all their devices.
     *
     * @return true if the requester was notified now, false if this supplier had already
     *         notified them about this request (no push is sent again)
     */
    public boolean sendSupplyOfferNotification(UUID requestId, UUID supplierId) {
        MaterialRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.REQUEST_NOT_FOUND));

        // Talep sahibi istekten değil talebin kendisinden okunur; eskiden gövdedeki
        // requesterId'ye güveniliyordu ve herkese bildirim atılabiliyordu.
        UUID requesterId = request.getRequester().getId();
        if (requesterId.equals(supplierId)) {
            throw new ApiException(ErrorCode.OFFER_ON_OWN_REQUEST);
        }

        Instant now = Instant.now();
        Instant expiresAt = request.getExpiresAt();
        if (request.getStatus() != RequestStatus.OPEN || (expiresAt != null && !expiresAt.isAfter(now))) {
            throw new ApiException(ErrorCode.REQUEST_NOT_OPEN);
        }

        OfferKey offer = new OfferKey(supplierId, requestId);
        Instant forgetAt = expiresAt != null ? expiresAt : now.plus(RequestLimits.MAX_DURATION_MIN, ChronoUnit.MINUTES);
        // putIfAbsent: aynı anda gelen iki dokunuştan yalnızca biri push gönderir
        if (notifiedOffers.putIfAbsent(offer, forgetAt) != null) {
            log.info("Supplier {} already notified the requester of request {}", supplierId, requestId);
            return false;
        }

        // Metin telefonda, uygulamanın dilinde oluşur; argüman yalnızca talebin başlığı
        var notification = new FirebaseService.LocalizedNotification(
                SUPPLY_OFFER_TITLE_KEY, List.of(), SUPPLY_OFFER_BODY_KEY, List.of(request.getTitle()));

        // Add data payload for navigation
        Map<String, String> data = new HashMap<>();
        data.put("type", "supply_offer");
        data.put("requestId", requestId.toString());
        data.put("supplierId", supplierId.toString());
        // Add fields expected by MainActivity
        data.put("roomId", ChatRooms.roomIdFor(supplierId, requesterId));
        data.put("otherUserId", supplierId.toString()); // For the requester, the other user is the supplier

        int delivered = pushToUser(requesterId, notification, data);
        if (delivered == 0) {
            // Hiçbir cihaza ulaşmadı (cihaz yok ya da FCM hatası): sonraki dokunuş yeniden denesin
            notifiedOffers.remove(offer);
        }

        log.info("Sent supply offer notification for request {} to {} devices of requester {}",
                requestId, delivered, requesterId);
        return true;
    }

    /** Talebi biten teklif kayıtlarını unutur; o taleplere zaten yeni teklif verilemiyor. */
    @Scheduled(fixedDelay = 3_600_000)
    public void forgetFinishedOffers() {
        Instant now = Instant.now();
        notifiedOffers.values().removeIf(forgetAt -> forgetAt.isBefore(now));
    }

    /** Sends to all enabled devices of the user and returns how many accepted the push. */
    private int pushToUser(UUID userId, FirebaseService.LocalizedNotification notification, Map<String, String> data) {
        List<Device> devices = deviceService.getEnabledDevicesForUser(userId);

        if (devices.isEmpty()) {
            log.warn("User {} does not have any enabled devices. Cannot send notification.", userId);
            return 0;
        }

        int delivered = 0;
        List<String> invalidTokens = new ArrayList<>();

        for (Device device : devices) {
            switch (firebaseService.sendNotification(device.getFcmToken(), notification, data)) {
                case SENT -> delivered++;
                case INVALID_TOKEN -> invalidTokens.add(device.getFcmToken());
                case FAILED -> {
                    // Geçici hata: cihaz açık kalır. Eskiden burada da kapatılıyordu ve token
                    // yalnızca girişte kaydedildiği için kullanıcıya bir daha push gitmiyordu.
                }
            }
        }

        // Remove invalid tokens from database
        for (String invalidToken : invalidTokens) {
            try {
                deviceService.disableDevice(invalidToken);
                log.info("Disabled an invalid FCM token of user {}", userId);
            } catch (Exception e) {
                log.error("Failed to disable an invalid FCM token of user {}", userId, e);
            }
        }

        return delivered;
    }
}
