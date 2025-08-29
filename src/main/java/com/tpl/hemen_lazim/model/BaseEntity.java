package com.tpl.hemen_lazim.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Data;

import java.time.Instant;


@MappedSuperclass
@Data
public abstract class BaseEntity {
    @Column(name = "created_at", updatable = false, nullable = false)
    protected Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() { this.updatedAt = Instant.now(); }
}