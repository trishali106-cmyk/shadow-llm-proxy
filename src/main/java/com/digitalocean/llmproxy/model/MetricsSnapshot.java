package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable snapshot of shadow-comparison metrics exposed by {@code GET /metrics}.
 */
public record MetricsSnapshot(
        @JsonProperty("total_shadow_requests")
        long totalShadowRequests,

        long matches,
        long mismatches,

        @JsonProperty("candidate_failures")
        long candidateFailures,

        @JsonProperty("real_time_match_rate")
        double realTimeMatchRate
) {}
