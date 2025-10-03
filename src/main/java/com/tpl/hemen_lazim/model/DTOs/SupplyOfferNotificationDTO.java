package com.tpl.hemen_lazim.model.DTOs;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SupplyOfferNotificationDTO(
        @NotNull
        UUID requestId,
        
        @NotNull
        UUID requesterId
) {}

