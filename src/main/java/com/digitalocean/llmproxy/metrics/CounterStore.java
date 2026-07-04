package com.digitalocean.llmproxy.metrics;

/**
 * Shared counter storage for shadow metrics.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link InMemoryCounterStore} — lock-free {@code AtomicLong} map, one counter set per JVM.
 *       Use for local dev or a single App Platform instance ({@code metrics.store=memory}).</li>
 *   <li>{@link RedisCounterStore} — Redis {@code INCR} for cluster-wide totals when multiple
 *       instances sit behind a load balancer ({@code metrics.store=redis}).</li>
 * </ul>
 *
 * <p>Without Redis, repeated {@code GET /metrics} calls behind a load balancer may return
 * different values depending on which instance serves the request.
 */
public interface CounterStore {

    /** Atomically increment the named counter by one. */
    void increment(String key);

    /** Return the current value of the named counter, or {@code 0} if unset. */
    long get(String key);
}
