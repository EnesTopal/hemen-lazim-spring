package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.RequestLimits;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.DTOs.MaterialRequestDTO;
import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import com.tpl.hemen_lazim.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** İlanlarım ekranındaki eylemler: uzat, iptal, tamamla. */
@ExtendWith(MockitoExtension.class)
class MaterialRequestActionsTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final Duration HOUR = Duration.ofMinutes(60);

    @Mock
    private MaterialRequestRepository materialRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MaterialRequestService service;

    @Test
    void extendAddsMinutesToTheCurrentEnd() {
        Instant end = Instant.now().plus(Duration.ofMinutes(30));
        MaterialRequest request = givenRequest(OWNER, RequestStatus.OPEN, end);

        MaterialRequestDTO extended = service.extend(OWNER, REQUEST_ID, 60);

        assertEquals(end.plus(HOUR), extended.expiresAt());
        assertEquals(extended.expiresAt(), request.getExpiresAt());
    }

    @Test
    void extendCountsFromNowWhenTheEndHasPassed() {
        // Süre dolmuş ama dakikalık iş henüz EXPIRED yapmamış
        givenRequest(OWNER, RequestStatus.OPEN, Instant.now().minus(Duration.ofMinutes(5)));

        Instant before = Instant.now();
        MaterialRequestDTO extended = service.extend(OWNER, REQUEST_ID, 60);

        assertBetween(before.plus(HOUR), Instant.now().plus(HOUR), extended.expiresAt());
    }

    @Test
    void extendNeverGoesBeyondTheLongestDuration() {
        givenRequest(OWNER, RequestStatus.OPEN, Instant.now().plus(Duration.ofHours(23)));

        Instant before = Instant.now();
        MaterialRequestDTO extended = service.extend(OWNER, REQUEST_ID, 120);

        Duration longest = Duration.ofMinutes(RequestLimits.MAX_DURATION_MIN);
        assertBetween(before.plus(longest), Instant.now().plus(longest), extended.expiresAt());
    }

    @Test
    void closedRequestCannotBeExtendedCancelledOrCompleted() {
        givenRequest(OWNER, RequestStatus.COMPLETED, Instant.now().plus(HOUR));

        assertCode(ErrorCode.REQUEST_NOT_OPEN, () -> service.extend(OWNER, REQUEST_ID, 60));
        assertCode(ErrorCode.REQUEST_NOT_OPEN, () -> service.cancel(OWNER, REQUEST_ID));
        assertCode(ErrorCode.REQUEST_NOT_OPEN, () -> service.complete(OWNER, REQUEST_ID));
    }

    @Test
    void onlyTheOwnerCanChangeTheRequest() {
        givenRequest(OWNER, RequestStatus.OPEN, Instant.now().plus(HOUR));
        User stranger = new User();
        stranger.setId(STRANGER);
        when(userRepository.findById(STRANGER)).thenReturn(Optional.of(stranger));

        assertCode(ErrorCode.NOT_REQUEST_OWNER, () -> service.extend(STRANGER, REQUEST_ID, 60));
        assertCode(ErrorCode.NOT_REQUEST_OWNER, () -> service.cancel(STRANGER, REQUEST_ID));
    }

    @Test
    void missingRequestIsNotFound() {
        when(materialRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertCode(ErrorCode.REQUEST_NOT_FOUND, () -> service.extend(OWNER, REQUEST_ID, 60));
        assertCode(ErrorCode.REQUEST_NOT_FOUND, () -> service.cancel(OWNER, REQUEST_ID));
    }

    @Test
    void cancelAndCompleteCloseAnOpenRequest() {
        MaterialRequest request = givenRequest(OWNER, RequestStatus.OPEN, Instant.now().plus(HOUR));

        service.complete(OWNER, REQUEST_ID);

        assertEquals(RequestStatus.COMPLETED, request.getStatus());
    }

    private MaterialRequest givenRequest(UUID ownerId, RequestStatus status, Instant expiresAt) {
        User owner = new User();
        owner.setId(ownerId);

        MaterialRequest request = new MaterialRequest();
        request.setId(REQUEST_ID);
        request.setRequester(owner);
        request.setTitle("un");
        request.setCategory(Category.YEMEK_MALZEMESI);
        request.setLatitude(41.0);
        request.setLongitude(29.0);
        request.setStatus(status);
        request.setExpiresAt(expiresAt);

        when(materialRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        // Yetkisiz kullanıcı testinde sahip hiç sorgulanmıyor
        lenient().when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        return request;
    }

    private static void assertBetween(Instant from, Instant to, Instant actual) {
        assertTrue(!actual.isBefore(from) && !actual.isAfter(to), actual + " not in [" + from + ", " + to + "]");
    }

    private static void assertCode(ErrorCode expected, Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(expected, e.getCode());
    }
}
