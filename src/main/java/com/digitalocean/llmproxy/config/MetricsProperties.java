package com.digitalocean.llmproxy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metrics storage configuration for {@code GET /metrics} counter backends.
 *
 * <p>Set {@code metrics.store=redis} (via env {@code METRICS_STORE=redis}) when running multiple
 * App Platform instances so counters aggregate cluster-wide. The deploy agent provisions Redis
 * automatically; see {@code deploy/agent.sh}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "metrics")
public class MetricsProperties {

    /**
     * {@code memory} keeps counters in-process (per instance);
     * {@code redis} shares counters across all replicas.
     */
    private Store store = Store.MEMORY;

    public enum Store {
        /** Per-JVM counters; {@code scope: "instance"} in metrics JSON. */
        MEMORY,
        /** Redis-backed totals; {@code scope: "cluster"} in metrics JSON. */
        REDIS
    }
}
