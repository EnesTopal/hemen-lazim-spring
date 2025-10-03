package com.tpl.hemen_lazim.controllers;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.DeviceRegisterReq;
import com.tpl.hemen_lazim.model.DTOs.SupplyOfferNotificationDTO;
import com.tpl.hemen_lazim.security.SecurityUtils;
import com.tpl.hemen_lazim.services.DeviceService;
import com.tpl.hemen_lazim.services.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final DeviceService deviceService;

    public NotificationController(NotificationService notificationService, DeviceService deviceService) {
        this.notificationService = notificationService;
        this.deviceService = deviceService;
    }

    /**
     * Register/update device with FCM token
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<ApiResponse<String>> registerDevice(@Valid @RequestBody DeviceRegisterReq body) {
        var userId = SecurityUtils.currentUserId();
        
        deviceService.registerDevice(
            userId, 
            body.fcmToken(), 
            body.deviceType(), 
            body.deviceName()
        );
        
        return ResponseEntity.ok(new ApiResponse<>("Device registered successfully"));
    }

    /**
     * Send supply offer notification
     */
    @PostMapping("/supply-offer")
    public ResponseEntity<ApiResponse<String>> sendSupplyOfferNotification(
            @Valid @RequestBody SupplyOfferNotificationDTO body) {
        var supplierId = SecurityUtils.currentUserId();
        
        notificationService.sendSupplyOfferNotification(
                body.requestId(),
                body.requesterId(),
                supplierId
        );
        
        return ResponseEntity.ok(new ApiResponse<>("Notification sent successfully"));
    }
}

