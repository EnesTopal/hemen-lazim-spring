package com.tpl.hemen_lazim.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "ix_refresh_user", columnList = "user_id"),
        @Index(name = "ux_refresh_token", columnList = "token", unique = true)
})
@Data
public class RefreshToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private UUID userId;

    @Column(nullable=false, length=300, unique=true)
    private String token;

    @Column(nullable=false)
    private Instant expiresAt;

    @Column(nullable=false)
    private boolean revoked = false;

    @Column(nullable=false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant revokedAt;

}