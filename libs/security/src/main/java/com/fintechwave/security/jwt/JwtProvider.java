package com.fintechwave.security.jwt;

import com.fintechwave.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenTtlMs;

    public JwtProvider(JwtProperties jwtProperties) {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least 32 characters (256 bits) for HMAC-SHA256");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtlMs = (long) jwtProperties.getAccessTokenTtlMinutes() * 60 * 1_000L;
    }

    public String generateAccessToken(UUID userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenTtlMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
}
