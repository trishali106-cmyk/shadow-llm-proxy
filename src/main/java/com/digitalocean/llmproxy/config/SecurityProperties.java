package com.digitalocean.llmproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings for API key authentication on public endpoints.
 */
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        boolean enabled,
        String apiKey,
        boolean permitHealth
) {
    public SecurityProperties {
        if (apiKey == null) {
            apiKey = "";
        }
    }
}
