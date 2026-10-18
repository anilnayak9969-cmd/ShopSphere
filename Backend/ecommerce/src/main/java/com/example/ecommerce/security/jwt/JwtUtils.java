package com.example.ecommerce.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.ecommerce.security.UserPrincipal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and checks JSON Web Tokens. A JWT is a signed string: anyone can read its contents,
 * but only the server (which knows the secret) can produce a valid signature, so a user cannot
 * edit their own role inside the token.
 */
@Slf4j
@Component
public class JwtUtils {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtils(@Value("${spring.app.jwtSecret}") String secret,
                    @Value("${spring.app.jwtExpirationMs}") long expirationMs) {
        if (secret == null || secret.length() < 64) {
            throw new IllegalStateException("spring.app.jwtSecret must be at least 64 characters long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        return Jwts.builder()
                .subject(principal.getEmail())
                .claim("uid", principal.getId())
                .claim("role", principal.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Returns the email inside a valid token, or null if the token is invalid or expired. */
    public String getEmailIfValid(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
