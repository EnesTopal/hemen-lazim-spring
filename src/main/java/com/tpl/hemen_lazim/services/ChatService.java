package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.DTOs.RoomUnread;
import com.tpl.hemen_lazim.model.Message;
import com.tpl.hemen_lazim.repositories.UserRepository;
import com.tpl.hemen_lazim.repository.MessageRepository;
import com.tpl.hemen_lazim.util.ChatRooms;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Birebir sohbet. Her işlem çağıranın kimliğini (me) oturumdan alır. Oda, gönderici ve
 * alıcı bilgisi istekte gelse de ona güvenilmez; bir odayı yalnızca iki üyesi okuyabilir.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** messages.content sütunu VARCHAR(255): daha uzunu veritabanında 500 hatasına dönüyordu. */
    public static final int MAX_CONTENT_LENGTH = 255;

    private static final int MAX_SYNC_BATCH = 200;
    private static final int MAX_HISTORY_PAGE = 100;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public Message send(UUID me, String recipientId, String content) {
        UUID recipient = requireOtherUser(me, recipientId);
        if (content == null || content.isBlank()) {
            throw new ApiException(ErrorCode.MESSAGE_EMPTY);
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(ErrorCode.MESSAGE_TOO_LONG,
                    "Message must be at most " + MAX_CONTENT_LENGTH + " characters");
        }

        Message saved = messageRepository.save(Message.builder()
                .roomId(ChatRooms.roomIdFor(me, recipient))
                .senderId(me.toString())
                .recipientId(recipient.toString())
                .content(content)
                .serverTimestamp(Instant.now().toEpochMilli())
                .read(false)
                .build());
        log.info("Saved message {} from {} to {} in room {}",
                saved.getId(), saved.getSenderId(), saved.getRecipientId(), saved.getRoomId());

        // Alıcı o an bağlıysa mesaj anında düşer; değilse açılışta /sync ve /history ile alır
        messagingTemplate.convertAndSendToUser(saved.getRecipientId(), "/queue/messages", saved);
        return saved;
    }

    public String roomWith(UUID me, String otherUserId) {
        return ChatRooms.roomIdFor(me, requireOtherUser(me, otherUserId));
    }

    /**
     * Sayfalı geçmiş: beforeId yoksa en yeni "limit" mesaj, varsa o id'den eski "limit" mesaj.
     * Yanıt her zaman eskiden yeniye sıralıdır.
     */
    public List<Message> history(UUID me, String roomId, Long beforeId, int limit) {
        requireMember(me, roomId);
        Pageable page = PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_HISTORY_PAGE));
        List<Message> newestFirst = (beforeId == null)
                ? messageRepository.findByRoomIdOrderByIdDesc(roomId, page)
                : messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, page);

        List<Message> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    /** Kopan bağlantıdan sonra kaçırılan mesajlar. */
    public List<Message> sync(UUID me, String roomId, Long lastReceivedMessageId) {
        requireMember(me, roomId);
        // Üst sınır: istemci 0 ile gelirse (ör. geçmiş yüklenemediyse) tüm odayı tek seferde çekmesin
        return messageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, lastReceivedMessageId,
                PageRequest.of(0, MAX_SYNC_BATCH));
    }

    /** Sohbet listesi: kullanıcının her odasındaki son mesaj. */
    public List<Message> latestPerRoom(UUID me) {
        return messageRepository.findLatestMessagesForUser(me.toString());
    }

    public Map<String, Long> unreadCounts(UUID me) {
        return messageRepository.countUnreadByRoom(me.toString()).stream()
                .collect(Collectors.toMap(RoomUnread::getRoomId, RoomUnread::getUnreadCount));
    }

    /** Sorgu yalnızca alıcısı me olan mesajlara dokunuyor, üyelik denetimine gerek yok. */
    public void markRoomRead(UUID me, String roomId) {
        int updated = messageRepository.markRoomRead(roomId, me.toString());
        log.info("Marked {} messages read in room {} for {}", updated, roomId, me);
    }

    private UUID requireOtherUser(UUID me, String userId) {
        UUID other = parseUserId(userId);
        if (other.equals(me)) {
            throw new ApiException(ErrorCode.CHAT_WITH_SELF);
        }
        if (!userRepository.existsById(other)) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
        return other;
    }

    private static UUID parseUserId(String userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.INVALID_USER_ID);
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_USER_ID);
        }
    }

    private static void requireMember(UUID me, String roomId) {
        if (!ChatRooms.isMember(roomId, me)) {
            throw new ApiException(ErrorCode.NOT_CHAT_MEMBER);
        }
    }
}
