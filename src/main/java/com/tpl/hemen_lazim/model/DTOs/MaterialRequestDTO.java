package com.tpl.hemen_lazim.model.DTOs;

import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.model.enums.Units;

import java.time.Instant;
import java.util.UUID;

public record MaterialRequestDTO(
        UUID id,
        UUID requesterId,
        String requesterName,

        String title,
        String description,
        Category category,
        Integer quantity,
        Units units,

        Double latitude,
        Double longitude,
        Integer radiusMeters,

        RequestStatus status,
        Instant expiresAt,
        Instant createdAt
) {}
