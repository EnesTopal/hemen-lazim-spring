package com.tpl.hemen_lazim.model.DTOs;

import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.Units;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record MaterialRequestCreateDTO(
        @NotBlank @Size(max = 120)
        String title,

        @Size(max = 1000)
        String description,

        @NotNull
        Category category,

        @Positive
        Integer quantity,

        @NotNull
        Units units,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude,

        @Positive
        Integer radiusMeters,

        String expiresInHours
) {}
