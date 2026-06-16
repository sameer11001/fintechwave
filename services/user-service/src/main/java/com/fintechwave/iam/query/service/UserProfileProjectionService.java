package com.fintechwave.iam.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.iam.dto.response.UserProfileResponse;
import com.fintechwave.iam.exception.UserNotFoundException;
import com.fintechwave.iam.query.entity.UserProfileView;
import com.fintechwave.iam.query.repository.UserProfileViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileProjectionService {

    private final UserProfileViewRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "fintechwave:user-service:user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    public void handleUserRegistered(UUID userId, String email, String firstName, String lastName, UUID keycloakId, String status) {
        UserProfileView view = UserProfileView.builder()
                .id(userId)
                .keycloakId(keycloakId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .status(status)
                .kycTier("TIER_0")
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
        
        repository.save(view);
        cacheView(view);
        log.info("Projected UserRegistered for userId={}", userId);
    }

    public void handleKycVerified(UUID userId, String newTier) {
        repository.findById(userId).ifPresent(view -> {
            view.setKycTier(newTier);
            view.setUpdatedAt(java.time.Instant.now());
            repository.save(view);
            cacheView(view);
            log.info("Projected KYCVerified for userId={} newTier={}", userId, newTier);
        });
    }

    public Optional<UserProfileView> getUserProfile(UUID userId) {
        String cacheKey = CACHE_PREFIX + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Optional.of(objectMapper.readValue(cached, UserProfileView.class));
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache for userId={}", userId, e);
        }

        Optional<UserProfileView> viewOpt = repository.findById(userId);
        viewOpt.ifPresent(this::cacheView);
        return viewOpt;
    }

    private void cacheView(UserProfileView view) {
        try {
            String cacheKey = CACHE_PREFIX + view.getId();
            String json = objectMapper.writeValueAsString(view);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache UserProfileView for userId={}", view.getId(), e);
        }
    }

    public UserProfileResponse getUserProfileResponse(UUID userId) {
        UserProfileView view = getUserProfile(userId)
                .orElseThrow(() -> new UserNotFoundException("User profile not found in read model for ID: " + userId));
        return mapToResponse(view);
    }

    public UserProfileResponse getUserProfileResponseByKeycloakId(UUID keycloakId) {
        String cacheKey = CACHE_PREFIX + "kc:" + keycloakId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                UserProfileView view = objectMapper.readValue(cached, UserProfileView.class);
                return mapToResponse(view);
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache for keycloakId={}", keycloakId, e);
        }

        UserProfileView view = repository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User profile not found in read model for Keycloak ID: " + keycloakId));
        
        cacheView(view);
        try {
            String json = objectMapper.writeValueAsString(view);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache UserProfileView for keycloakId={}", keycloakId, e);
        }
        
        return mapToResponse(view);
    }

    private UserProfileResponse mapToResponse(UserProfileView view) {
        return UserProfileResponse.builder()
                .id(view.getId())
                .keycloakId(view.getKeycloakId())
                .email(view.getEmail())
                .firstName(view.getFirstName())
                .lastName(view.getLastName())
                .status(view.getStatus())
                .kycTier(view.getKycTier())
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .build();
    }
}
