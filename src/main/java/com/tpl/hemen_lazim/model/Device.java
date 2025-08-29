package com.tpl.hemen_lazim.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices",
        indexes = {
                @Index(name = "idx_devices_user", columnList = "user_id"),
                @Index(name = "idx_devices_push_enabled", columnList = "push_enabled")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_devices_fcm_token", columnNames = {"fcm_token"})
        })
@Data
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "fcm_token", length = 4096, unique = true)
    private String fcmToken;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

}



