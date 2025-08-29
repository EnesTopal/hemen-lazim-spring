package com.tpl.hemen_lazim.controllers;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.security.SecurityUtils;
import com.tpl.hemen_lazim.services.UserServices;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
