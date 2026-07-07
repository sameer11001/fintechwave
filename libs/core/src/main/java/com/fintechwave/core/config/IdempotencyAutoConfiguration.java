package com.fintechwave.core.config;

import com.fintechwave.core.messaging.IdempotencyGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class IdempotencyAutoConfiguration {

    @Bean
    public IdempotencyGuard idempotencyGuard(StringRedisTemplate redisTemplate) {
        return new IdempotencyGuard(redisTemplate);
    }
}
