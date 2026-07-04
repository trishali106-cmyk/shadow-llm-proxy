package com.digitalocean.llmproxy.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link MetricsTracker} snapshot and match-rate calculations.
 */
class MetricsTrackerTest {

    @Test
    void snapshot_calculatesRealTimeMatchRate() {
        MetricsTracker tracker = new MetricsTracker();
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
    }

    @Test
    void snapshot_defaultsMatchRateTo100WhenNoComparisonsYet() {
        MetricsTracker tracker = new MetricsTracker();
        assertEquals(100.0, tracker.snapshot().realTimeMatchRate());
    }
}
