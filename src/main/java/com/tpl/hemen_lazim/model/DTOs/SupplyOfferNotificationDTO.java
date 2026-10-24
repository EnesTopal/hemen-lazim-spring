package com.tpl.hemen_lazim.model.DTOs;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Uygulama requesterId de gönderiyor; bilerek okunmuyor. Talep sahibi talebin kendisinden
 * alınır, yoksa gövdeye başka bir kullanıcı yazılarak ona bildirim atılabiliyordu.
 */
public record SupplyOfferNotificationDTO(
        @NotNull
        UUID requestId
) {}

