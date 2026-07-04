package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.MetricsProperties;
import com.digitalocean.llmproxy.metrics.CounterStore;
import com.digitalocean.llmproxy.model.MetricsSnapshot;
import com.digitalocean.llmproxy.support.InstanceIdentity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Thread-safe shadow metrics with Micrometer counters and a JSON snapshot for {@code GET /metrics}.
 *
 * <p>Counters are backed by {@link com.digitalocean.llmproxy.metrics.CounterStore}:
 * in-memory by default, or Redis when {@code metrics.store=redis}. Micrometer counters are
 * also registered for {@code /actuator/prometheus}.
 *
 * <p>Match rate is computed as {@code matches / (matches + mismatches)}; skipped and dropped
 * requests are tracked separately and do not affect the rate.
 */
@Component
public class MetricsTracker {

    private static final String TOTAL_SHADOW_REQUESTS = "total_shadow_requests";
    private static final String MATCHES = "matches";
    private static final String MISMATCHES = "mismatches";
    private static final String CANDIDATE_FAILURES = "candidate_failures";
    private static final String SHADOW_DROPPED = "shadow_dropped";
    private static final String SHADOW_SKIPPED = "shadow_skipped";

    private final CounterStore counterStore;
    private final InstanceIdentity instanceIdentity;
    private final MetricsProperties metricsProperties;

    private final Counter shadowRequestsCounter;
    private final Counter matchesCounter;
    private final Counter mismatchesCounter;
    private final Counter candidateFailuresCounter;
    private final Counter shadowDroppedCounter;
    private final Counter shadowSkippedCounter;

    public MetricsTracker(
            CounterStore counterStore,
            InstanceIdentity instanceIdentity,
            MetricsProperties metricsProperties,
            MeterRegistry meterRegistry) {
        this.counterStore = counterStore;
        this.instanceIdentity = instanceIdentity;
        this.metricsProperties = metricsProperties;
        shadowRequestsCounter = Counter.builder("shadow.requests.total").register(meterRegistry);
        matchesCounter = Counter.builder("shadow.matches.total").register(meterRegistry);
        mismatchesCounter = Counter.builder("shadow.mismatches.total").register(meterRegistry);
        candidateFailuresCounter = Counter.builder("shadow.candidate.failures.total").register(meterRegistry);
        shadowDroppedCounter = Counter.builder("shadow.dropped.total").register(meterRegistry);
        shadowSkippedCounter = Counter.builder("shadow.skipped.total").register(meterRegistry);
    }

    public void recordShadowRequest() {
        counterStore.increment(TOTAL_SHADOW_REQUESTS);
        shadowRequestsCounter.increment();
    }

    public void recordMatch() {
        counterStore.increment(MATCHES);
        matchesCounter.increment();
    }

    public void recordMismatch() {
        counterStore.increment(MISMATCHES);
        mismatchesCounter.increment();
    }

    public void recordCandidateFailure() {
        counterStore.increment(CANDIDATE_FAILURES);
        candidateFailuresCounter.increment();
    }

    public void recordShadowDropped() {
        counterStore.increment(SHADOW_DROPPED);
        shadowDroppedCounter.increment();
    }

    public void recordShadowSkipped() {
        counterStore.increment(SHADOW_SKIPPED);
        shadowSkippedCounter.increment();
    }

    public MetricsSnapshot snapshot() {
        long matchCount = counterStore.get(MATCHES);
        long mismatchCount = counterStore.get(MISMATCHES);
        long compared = matchCount + mismatchCount;
        double rate = compared == 0 ? 100.0 : (matchCount * 100.0) / compared;

        return new MetricsSnapshot(
                counterStore.get(TOTAL_SHADOW_REQUESTS),
                matchCount,
                mismatchCount,
                counterStore.get(CANDIDATE_FAILURES),
                counterStore.get(SHADOW_DROPPED),
                counterStore.get(SHADOW_SKIPPED),
                Math.round(rate * 100.0) / 100.0,
                instanceIdentity.getInstanceId(),
                metricsProperties.getStore() == MetricsProperties.Store.REDIS ? "cluster" : "instance"
        );
    }
}
