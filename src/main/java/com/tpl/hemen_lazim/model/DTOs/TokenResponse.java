package com.tpl.hemen_lazim.model.DTOs;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long   expiresIn
) {}