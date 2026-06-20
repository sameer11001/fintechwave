package com.fintechwave.kyc.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.kyc.domain.enums.KycStatus;
import com.fintechwave.kyc.domain.enums.KycTier;
import com.fintechwave.kyc.dto.response.KycApplicationResponse;
import com.fintechwave.kyc.exception.KycApplicationNotFoundException;
import com.fintechwave.kyc.query.entity.KycApplicationView;
import com.fintechwave.kyc.query.repository.KycApplicationViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycProjectionService {

    private final KycApplicationViewRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "fintechwave:kyc-service:app:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    public KycApplicationResponse getMyApplication(UUID userId) {
        String cacheKey = CACHE_PREFIX + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                KycApplicationView view = objectMapper.readValue(cached, KycApplicationView.class);
                return mapToResponse(view);
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache for userId={}", userId, e);
        }

        KycApplicationView view = repository.findByUserId(userId)
                .orElseThrow(
                        () -> new KycApplicationNotFoundException("KYC Application not found for user: " + userId));

        try {
            String json = objectMapper.writeValueAsString(view);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache KycApplicationView for userId={}", userId, e);
        }

        return mapToResponse(view);
    }

    public Page<KycApplicationResponse> listApplications(String status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    public KycApplicationResponse getApplicationById(UUID applicationId) {
        KycApplicationView view = repository.findById(applicationId)
                .orElseThrow(() -> new KycApplicationNotFoundException("KYC Application not found: " + applicationId));
        return mapToResponse(view);
    }

    public void handleKycCreated(UUID id, UUID userId, String status, String currentTier, String requestedTier) {
        KycApplicationView view = repository.findById(id).orElseGet(() -> KycApplicationView.builder().id(id).build());
        view.setUserId(userId);
        view.setStatus(status);
        view.setCurrentTier(currentTier);
        view.setRequestedTier(requestedTier);
        view.setCreatedAt(java.time.Instant.now());
        view.setUpdatedAt(java.time.Instant.now());
        repository.save(view);
        cacheView(view);
        log.info("Projected KYC_CREATED for appId={} userId={}", id, userId);
    }

    public void handleKycSubmitted(UUID id, UUID userId, String status, String requestedTier) {
        KycApplicationView view = repository.findById(id).orElseGet(() -> KycApplicationView.builder().id(id).build());
        if (userId != null) {
            view.setUserId(userId);
        }
        view.setStatus(status);
        view.setRequestedTier(requestedTier);
        view.setRejectionReason(null);
        if (view.getCreatedAt() == null) {
            view.setCreatedAt(java.time.Instant.now());
        }
        view.setUpdatedAt(java.time.Instant.now());
        repository.save(view);
        cacheView(view);
        log.info("Projected KYC_SUBMITTED for appId={}", id);
    }

    public void handleKycVerified(UUID id, UUID userId, String status, String currentTier) {
        KycApplicationView view = repository.findById(id).orElseGet(() -> KycApplicationView.builder().id(id).build());
        if (userId != null) {
            view.setUserId(userId);
        }
        view.setStatus(status);
        view.setCurrentTier(currentTier);
        if (view.getCreatedAt() == null) {
            view.setCreatedAt(java.time.Instant.now());
        }
        view.setUpdatedAt(java.time.Instant.now());
        repository.save(view);
        cacheView(view);
        log.info("Projected KYC_VERIFIED for appId={}", id);
    }

    public void handleKycRejected(UUID id, UUID userId, String status, String rejectionReason) {
        KycApplicationView view = repository.findById(id).orElseGet(() -> KycApplicationView.builder().id(id).build());
        if (userId != null) {
            view.setUserId(userId);
        }
        view.setStatus(status);
        view.setRejectionReason(rejectionReason);
        if (view.getCreatedAt() == null) {
            view.setCreatedAt(java.time.Instant.now());
        }
        view.setUpdatedAt(java.time.Instant.now());
        repository.save(view);
        cacheView(view);
        log.info("Projected KYC_REJECTED for appId={}", id);
    }

    private void cacheView(KycApplicationView view) {
        try {
            String cacheKey = CACHE_PREFIX + view.getUserId();
            String json = objectMapper.writeValueAsString(view);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache KycApplicationView for userId={}", view.getUserId(), e);
        }
    }

    private KycApplicationResponse mapToResponse(KycApplicationView view) {
        return KycApplicationResponse.builder()
                .id(view.getId())
                .userId(view.getUserId())
                .status(KycStatus.valueOf(view.getStatus()))
                .currentTier(KycTier.valueOf(view.getCurrentTier()))
                .requestedTier(view.getRequestedTier() != null ? KycTier.valueOf(view.getRequestedTier()) : null)
                .rejectionReason(view.getRejectionReason())
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .build();
    }
}
