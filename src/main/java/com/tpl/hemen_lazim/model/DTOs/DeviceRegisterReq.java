package com.tpl.hemen_lazim.model.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceRegisterReq(
        @NotBlank @Size(max = 4096)
        String fcmToken,
        
        @Size(max = 50)
        String deviceType,  // "ANDROID", "IOS", etc.
        
        @Size(max = 255)
        String deviceName   // Device model/name
) {}
