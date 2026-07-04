package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable snapshot of shadow-comparison metrics exposed by {@code GET /metrics}.
 *
 * <p>Counters update after asynchronous shadow work completes (~500 ms candidate delay),
 * not when {@code POST /generate} returns. With {@code metrics.store=memory}, values reflect
 * only the instance that served this request; check {@link #scope()} and {@link #instanceId()}.
 *
 * @param totalShadowRequests candidate invocations that started shadow comparison
 * @param matches normalized primary/candidate outputs that agreed
 * @param mismatches normalized outputs that disagreed (also logged as WARN JSON)
 * @param candidateFailures candidate timeout, circuit-breaker open, or uncaught exception
 * @param shadowDropped shadow work not started because {@code llm.shadow.max-concurrency} was reached
 * @param shadowSkipped request passed probabilistic sampling ({@code llm.shadow.sample-rate})
 * @param realTimeMatchRate {@code matches / (matches + mismatches) * 100}, or {@code 100.0} if none compared yet
 * @param instanceId hostname of the container that produced this snapshot
 * @param scope {@code "instance"} for per-JVM counters, {@code "cluster"} when backed by Redis
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
