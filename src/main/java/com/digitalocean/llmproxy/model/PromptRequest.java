package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Inbound request body for {@code POST /generate}.
 * Carries the user prompt and optional flags to simulate candidate failure or forced mismatch.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptRequest(
        @NotBlank(message = "prompt must not be blank")
        String prompt,

        @JsonProperty("force_mismatch")
        Boolean forceMismatch,

        @JsonProperty("simulate_candidate_failure")
        Boolean simulateCandidateFailure
) {
    public PromptRequest {
        if (forceMismatch == null) {
            forceMismatch = false;
        }
        if (simulateCandidateFailure == null) {
            simulateCandidateFailure = false;
        }
    }

    @JsonIgnore
    public boolean isForceMismatch() {
        return forceMismatch;
    }

    @JsonIgnore
    public boolean isSimulateCandidateFailure() {
        return simulateCandidateFailure;
    }
}
