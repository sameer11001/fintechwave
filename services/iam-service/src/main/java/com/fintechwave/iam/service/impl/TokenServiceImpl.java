package com.fintechwave.iam.service.impl;

import com.fintechwave.iam.config.JwtProperties;
import com.fintechwave.iam.domain.entity.RefreshToken;
import com.fintechwave.iam.domain.entity.User;
import com.fintechwave.iam.exception.InvalidTokenException;
import com.fintechwave.iam.repository.RefreshTokenRepository;
import com.fintechwave.iam.service.ITokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TokenServiceImpl implements ITokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokenRepository;
    private final JwtProperties          jwtProperties;

    @Override
    @Transactional
    public RefreshToken issue(User user) {
        String raw = generateSecureToken();
        Instant expiry = Instant.now().plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS);
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token(raw)
                .expiresAt(expiry)
                .build();
        return tokenRepository.save(token);
    }

    @Override
    @Transactional
    public RefreshToken rotate(String incomingToken) {
        RefreshToken existing = tokenRepository.findByToken(incomingToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (!existing.isValid()) {
            tokenRepository.revokeAllByUserId(existing.getUser().getId());
            log.warn("Reuse of revoked/expired token detected — all sessions terminated for userId={}",
                    existing.getUser().getId());
            throw new InvalidTokenException(
                    "Refresh token is revoked or expired. All sessions have been terminated.");
        }

        existing.setRevoked(true);
        tokenRepository.save(existing);

        return issue(existing.getUser());
    }

    @Override
    @Transactional
    public void revoke(String token) {
        tokenRepository.findByToken(token).ifPresentOrElse(
                rt -> {
                    rt.setRevoked(true);
                    tokenRepository.save(rt);
                    log.info("Token revoked for userId={}", rt.getUser().getId());
                },
                () -> log.warn("Revoke attempted for unknown token")
        );
    }

    @Override
    @Transactional
    public void revokeAll(User user) {
        int count = tokenRepository.revokeAllByUserId(user.getId());
        log.info("Revoked {} tokens for userId={}", count, user.getId());
    }

    private static String generateSecureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
