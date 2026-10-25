package com.tpl.hemen_lazim.model.DTOs;

import com.tpl.hemen_lazim.model.RequestLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Süre uzatma: oluştururken seçilebilen süre aralığıyla aynı. */
public record ExtendRequestDTO(
        @NotNull @Min(RequestLimits.MIN_DURATION_MIN) @Max(RequestLimits.MAX_DURATION_MIN)
        Integer minutes
) {}
