package com.tpl.hemen_lazim.model;

import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.model.enums.Units;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Data
@Entity
@Table(name = "material_requests",
        indexes = {
                @Index(name = "idx_req_user", columnList = "requester_id"),
                @Index(name = "idx_req_status", columnList = "status"),
                @Index(name = "idx_req_expires", columnList = "expires_at"),
                @Index(name = "idx_req_lat_lng", columnList = "latitude, longitude")
        })
public class MaterialRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private Category category = Category.OTHER;

    @Column(name = "quantity")
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "units", length = 20)
    private Units units = Units.PARÇA;

    // Konum (şimdilik basit lat/lng + opsiyonel geohash)
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.OPEN;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.radiusMeters == null) this.radiusMeters = 1500;
        if (this.expiresAt == null) this.expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
    }

}
