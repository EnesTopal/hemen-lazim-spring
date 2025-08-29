package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.CreateUserDTO;
import com.tpl.hemen_lazim.model.DTOs.UserDTO;
import com.tpl.hemen_lazim.security.JwtGenerate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServices {

    private final AuthenticationManager authenticationManager;
    private final JwtGenerate jwtGenerate;
    private final UserServices userServices;
    private final PasswordEncoder passwordEncoder;

    public AuthServices(AuthenticationManager authenticationManager,
                        JwtGenerate jwtGenerate,
                        UserServices userServices,
                        PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtGenerate = jwtGenerate;
        this.userServices = userServices;
        this.passwordEncoder = passwordEncoder;
    }


    public ResponseEntity<ApiResponse<String>> login(CreateUserDTO loginRequest) {
        try {
            // Kullanıcı adı ile login (email ile yapacaksan burada email'i kullan)
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                            safeTrim(loginRequest.getUserName()),
                            safe(loginRequest.getUserPassword())
                    );

            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwtToken = jwtGenerate.generateJwtToken(authentication);
            return ResponseEntity.ok(new ApiResponse<>("Login successful", jwtToken));
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>("Invalid username or password", null));
        } catch (DisabledException e) {
            // User.enabled=false ise gelebilir
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>("User is disabled", null));
        } catch (LockedException e) {
            return ResponseEntity
                    .status(HttpStatus.LOCKED)
                    .body(new ApiResponse<>("User account is locked", null));
        } catch (AuthenticationException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>("Authentication failed", null));
        }
    }

    public ResponseEntity<ApiResponse<UserDTO>> register(CreateUserDTO registerRequest) {
        // Normalizasyon
        String userName = safeTrim(registerRequest.getUserName());
        String email = normalizeEmail(registerRequest.getEmail());
        String rawPassword = safe(registerRequest.getUserPassword());

        // Basit kontroller (Controller katmanında @Valid de kullanabilirsin)
        if (userName == null || userName.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>("Username is required", null));
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>("Email is required", null));
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>("Password is required", null));
        }

        // Çakışma kontrolleri
        if (userServices.existsByUserName(userName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>("Username already in use", null));
        }
        if (userServices.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>("Email already in use", null));
        }

        // Parolayı hashle
        String encoded = passwordEncoder.encode(rawPassword);

        // DTO’yu güncelle ve kaydet
        registerRequest.setUserName(userName);
        registerRequest.setEmail(email);
        registerRequest.setUserPassword(encoded);

        UserDTO saved = userServices.createAccount(registerRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("User successfully registered", saved));
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
