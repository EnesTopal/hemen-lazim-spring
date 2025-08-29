package com.tpl.hemen_lazim.model.DTOs;

import com.tpl.hemen_lazim.model.enums.Role;

import java.util.UUID;

public record UserDTO(
        UUID id,
        String userName,
        String email,
        Role role,
        boolean enabled
) {}
