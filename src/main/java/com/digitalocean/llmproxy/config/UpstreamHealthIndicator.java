package com.digitalocean.llmproxy.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reports readiness based on whether external LLM URLs are configured in production.
 */
@Component
@Profile("!mock")
public class UpstreamHealthIndicator implements HealthIndicator {

    private final LlmProperties llmProperties;

    public UpstreamHealthIndicator(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Override
    public Health health() {
        boolean primaryConfigured = isExternalUrl(llmProperties.primary().url());
        boolean candidateConfigured = isExternalUrl(llmProperties.candidate().url());

        if (primaryConfigured && candidateConfigured) {
            return Health.up()
                    .withDetail("primaryUrl", llmProperties.primary().url())
                    .withDetail("candidateUrl", llmProperties.candidate().url())
                    .build();
        }

        return Health.outOfService()
                .withDetail("reason", "LLM upstream URLs must be configured for non-mock profiles")
                .withDetail("primaryUrl", llmProperties.primary().url())
                .withDetail("candidateUrl", llmProperties.candidate().url())
                .build();
    }

    private static boolean isExternalUrl(String url) {
        return url != null
                && !url.contains("/internal/mock/")
                && !url.startsWith("http://127.0.0.1")
                && !url.startsWith("http://localhost");
    }
}
