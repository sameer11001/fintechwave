package com.fintechwave.ledger.query.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.ledger.dto.response.WalletResponse;
import com.fintechwave.ledger.exception.WalletNotFoundException;
import com.fintechwave.ledger.query.entity.WalletSummaryView;
import com.fintechwave.ledger.query.repository.WalletSummaryViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletProjectionService {

    private final WalletSummaryViewRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String WALLET_CACHE_PREFIX = "fintechwave:ledger-service:wallet:";
    private static final String BALANCE_CACHE_PREFIX = "fintechwave:ledger-service:balance:";
    private static final Duration WALLET_TTL = Duration.ofMinutes(5);
    private static final Duration BALANCE_TTL = Duration.ofSeconds(30);

    public void handleBalanceUpdate(UUID userId, UUID accountId, BigDecimal balance, String currency) {
        WalletSummaryView view = repository.findById(userId).orElseGet(() -> 
            WalletSummaryView.builder()
                .userId(userId)
                .balances(new HashMap<>())
                .accounts(new HashMap<>())
                .build()
        );

        view.getBalances().put(currency, balance);
        view.getAccounts().put(accountId, currency);
        view.setUpdatedAt(Instant.now());

        repository.save(view);

        // Update Redis
        try {
            redisTemplate.opsForValue().set(WALLET_CACHE_PREFIX + userId, objectMapper.writeValueAsString(view), WALLET_TTL);
            redisTemplate.opsForValue().set(BALANCE_CACHE_PREFIX + accountId, balance.toString(), BALANCE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache wallet summary for userId={}", userId, e);
        }

        log.info("Projected balance update for userId={} accountId={}", userId, accountId);
    }

    public Optional<WalletSummaryView> getWalletSummary(UUID userId) {
        try {
            String cached = redisTemplate.opsForValue().get(WALLET_CACHE_PREFIX + userId);
            if (cached != null) {
                return Optional.of(objectMapper.readValue(cached, WalletSummaryView.class));
            }
        } catch (Exception e) {
            log.warn("Failed to read cache for userId={}", userId, e);
        }

        Optional<WalletSummaryView> viewOpt = repository.findById(userId);
        viewOpt.ifPresent(view -> {
            try {
                redisTemplate.opsForValue().set(WALLET_CACHE_PREFIX + userId, objectMapper.writeValueAsString(view), WALLET_TTL);
            } catch (Exception e) {}
        });
        return viewOpt;
    }

    public WalletResponse getWalletResponse(UUID userId) {
        WalletSummaryView view = getWalletSummary(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));

        String currency = "JOD";
        BigDecimal balance = view.getBalances().getOrDefault(currency, BigDecimal.ZERO);
        UUID accountId = null;
        for (Map.Entry<UUID, String> entry : view.getAccounts().entrySet()) {
            if (entry.getValue().equals(currency)) {
                accountId = entry.getKey();
                break;
            }
        }
        if (accountId == null && !view.getAccounts().isEmpty()) {
            accountId = view.getAccounts().keySet().iterator().next();
            currency = view.getAccounts().get(accountId);
            balance = view.getBalances().get(currency);
        }

        return WalletResponse.builder()
                .accountId(accountId)
                .ownerId(userId)
                .balance(balance)
                .currency(currency)
                .kycTier("TIER_0") // Assuming default or unavailable in this view
                .createdAt(view.getUpdatedAt()) // Using updatedAt as proxy
                .build();
    }
}
