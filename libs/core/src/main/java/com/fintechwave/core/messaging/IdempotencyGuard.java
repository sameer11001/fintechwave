package com.fintechwave.core.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor
public class IdempotencyGuard {

    private final StringRedisTemplate redisTemplate;

    public boolean isAlreadyProcessed(String namespace, String eventId) {
        if (eventId == null || eventId.isEmpty() || "null".equals(eventId)) {
            return false; // Can't guarantee idempotency without an ID
        }

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent("processed:" + namespace + ":" + eventId, "1", Duration.ofDays(7));

        return Boolean.FALSE.equals(isNew);
    }
}
