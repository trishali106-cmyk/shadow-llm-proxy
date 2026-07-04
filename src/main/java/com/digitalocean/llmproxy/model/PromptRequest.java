package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Inbound request body for {@code POST /generate}.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptRequest(
        @NotBlank(message = "prompt must not be blank")
        @Size(max = 8192, message = "prompt must not exceed 8192 characters")
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
