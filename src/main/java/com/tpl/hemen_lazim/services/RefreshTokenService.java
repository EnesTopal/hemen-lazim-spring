package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.RefreshToken;
import com.tpl.hemen_lazim.repositories.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final long refreshDays;
    private final SecureRandom rnd = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo,
                               @Value("${jwt.refresh.days}") long refreshDays) {
        this.repo = repo;
        this.refreshDays = refreshDays;
    }

    public RefreshToken issue(UUID userId) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setToken(randomToken());
        rt.setExpiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS));
        return repo.save(rt);
    }

    public RefreshToken validateActive(String token) {
        RefreshToken rt = repo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("REFRESH_NOT_FOUND"));
        if (rt.isRevoked()) throw new IllegalStateException("REFRESH_REVOKED");
        if (rt.getExpiresAt().isBefore(Instant.now())) throw new IllegalStateException("REFRESH_EXPIRED");
        return rt;
    }

    public void revoke(RefreshToken rt, String reason) {
        rt.setRevoked(true);
        rt.setRevokedAt(Instant.now());
        repo.save(rt);
    }

    private String randomToken() {
        byte[] buf = new byte[64]; // 512-bit
        rnd.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}