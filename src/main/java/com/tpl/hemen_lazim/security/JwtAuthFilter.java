package com.tpl.hemen_lazim.security;

import com.tpl.hemen_lazim.services.JwtUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtGenerate jwtGenerate;
    private final JwtUserDetailsService jwtUserDetailsService;

    public JwtAuthFilter(JwtGenerate jwtGenerate, JwtUserDetailsService jwtUserDetailsService) {
        this.jwtGenerate = jwtGenerate;
        this.jwtUserDetailsService = jwtUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = extractToken(request);
            System.out.println("Authorization header’dan çıkarılan JWT: " + (jwt != null ? jwt : "yok"));

            if (StringUtils.hasText(jwt) && jwtGenerate.validateToken(jwt)) {
                System.out.println("Token alındı ve geçerli: " + jwt);

                UUID userId = jwtGenerate.getUserIdFromToken(jwt);

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var user = jwtUserDetailsService.loadUserById(userId);
                    System.out.println(user != null
                            ? ("Kullanıcı bulundu: " + user.getUsername())
                            : "Geçerli token fakat kullanıcı bulunamadı");

                    if (user != null) {
                        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        System.out.println("SecurityContext’e authentication yerleştirildi.");
                    } else {
                        SecurityContextHolder.clearContext();
                        System.out.println("SecurityContext temizlendi (kullanıcı yok).");
                    }
                } else if (SecurityContextHolder.getContext().getAuthentication() != null) {
                    System.out.println("Authentication zaten mevcut, tekrar set edilmedi.");
                }
            } else {
                System.out.println("Token yok veya geçersiz.");
                SecurityContextHolder.clearContext();
            }
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            System.out.println("Token doğrulama sırasında hata: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        } finally {
            filterChain.doFilter(request, response);
        }

    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }
}


/*
Old Try-catch block
        try {
String jwt = extractToken(request);

            if (StringUtils.hasText(jwt) && jwtGenerate.validateToken(jwt)) {
UUID userId = jwtGenerate.getUserIdFromToken(jwt);

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
UserDetails user = jwtUserDetailsService.loadUserById(userId);
                    if (user != null) {
UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                            }
                            }
                            } catch (Exception e) {
        SecurityContextHolder.clearContext();
        } finally {
                filterChain.doFilter(request, response);
        } */