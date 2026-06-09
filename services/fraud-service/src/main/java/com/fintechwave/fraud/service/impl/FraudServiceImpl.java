package com.fintechwave.fraud.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.fraud.domain.entity.FraudDecision;
import com.fintechwave.fraud.domain.entity.FraudRule;
import com.fintechwave.fraud.domain.entity.OutboxEvent;
import com.fintechwave.fraud.domain.entity.ProcessedEvent;
import com.fintechwave.fraud.domain.enums.FraudDecisionType;
import com.fintechwave.fraud.domain.enums.RuleAction;
import com.fintechwave.fraud.dto.FraudDecisionResponse;
import com.fintechwave.fraud.repository.FraudDecisionRepository;
import com.fintechwave.fraud.repository.FraudRuleRepository;
import com.fintechwave.fraud.repository.OutboxEventRepository;
import com.fintechwave.fraud.repository.ProcessedEventRepository;
import com.fintechwave.fraud.service.IFraudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import com.fintechwave.events.GenericDomainEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudServiceImpl implements IFraudService {

    private final FraudRuleRepository fraudRuleRepository;
    private final FraudDecisionRepository fraudDecisionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void evaluate(UUID transactionId, UUID userId, BigDecimal amount, String currency, UUID idempotencyKey) {

        try {
            processedEventRepository.save(
                    ProcessedEvent.builder()
                            .idempotencyKey(idempotencyKey)
                            .processedAt(Instant.now())
                            .build());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate TransferInitiated event skipped: idempotencyKey={}", idempotencyKey);
            return;
        }

        List<FraudRule> rules = fraudRuleRepository.findByEnabledTrue();

        List<String> triggeredRules = new ArrayList<>();
        int riskScore = 0;
        boolean block = false;

        for (FraudRule rule : rules) {
            boolean triggered = checkVelocity(rule, userId, amount);
            if (triggered) {
                triggeredRules.add(rule.getRuleCode());
                riskScore += 25;
                if (rule.getAction() == RuleAction.BLOCK) {
                    block = true;
                }
                log.warn("Fraud rule triggered: rule={} userId={} txId={}", rule.getRuleCode(), userId, transactionId);
            }
        }

        incrementVelocityCounters(userId, amount);

        FraudDecisionType decision = (triggeredRules.isEmpty() && !block)
                ? FraudDecisionType.APPROVED
                : FraudDecisionType.FLAGGED;

        riskScore = Math.min(riskScore, 100);

        FraudDecision fraudDecision = FraudDecision.builder()
                .transactionId(transactionId)
                .userId(userId)
                .decision(decision)
                .riskScore(riskScore)
                .triggeredRules(triggeredRules)
                .amount(amount)
                .currency(currency)
                .decidedAt(Instant.now())
                .build();

        fraudDecisionRepository.save(fraudDecision);

        String eventType = (decision == FraudDecisionType.APPROVED)
                ? "TRANSACTION_APPROVED"
                : "TRANSACTION_FLAGGED";

        publishToOutbox(transactionId, userId, amount, currency, eventType, riskScore, triggeredRules);

        log.info("Fraud evaluation complete: txId={} userId={} decision={} score={}",
                transactionId, userId, decision, riskScore);
    }

    @Override
    public Page<FraudDecisionResponse> getUserDecisions(UUID userId, Pageable pageable) {
        return fraudDecisionRepository.findByUserIdOrderByDecidedAtDesc(userId, pageable)
                .map(d -> FraudDecisionResponse.builder()
                        .id(d.getId())
                        .transactionId(d.getTransactionId())
                        .userId(d.getUserId())
                        .decision(d.getDecision())
                        .riskScore(d.getRiskScore())
                        .triggeredRules(d.getTriggeredRules())
                        .amount(d.getAmount())
                        .currency(d.getCurrency())
                        .decidedAt(d.getDecidedAt())
                        .build());
    }

    private boolean checkVelocity(FraudRule rule, UUID userId, BigDecimal amount) {
        if (rule.getWindowSec() == null || rule.getThreshold() == null) {
            return false;
        }

        String key = buildRedisKey(rule.getRuleCode(), userId);
        long now = Instant.now().getEpochSecond();
        long windowStart = now - rule.getWindowSec();

        // Remove stale entries outside the window
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart - 1);

        if (rule.getRuleCode().contains("TX_COUNT")) {
            // Count-based: check number of entries in window
            Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
            return count != null && count >= rule.getThreshold().longValue();
        } else if (rule.getRuleCode().contains("TX_VOLUME")) {
            // Volume-based: sum scores is not native in ZSet; use a parallel STRING key
            String volumeKey = key + ":volume";
            String currentStr = redisTemplate.opsForValue().get(volumeKey);
            BigDecimal current = (currentStr != null) ? new BigDecimal(currentStr) : BigDecimal.ZERO;
            return current.add(amount).compareTo(rule.getThreshold()) > 0;
        }

        return false;
    }

    private void incrementVelocityCounters(UUID userId, BigDecimal amount) {
        long now = Instant.now().getEpochSecond();
        String member = UUID.randomUUID().toString();

        // TX count: 60s window
        String countKey60 = "velocity:tx_count:" + userId + ":60s";
        redisTemplate.opsForZSet().add(countKey60, member, now);
        redisTemplate.expire(countKey60, Duration.ofSeconds(120));

        // TX volume: 1h window
        String volKey1h = "velocity:tx_volume:" + userId + ":1h";
        incrementVolumeKey(volKey1h, amount, Duration.ofSeconds(7200));

        // TX volume: 24h window
        String volKey24h = "velocity:tx_volume:" + userId + ":24h";
        incrementVolumeKey(volKey24h, amount, Duration.ofSeconds(172800));
    }

    private void incrementVolumeKey(String key, BigDecimal amount, Duration expiry) {
        String current = redisTemplate.opsForValue().get(key);
        BigDecimal updated = (current != null)
                ? new BigDecimal(current).add(amount)
                : amount;
        redisTemplate.opsForValue().set(key, updated.toPlainString(), expiry);
    }

    private String buildRedisKey(String ruleCode, UUID userId) {
        return "velocity:" + ruleCode.toLowerCase().replace("_", ":") + ":" + userId;
    }

    private void publishToOutbox(UUID transactionId, UUID userId,
            BigDecimal amount, String currency,
            String eventType, int riskScore, List<String> triggeredRules) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("transactionId", transactionId.toString());
            payload.put("userId", userId.toString());
            payload.put("amount", amount);
            payload.put("currency", currency);
            payload.put("riskScore", riskScore);
            payload.put("triggeredRules", triggeredRules);

            GenericDomainEvent domainEvent = new GenericDomainEvent(
                    eventType,
                    1,
                    transactionId,
                    "TRANSACTION",
                    payload);

            String json = objectMapper.writeValueAsString(domainEvent);

            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateId(domainEvent.getAggregateId())
                    .aggregateType(domainEvent.getAggregateType())
                    .eventType(domainEvent.getEventType())
                    .payload(json)
                    .published(false)
                    .occurredAt(domainEvent.getOccurredAt())
                    .build());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize fraud outbox event for txId={}", transactionId, e);
            throw new RuntimeException("Fraud outbox serialization failure", e);
        }
    }
}
