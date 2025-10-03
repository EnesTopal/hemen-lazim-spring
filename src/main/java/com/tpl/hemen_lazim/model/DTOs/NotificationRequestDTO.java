package com.tpl.hemen_lazim.model.DTOs;

import java.util.Map;
import java.util.UUID;

public record NotificationRequestDTO(
        UUID recipientUserId,
        String title,
        String body,
        Map<String, String> data
) {}

