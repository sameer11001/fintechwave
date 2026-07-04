package com.fintechwave.reporting.startup;

import com.fintechwave.reporting.repository.search.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportingCounterInitializer {

    private final StringRedisTemplate redisTemplate;
    private final UserSearchRepository userSearchRepo;

    @EventListener(ApplicationReadyEvent.class)
    public void seedCountersIfAbsent() {
        if (Boolean.FALSE.equals(redisTemplate.hasKey("reporting:active_users_count"))) {
            long count = userSearchRepo.countByStatus("ACTIVE");
            redisTemplate.opsForValue().set("reporting:active_users_count", String.valueOf(count));
            log.info("Seeded reporting:active_users_count = {}", count);
        }
        if (Boolean.FALSE.equals(redisTemplate.hasKey("reporting:pending_kyc_count"))) {
            long count = 0; // Defaulting to 0 since we do not have a direct API to query pending counts yet
            redisTemplate.opsForValue().set("reporting:pending_kyc_count", String.valueOf(count));
            log.info("Seeded reporting:pending_kyc_count = {}", count);
        }
    }
}
