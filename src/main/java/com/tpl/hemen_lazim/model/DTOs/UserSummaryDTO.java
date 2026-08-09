package com.tpl.hemen_lazim.model.DTOs;

import java.util.UUID;

public record UserSummaryDTO(
        UUID id,
        String userName
) {}
