package com.digitalocean.llmproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for LLM upstream URLs, timeouts, mock delays, and shadow behavior.
 */
@Validated
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        Upstream primary,
        Upstream candidate,
        String apiKey,
        Timeout timeout,
        Mock mock,
        Shadow shadow
) {
    public LlmProperties {
        if (primary == null) {
            primary = new Upstream("http://127.0.0.1:8080/internal/mock/primary");
        }
        if (candidate == null) {
            candidate = new Upstream("http://127.0.0.1:8080/internal/mock/candidate");
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (timeout == null) {
            timeout = new Timeout(500, 2000);
        }
        if (mock == null) {
            mock = new Mock(100, 500);
        }
        if (shadow == null) {
            shadow = new Shadow(true, 1.0, 100);
        }
    }

    public record Upstream(String url) {}

    public record Timeout(long primaryMs, long candidateMs) {}

    public record Mock(long primaryDelayMs, long candidateDelayMs) {}

    /**
     * @param enabled when false, shadow work is not scheduled
     * @param sampleRate fraction of requests that run shadow comparison ({@code 0.0}–{@code 1.0});
     *                   prod profile defaults to {@code 0.1}
     * @param maxConcurrency maximum concurrent shadow tasks; excess increments {@code shadow_dropped}
     */
    public record Shadow(boolean enabled, double sampleRate, int maxConcurrency) {}
}
