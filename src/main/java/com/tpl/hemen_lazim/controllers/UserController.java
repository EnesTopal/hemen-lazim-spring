package com.tpl.hemen_lazim.controllers;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.UserSummaryDTO;
import com.tpl.hemen_lazim.security.SecurityUtils;
import com.tpl.hemen_lazim.services.UserServices;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserServices userServices;

    public UserController(UserServices userServices) {
        this.userServices = userServices;
    }

    // Hesabımı sil
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<String>> deleteMe() {
        var me = SecurityUtils.currentUserId();
        return userServices.deleteAccount(me);
    }

    // Chat ekranlarında karşı tarafın adını göstermek için minimal profil
    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryDTO> getUser(@PathVariable UUID id) {
        return userServices.getUserSummary(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
