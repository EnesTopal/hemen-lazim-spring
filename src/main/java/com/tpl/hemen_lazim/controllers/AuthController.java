package com.tpl.hemen_lazim.controllers;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.CreateUserDTO;
import com.tpl.hemen_lazim.model.DTOs.UserDTO;
import com.tpl.hemen_lazim.services.AuthServices;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthServices authServices;

    public AuthController(AuthServices authServices) {
        this.authServices = authServices;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<String>> login(@Valid @RequestBody CreateUserDTO loginRequest) {
        return authServices.login(loginRequest);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDTO>> register(@Valid @RequestBody CreateUserDTO registerRequest) {
        return authServices.register(registerRequest);
    }
}
