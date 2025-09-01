package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.DTOs.CreateUserDTO;
import com.tpl.hemen_lazim.model.DTOs.RefreshRequest;
import com.tpl.hemen_lazim.model.DTOs.TokenResponse;
import com.tpl.hemen_lazim.model.DTOs.UserDTO;
import com.tpl.hemen_lazim.model.RefreshToken;
import com.tpl.hemen_lazim.security.JwtGenerate;
import org.springframework.beans.factory.annotation.Value;
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
    private final RefreshTokenService refreshTokens;

    @Value("${jwt.access.minutes:15}")
    private long accessMinutes; // sadece expiresIn hesaplamak için

    public AuthServices(AuthenticationManager authenticationManager,
                        JwtGenerate jwtGenerate,
                        UserServices userServices,
                        PasswordEncoder passwordEncoder,
                        RefreshTokenService refreshTokens) {
        this.authenticationManager = authenticationManager;
        this.jwtGenerate = jwtGenerate;
        this.userServices = userServices;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
    }

    // ESKI imza: ResponseEntity<ApiResponse<String>>
    // YENI: ResponseEntity<ApiResponse<TokenResponse>>
    public ResponseEntity<ApiResponse<TokenResponse>> login(CreateUserDTO loginRequest) {
        try {
            var token = new UsernamePasswordAuthenticationToken(
                    safeTrim(loginRequest.getUserName()),
                    safe(loginRequest.getUserPassword())
            );
            Authentication authentication = authenticationManager.authenticate(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String access = jwtGenerate.generateJwtToken(authentication);

            // JwtUserDetails içinde subject UUID; ondan userId çekelim:
            var principal = (com.tpl.hemen_lazim.security.JwtUserDetails) authentication.getPrincipal();
            var userId = principal.getUserId();

            RefreshToken rt = refreshTokens.issue(userId);

            long expiresIn = accessMinutes * 60L;
            TokenResponse body = new TokenResponse(access, rt.getToken(), expiresIn);
            return ResponseEntity.ok(new ApiResponse<>("Login successful", body));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>("Invalid username or password", null));
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>("User is disabled", null));
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(new ApiResponse<>("User account is locked", null));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>("Authentication failed", null));
        }
    }

    public ResponseEntity<ApiResponse<UserDTO>> register(CreateUserDTO registerRequest) {
        String userName = safeTrim(registerRequest.getUserName());
        String email = normalizeEmail(registerRequest.getEmail());
        String rawPassword = safe(registerRequest.getUserPassword());

        if (userName == null || userName.isBlank())
            return ResponseEntity.badRequest().body(new ApiResponse<>("Username is required", null));
        if (email == null || email.isBlank())
            return ResponseEntity.badRequest().body(new ApiResponse<>("Email is required", null));
        if (rawPassword == null || rawPassword.isBlank())
            return ResponseEntity.badRequest().body(new ApiResponse<>("Password is required", null));

        if (userServices.existsByUserName(userName))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>("Username already in use", null));
        if (userServices.existsByEmail(email))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>("Email already in use", null));

        registerRequest.setUserName(userName);
        registerRequest.setEmail(email);
        registerRequest.setUserPassword(passwordEncoder.encode(rawPassword));

        UserDTO saved = userServices.createAccount(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>("User successfully registered", saved));
    }

    // YENİ: refresh
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(RefreshRequest req) {
        try {
            RefreshToken current = refreshTokens.validateActive(req.refreshToken());
            // rotation: eskisini revoke et
            refreshTokens.revoke(current, "ROTATED");

            // yeni refresh + access
            RefreshToken next = refreshTokens.issue(current.getUserId());
            String access = jwtGenerate.generateJwtTokenFromUserId(current.getUserId()); // AŞAĞIDA küçük helper ekleyeceğiz
            long expiresIn = accessMinutes * 60L;

            TokenResponse body = new TokenResponse(access, next.getToken(), expiresIn);
            return ResponseEntity.ok(new ApiResponse<>("Refreshed", body));
        } catch (IllegalStateException ex) {
            String code = ex.getMessage(); // REFRESH_EXPIRED | REFRESH_REVOKED
            HttpStatus status = ("REFRESH_EXPIRED".equals(code)) ? HttpStatus.UNAUTHORIZED : HttpStatus.CONFLICT;
            return ResponseEntity.status(status).body(new ApiResponse<>(code, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>("REFRESH_NOT_FOUND", null));
        }
    }

    // YENİ: logout
    public ResponseEntity<ApiResponse<Void>> logout(RefreshRequest req) {
        try {
            RefreshToken current = refreshTokens.validateActive(req.refreshToken());
            refreshTokens.revoke(current, "LOGOUT");
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            String code = ex.getMessage();
            HttpStatus status = ("REFRESH_EXPIRED".equals(code)) ? HttpStatus.UNAUTHORIZED : HttpStatus.CONFLICT;
            return ResponseEntity.status(status).body(new ApiResponse<>(code, null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>("REFRESH_NOT_FOUND", null));
        }
    }

    // --- util'ler ---
    private static String normalizeEmail(String email) { return email == null ? null : email.trim().toLowerCase(); }
    private static String safeTrim(String s) { return s == null ? null : s.trim(); }
    private static String safe(String s) { return s == null ? "" : s; }
}

/*
@Service
public class AuthServices {

    private final AuthenticationManager authenticationManager;
    private final JwtGenerate jwtGenerate;
    private final UserServices userServices;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.access.minutes}")
    private long accessMinutes;

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

        String userName = safeTrim(registerRequest.getUserName());
        String email = normalizeEmail(registerRequest.getEmail());
        String rawPassword = safe(registerRequest.getUserPassword());

        System.out.println(userName + "" + email + " " + rawPassword);
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
*/