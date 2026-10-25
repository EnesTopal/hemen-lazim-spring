package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.Device;
import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.model.enums.Units;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import com.tpl.hemen_lazim.services.FirebaseService.LocalizedNotification;
import com.tpl.hemen_lazim.services.FirebaseService.SendResult;
import com.tpl.hemen_lazim.util.ChatRooms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SUPPLIER = UUID.randomUUID();
    private static final String TOKEN = "owner-device";

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private MaterialRequestRepository requestRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void notifiesTheRequestOwnerWithRoomForTheChat() {
        givenRequest(RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(1)));
        givenOwnerDevice(SendResult.SENT);

        assertTrue(notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(firebaseService).sendNotification(eq(TOKEN), any(), data.capture());
        assertEquals(ChatRooms.roomIdFor(SUPPLIER, OWNER), data.getValue().get("roomId"));
        assertEquals(SUPPLIER.toString(), data.getValue().get("otherUserId"));
    }

    @Test
    void notificationTextIsLeftToThePhoneLanguage() {
        givenRequest(RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(1)));
        givenOwnerDevice(SendResult.SENT);

        notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER);

        ArgumentCaptor<LocalizedNotification> notification = ArgumentCaptor.forClass(LocalizedNotification.class);
        verify(firebaseService).sendNotification(eq(TOKEN), notification.capture(), any());
        assertEquals("notif_supply_offer_title", notification.getValue().titleKey());
        assertEquals("notif_supply_offer_body", notification.getValue().bodyKey());
        assertEquals(List.of("Un"), notification.getValue().bodyArgs());
    }

    @Test
    void secondOfferFromTheSameSupplierDoesNotPushAgain() {
        givenRequest(RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(1)));
        givenOwnerDevice(SendResult.SENT);

        assertTrue(notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));
        assertFalse(notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));

        verify(firebaseService, times(1)).sendNotification(anyString(), any(), any());
    }

    @Test
    void undeliveredOfferCanBeTriedAgain() {
        givenRequest(RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(1)));
        givenOwnerDevice(SendResult.FAILED);

        assertTrue(notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));
        assertTrue(notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));

        verify(firebaseService, times(2)).sendNotification(anyString(), any(), any());
    }

    @Test
    void transientFailureKeepsTheDeviceEnabled() {
        givenRequest(RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(1)));
        givenOwnerDevice(SendResult.FAILED);

        notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER);

        verify(deviceService, never()).disableDevice(anyString());
    }

    @Test
    void invalidTokenDisablesTheDevice() {
        givenRequest(RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(1)));
        givenOwnerDevice(SendResult.INVALID_TOKEN);

        notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER);

        verify(deviceService).disableDevice(TOKEN);
    }

    @Test
    void ownRequestIsRejected() {
        givenRequest(RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(1)));

        assertCode(ErrorCode.OFFER_ON_OWN_REQUEST, () -> notificationService.sendSupplyOfferNotification(REQUEST_ID, OWNER));
        verifyNoInteractions(deviceService, firebaseService);
    }

    @Test
    void closedRequestIsRejected() {
        givenRequest(RequestStatus.COMPLETED, Instant.now().plus(Duration.ofHours(1)));

        assertCode(ErrorCode.REQUEST_NOT_OPEN, () -> notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));
        verifyNoInteractions(deviceService, firebaseService);
    }

    @Test
    void expiredRequestIsRejectedBeforeTheExpiryJobRuns() {
        givenRequest(RequestStatus.OPEN, Instant.now().minus(Duration.ofMinutes(1)));

        assertCode(ErrorCode.REQUEST_NOT_OPEN, () -> notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));
        verifyNoInteractions(deviceService, firebaseService);
    }

    @Test
    void missingRequestIsNotFound() {
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertCode(ErrorCode.REQUEST_NOT_FOUND, () -> notificationService.sendSupplyOfferNotification(REQUEST_ID, SUPPLIER));
    }

    private void givenRequest(RequestStatus status, Instant expiresAt) {
        User owner = new User();
        owner.setId(OWNER);

        MaterialRequest request = new MaterialRequest();
        request.setId(REQUEST_ID);
        request.setRequester(owner);
        request.setTitle("Un");
        request.setQuantity(2);
        request.setUnits(Units.KG);
        request.setStatus(status);
        request.setExpiresAt(expiresAt);

        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    }

    private void givenOwnerDevice(SendResult result) {
        Device device = new Device();
        device.setFcmToken(TOKEN);
        when(deviceService.getEnabledDevicesForUser(OWNER)).thenReturn(List.of(device));
        when(firebaseService.sendNotification(eq(TOKEN), any(), any())).thenReturn(result);
    }

    private static void assertCode(ErrorCode expected, Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(expected, e.getCode());
    }
}
