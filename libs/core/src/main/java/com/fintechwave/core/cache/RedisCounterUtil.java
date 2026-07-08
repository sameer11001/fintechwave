package com.fintechwave.core.cache;

import org.springframework.data.redis.core.StringRedisTemplate;

public final class RedisCounterUtil {

    private RedisCounterUtil() {
    }

    /**
     * Reads a Redis key as a {@code long}, defaulting to {@code 0} if the key
     * does not exist or is blank.
     *
     * @param redis the template
     * @param key   the Redis key
     * @return parsed long value, or {@code 0}
     */
    public static long getLong(StringRedisTemplate redis, String key) {
        String value = redis.opsForValue().get(key);
        return value != null && !value.isBlank() ? Long.parseLong(value) : 0L;
    }

    /**
     * Reads a Redis key as a {@code double}, defaulting to {@code 0.0} if the
     * key does not exist or is blank.
     *
     * @param redis the template
     * @param key   the Redis key
     * @return parsed double value, or {@code 0.0}
     */
    public static double getDouble(StringRedisTemplate redis, String key) {
        String value = redis.opsForValue().get(key);
        return value != null && !value.isBlank() ? Double.parseDouble(value) : 0.0;
    }
}
