package com.digitalocean.llmproxy.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cluster-wide counter storage backed by Redis {@code INCR}.
 *
 * <p>Active when {@code metrics.store=redis}. Requires {@code REDIS_HOST}, {@code REDIS_PORT},
 * and optionally {@code REDIS_PASSWORD}. {@code GET /metrics} returns {@code scope: "cluster"}
 * with totals aggregated across all App Platform instances.
 */
@Component
@ConditionalOnProperty(name = "metrics.store", havingValue = "redis")
public class RedisCounterStore implements CounterStore {

    private static final String KEY_PREFIX = "shadow-llm-proxy:metrics:";

    private final StringRedisTemplate redisTemplate;

    public RedisCounterStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void increment(String key) {
        redisTemplate.opsForValue().increment(key(key));
    }

    @Override
    public long get(String key) {
        String value = redisTemplate.opsForValue().get(key(key));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private static String key(String metric) {
        return KEY_PREFIX + metric;
    }
}
