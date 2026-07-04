package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.model.MetricsSnapshot;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe accumulator for shadow-comparison metrics.
 * Tracks shadow requests, matches, mismatches, and candidate failures using lock-free atomics.
 */
@Component
public class MetricsTracker {

    private final AtomicLong totalShadowRequests = new AtomicLong();
    private final AtomicLong matches = new AtomicLong();
    private final AtomicLong mismatches = new AtomicLong();
    private final AtomicLong candidateFailures = new AtomicLong();

    public void recordShadowRequest() {
        totalShadowRequests.incrementAndGet();
    }

    public void recordMatch() {
        matches.incrementAndGet();
    }

    public void recordMismatch() {
        mismatches.incrementAndGet();
    }

    public void recordCandidateFailure() {
        candidateFailures.incrementAndGet();
    }

    public MetricsSnapshot snapshot() {
        long matchCount = matches.get();
        long mismatchCount = mismatches.get();
        long compared = matchCount + mismatchCount;
        double rate = compared == 0 ? 100.0 : (matchCount * 100.0) / compared;

        return new MetricsSnapshot(
                totalShadowRequests.get(),
                matchCount,
                mismatchCount,
                candidateFailures.get(),
                Math.round(rate * 100.0) / 100.0
        );
    }
}
