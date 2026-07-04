package com.digitalocean.llmproxy.metrics;

/**
 * Shared counter storage for shadow metrics. Implementations may be process-local or cluster-wide.
 */
public interface CounterStore {

    void increment(String key);

    long get(String key);
}
