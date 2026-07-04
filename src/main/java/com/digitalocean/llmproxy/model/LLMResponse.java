package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Standard LLM response payload returned by mock endpoints and the public {@code /generate} API.
 */
@Builder
public record LLMResponse(
        @JsonProperty("request_id")
        String requestId,

        String model,
        String content,

        @JsonProperty("latency_ms")
        long latencyMs
) {}
