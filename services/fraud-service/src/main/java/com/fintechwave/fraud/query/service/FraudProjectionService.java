package com.fintechwave.fraud.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.fraud.query.entity.FraudRiskProfileView;
import com.fintechwave.fraud.query.repository.FraudRiskProfileViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudProjectionService {

    private final FraudRiskProfileViewRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PROFILE_CACHE_PREFIX = "fintechwave:fraud-service:profile:";
    private static final Duration PROFILE_TTL = Duration.ofMinutes(15);

    public void handleFraudEvaluation(UUID userId, UUID transactionId, Integer newRiskScore, String status) {
        FraudRiskProfileView view = repository.findById(userId).orElseGet(() -> 
            FraudRiskProfileView.builder()
                .userId(userId)
                .riskScore(0)
                .build()
        );

        if (newRiskScore != null) {
            view.setRiskScore(newRiskScore);
        }

        if ("FLAGGED".equals(status) || "REJECTED".equals(status)) {
            if (!view.getFlaggedTransactions().contains(transactionId)) {
                view.getFlaggedTransactions().add(transactionId);
            }
        }

        view.setUpdatedAt(Instant.now());
        repository.save(view);

        try {
            redisTemplate.opsForValue().set(PROFILE_CACHE_PREFIX + userId, objectMapper.writeValueAsString(view), PROFILE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache fraud risk profile for userId={}", userId, e);
        }

        log.info("Projected fraud risk profile for userId={} txId={}", userId, transactionId);
    }

    public Optional<FraudRiskProfileView> getRiskProfile(UUID userId) {
        try {
            String cached = redisTemplate.opsForValue().get(PROFILE_CACHE_PREFIX + userId);
            if (cached != null) {
                return Optional.of(objectMapper.readValue(cached, FraudRiskProfileView.class));
            }
        } catch (Exception e) {
            log.warn("Failed to read cache for userId={}", userId, e);
        }

        Optional<FraudRiskProfileView> viewOpt = repository.findById(userId);
        viewOpt.ifPresent(view -> {
            try {
                redisTemplate.opsForValue().set(PROFILE_CACHE_PREFIX + userId, objectMapper.writeValueAsString(view), PROFILE_TTL);
            } catch (Exception e) {}
        });
        return viewOpt;
    }
}
