package com.digitalocean.llmproxy.model;

/**
 * Structured payload logged when primary and candidate outputs disagree after normalization.
 */
public record MismatchLog(
        String requestId,
        String normalizedPrimary,
        String normalizedCandidate,
        String rawPrimary,
        String rawCandidate
) {}
