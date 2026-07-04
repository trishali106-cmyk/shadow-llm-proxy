package com.digitalocean.llmproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token-bucket rate limiting for the public generate endpoint.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int requestsPerMinute
) {}
