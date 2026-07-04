package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Redacted payload logged when primary and candidate outputs disagree after normalization.
 */
public record MismatchLog(
        @JsonProperty("request_id")
        String requestId,

        @JsonProperty("normalized_primary")
        String normalizedPrimary,

        @JsonProperty("normalized_candidate")
        String normalizedCandidate,

        @JsonProperty("primary_preview")
        String primaryPreview,

        @JsonProperty("candidate_preview")
        String candidatePreview,

        @JsonProperty("primary_sha256")
        String primarySha256,

        @JsonProperty("candidate_sha256")
        String candidateSha256,

        @JsonProperty("primary_length")
        int primaryLength,

        @JsonProperty("candidate_length")
        int candidateLength
) {}
