package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.MetricsProperties;
import com.digitalocean.llmproxy.metrics.InMemoryCounterStore;
import com.digitalocean.llmproxy.support.InstanceIdentity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link MetricsTracker} snapshot and match-rate calculations.
 */
class MetricsTrackerTest {

    @Test
    void snapshot_calculatesRealTimeMatchRate() {
        MetricsTracker tracker = new MetricsTracker(
                new InMemoryCounterStore(),
                new InstanceIdentity(),
                metricsProperties(),
                new SimpleMeterRegistry());
        tracker.recordShadowRequest();
        tracker.recordMatch();
        tracker.recordShadowRequest();
        tracker.recordMismatch();
        tracker.recordShadowRequest();
        tracker.recordCandidateFailure();

        var snapshot = tracker.snapshot();

        assertEquals(3, snapshot.totalShadowRequests());
        assertEquals(1, snapshot.matches());
        assertEquals(1, snapshot.mismatches());
        assertEquals(1, snapshot.candidateFailures());
        assertEquals(50.0, snapshot.realTimeMatchRate());
        assertEquals("instance", snapshot.scope());
    }

    private static MetricsProperties metricsProperties() {
        MetricsProperties properties = new MetricsProperties();
        properties.setStore(MetricsProperties.Store.MEMORY);
        return properties;
    }

    @Test
    void snapshot_defaultsMatchRateTo100WhenNoComparisonsYet() {
        MetricsTracker tracker = new MetricsTracker(
                new InMemoryCounterStore(),
                new InstanceIdentity(),
                metricsProperties(),
                new SimpleMeterRegistry());
        assertEquals(100.0, tracker.snapshot().realTimeMatchRate());
    }
}
