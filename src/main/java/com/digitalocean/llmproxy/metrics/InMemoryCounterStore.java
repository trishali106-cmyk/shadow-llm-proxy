package com.digitalocean.llmproxy.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local counter storage using lock-free {@link AtomicLong} values.
 *
 * <p>Active when {@code metrics.store=memory} (the default). Each JVM maintains independent
 * counters; {@code GET /metrics} returns {@code scope: "instance"}.
 */
@Component
@ConditionalOnProperty(name = "metrics.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryCounterStore implements CounterStore {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public void increment(String key) {
        counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public long get(String key) {
        AtomicLong counter = counters.get(key);
        return counter == null ? 0L : counter.get();
    }
}
