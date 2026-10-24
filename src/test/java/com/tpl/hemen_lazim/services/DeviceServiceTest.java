package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.Device;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.repositories.DeviceRepository;
import com.tpl.hemen_lazim.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final String TOKEN = "fcm-token";

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void unregisterDeletesOwnDevice() {
        UUID ownerId = UUID.randomUUID();
        Device device = deviceOf(ownerId);
        when(deviceRepository.findByFcmToken(TOKEN)).thenReturn(Optional.of(device));

        deviceService.unregisterDevice(ownerId, TOKEN);

        verify(deviceRepository).delete(device);
    }

    @Test
    void unregisterLeavesAnotherUsersDeviceAlone() {
        Device device = deviceOf(UUID.randomUUID());
        when(deviceRepository.findByFcmToken(TOKEN)).thenReturn(Optional.of(device));

        deviceService.unregisterDevice(UUID.randomUUID(), TOKEN);

        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void unregisterIgnoresUnknownToken() {
        when(deviceRepository.findByFcmToken(TOKEN)).thenReturn(Optional.empty());

        deviceService.unregisterDevice(UUID.randomUUID(), TOKEN);

        verify(deviceRepository, never()).delete(any());
    }

    private static Device deviceOf(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        Device device = new Device();
        device.setUser(owner);
        device.setFcmToken(TOKEN);
        return device;
    }
}
