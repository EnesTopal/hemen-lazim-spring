package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.Device;
import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final FirebaseService firebaseService;
    private final DeviceService deviceService;
    private final MaterialRequestRepository requestRepository;

    public NotificationService(FirebaseService firebaseService,
                             DeviceService deviceService,
                             MaterialRequestRepository requestRepository) {
        this.firebaseService = firebaseService;
        this.deviceService = deviceService;
        this.requestRepository = requestRepository;
    }

    /**
     * Send supply offer notification to the requester on all their devices
     */
    public void sendSupplyOfferNotification(UUID requestId, UUID requesterId, UUID supplierId) {
        try {
            // Get the material request
            MaterialRequest request = requestRepository.findById(requestId)
                    .orElseThrow(() -> new IllegalArgumentException("Request not found"));

            // Get all enabled devices for the requester
            List<Device> devices = deviceService.getEnabledDevicesForUser(requesterId);

            if (devices.isEmpty()) {
                log.warn("Requester {} does not have any enabled devices. Cannot send notification.", requesterId);
                return;
            }

            // Build notification message with quantity
            String quantity = request.getQuantity() != null ? request.getQuantity().toString() : "";
            String unit = request.getUnits() != null ? request.getUnits().name() : "";
            String title = request.getTitle();
            
            String notificationBody = String.format("Birisi %s %s %s ürün isteğinizi karşılamak istiyor", 
                    quantity, unit, title).trim().replaceAll("\\s+", " ");

            // Add data payload for navigation
            Map<String, String> data = new HashMap<>();
            data.put("type", "supply_offer");
            data.put("requestId", requestId.toString());
            data.put("supplierId", supplierId.toString());

            // Send notification to all devices and track invalid tokens
            int successCount = 0;
            List<String> invalidTokens = new ArrayList<>();
            
            for (Device device : devices) {
                boolean success = firebaseService.sendNotification(
                    device.getFcmToken(), 
                    "Yeni Tedarik Teklifi", 
                    notificationBody, 
                    data
                );
                
                if (success) {
                    successCount++;
                } else {
                    // Token is invalid, mark for removal
                    invalidTokens.add(device.getFcmToken());
                }
            }

            // Remove invalid tokens from database
            for (String invalidToken : invalidTokens) {
                try {
                    deviceService.disableDevice(invalidToken);
                    log.info("Disabled invalid FCM token: {}", invalidToken);
                } catch (Exception e) {
                    log.error("Failed to disable invalid token: {}", invalidToken, e);
                }
            }

            log.info("Sent supply offer notification for request {} to {} devices of requester {} ({} invalid tokens removed)", 
                    requestId, successCount, requesterId, invalidTokens.size());
        } catch (Exception e) {
            log.error("Failed to send supply offer notification", e);
            throw new RuntimeException("Failed to send notification: " + e.getMessage());
        }
    }

    /**
     * Send a custom notification to all user's devices
     */
    public void sendCustomNotification(UUID userId, String title, String body, Map<String, String> data) {
        try {
            List<Device> devices = deviceService.getEnabledDevicesForUser(userId);

            if (devices.isEmpty()) {
                log.warn("User {} does not have any enabled devices. Cannot send notification.", userId);
                return;
            }

            int successCount = 0;
            List<String> invalidTokens = new ArrayList<>();
            
            for (Device device : devices) {
                boolean success = firebaseService.sendNotification(
                    device.getFcmToken(), 
                    title, 
                    body, 
                    data
                );
                
                if (success) {
                    successCount++;
                } else {
                    invalidTokens.add(device.getFcmToken());
                }
            }

            // Remove invalid tokens
            for (String invalidToken : invalidTokens) {
                try {
                    deviceService.disableDevice(invalidToken);
                    log.info("Disabled invalid FCM token: {}", invalidToken);
                } catch (Exception e) {
                    log.error("Failed to disable invalid token: {}", invalidToken, e);
                }
            }

            log.info("Sent custom notification to {} devices of user {} ({} invalid tokens removed)", 
                    successCount, userId, invalidTokens.size());
        } catch (Exception e) {
            log.error("Failed to send custom notification", e);
            throw new RuntimeException("Failed to send notification: " + e.getMessage());
        }
    }
}
