package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.Device;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.repositories.DeviceRepository;
import com.tpl.hemen_lazim.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public DeviceService(DeviceRepository deviceRepository, UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Register or update a device with FCM token
     */
    @Transactional
    public Device registerDevice(UUID userId, String fcmToken, String deviceType, String deviceName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if device with this token already exists
        Device device = deviceRepository.findByFcmToken(fcmToken)
                .orElse(new Device());

        device.setUser(user);
        device.setFcmToken(fcmToken);
        device.setDeviceType(deviceType != null ? deviceType : "ANDROID");
        device.setDeviceName(deviceName != null ? deviceName : "Unknown Device");
        device.setPushEnabled(true);
        device.setLastSeenAt(Instant.now());

        Device saved = deviceRepository.save(device);
        log.info("Registered/updated device {} for user {}", saved.getId(), userId);
        return saved;
    }

    /**
     * Get all enabled devices for a user
     */
    public List<Device> getEnabledDevicesForUser(UUID userId) {
        return deviceRepository.findAllByUserIdAndPushEnabled(userId, true);
    }

    /**
     * Disable push notifications for a specific device
     */
    @Transactional
    public void disableDevice(String fcmToken) {
        deviceRepository.disableByToken(fcmToken);
        log.info("Disabled device with token: {}", fcmToken);
    }

    /**
     * Update last seen time for a device
     */
    @Transactional
    public void updateLastSeen(String fcmToken) {
        deviceRepository.findByFcmToken(fcmToken).ifPresent(device -> {
            device.setLastSeenAt(Instant.now());
            deviceRepository.save(device);
        });
    }

    /**
     * Remove old/inactive devices (older than specified days)
     */
    @Transactional
    public void cleanupInactiveDevices(int daysInactive) {
        Instant cutoffDate = Instant.now().minusSeconds(daysInactive * 24L * 60L * 60L);
        List<Device> allDevices = deviceRepository.findAll();
        
        for (Device device : allDevices) {
            if (device.getLastSeenAt() != null && device.getLastSeenAt().isBefore(cutoffDate)) {
                deviceRepository.delete(device);
                log.info("Removed inactive device: {}", device.getId());
            }
        }
    }
}

