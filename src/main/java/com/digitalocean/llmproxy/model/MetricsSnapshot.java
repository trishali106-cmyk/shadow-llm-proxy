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

        @JsonProperty("shadow_dropped")
        long shadowDropped,

        @JsonProperty("shadow_skipped")
        long shadowSkipped,

        @JsonProperty("real_time_match_rate")
        double realTimeMatchRate,

        @JsonProperty("instance_id")
        String instanceId,

        String scope
) {}
