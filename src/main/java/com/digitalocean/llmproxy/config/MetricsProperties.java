package com.digitalocean.llmproxy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metrics storage configuration. Use {@code redis} for cluster-wide counters behind a load balancer.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "metrics")
public class MetricsProperties {

    /**
     * {@code memory} keeps counters in-process; {@code redis} shares counters across instances.
     */
    private Store store = Store.MEMORY;

    public enum Store {
        MEMORY,
        REDIS
    }
}
