package com.tpl.hemen_lazim.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.*;

import java.util.Date;
import java.util.UUID;

@Component
public class JwtGenerate {

    @Value("${hemen_lazim.app.secret}")
    private String APP_SECRET;

    @Value("${hemen_lazim.expires.in}")
    private long EXPIRES_IN;

    public String generateJwtToken(Authentication authentication){
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        Date expireDate = new Date(System.currentTimeMillis() + EXPIRES_IN);

        return Jwts.builder()
                .setSubject(userDetails.getUserId().toString()) // UUID -> String
                .setIssuedAt(new Date())
                .setExpiration(expireDate)
                .signWith(SignatureAlgorithm.HS512, APP_SECRET)
                .compact();
    }

    public UUID getUserIdFromToken(String token){
        Claims claims = Jwts.parser()
                .setSigningKey(APP_SECRET)
                .parseClaimsJws(token)
                .getBody();
        return UUID.fromString(claims.getSubject()); // String -> UUID
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(APP_SECRET).parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            System.out.println("Token expired: " + e.getMessage());
        } catch (MalformedJwtException e) {
            System.out.println("Incorrect token format.");
        } catch (SignatureException e) {
            System.out.println("Token signature is invalid.");
        } catch (IllegalArgumentException e) {
            System.out.println("Empty token.");
        }
        return false;
    }

    private boolean isTokenExpired(String token) {
        Date expire = Jwts.parser()
                .setSigningKey(APP_SECRET)
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        return expire.before(new Date());
    }
}
