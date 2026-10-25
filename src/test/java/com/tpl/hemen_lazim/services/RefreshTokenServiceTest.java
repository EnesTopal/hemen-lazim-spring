package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.RefreshToken;
import com.tpl.hemen_lazim.repositories.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repo;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repo, 30);
    }

    @Test
    void activeTokenIsReturned() {
        RefreshToken token = givenToken(false, Instant.now().plus(Duration.ofDays(1)));

        assertSame(token, service.validateActive("t"));
    }

    @Test
    void eachRejectionHasItsCode() {
        when(repo.findByToken("t")).thenReturn(Optional.empty());
        assertCode(ErrorCode.REFRESH_NOT_FOUND);

        givenToken(true, Instant.now().plus(Duration.ofDays(1)));
        assertCode(ErrorCode.REFRESH_REVOKED);

        givenToken(false, Instant.now().minus(Duration.ofMinutes(1)));
        assertCode(ErrorCode.REFRESH_EXPIRED);
    }

    @Test
    void rejectionStatusesStayWhatTheAppTreatsAsLoggedOut() {
        // Uygulamanın TokenAuthenticator'ı 400/401/403/409'u "oturum bitti" sayıyor
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_NOT_FOUND.status());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.REFRESH_REVOKED.status());
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_EXPIRED.status());
    }

    private RefreshToken givenToken(boolean revoked, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUserId(UUID.randomUUID());
        token.setToken("t");
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        when(repo.findByToken("t")).thenReturn(Optional.of(token));
        return token;
    }

    private void assertCode(ErrorCode expected) {
        ApiException e = assertThrows(ApiException.class, () -> service.validateActive("t"));
        assertEquals(expected, e.getCode());
    }
}
