package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.AsyncConfig.LlmTimingProperties;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.MismatchLog;
import com.digitalocean.llmproxy.model.PromptRequest;
import com.digitalocean.llmproxy.util.OutputNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeoutException;

/**
 * Asynchronous shadow processor that compares primary and candidate LLM outputs.
 * Runs on Java 21 virtual threads, decoupled from the servlet request so work continues
 * after the client receives the primary response; records matches, mismatches, and failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowProcessor {

    private static final ObjectMapper MISMATCH_LOG_MAPPER = new ObjectMapper();

    private final LLMMockService llmMockService;
    private final MetricsTracker metricsTracker;
    private final LlmTimingProperties timing;

    @Async("shadowTaskExecutor")
    public void processShadow(String requestId, PromptRequest request, LLMResponse primaryResponse) {
        log.debug(
                "[shadow-debug] requestId={} phase=shadow-started thread={} ts={}",
                requestId,
                Thread.currentThread().getName(),
                System.currentTimeMillis());

        if (!timing.shadowEnabled()) {
            log.debug(
                    "[shadow-debug] requestId={} phase=shadow-disabled thread={} ts={}",
                    requestId,
                    Thread.currentThread().getName(),
                    System.currentTimeMillis());
            return;
        }

        metricsTracker.recordShadowRequest();

        LLMResponse candidateResponse;
        try {
            log.debug(
                    "[shadow-debug] requestId={} phase=candidate-call-start thread={} ts={}",
                    requestId,
                    Thread.currentThread().getName(),
                    System.currentTimeMillis());
            candidateResponse = llmMockService.generateCandidate(requestId, request);
            log.debug(
                    "[shadow-debug] requestId={} phase=candidate-call-complete thread={} candidateLatencyMs={} model={} ts={}",
                    requestId,
                    Thread.currentThread().getName(),
                    candidateResponse.latencyMs(),
                    candidateResponse.model(),
                    System.currentTimeMillis());
        } catch (TimeoutException e) {
            log.warn("Candidate LLM timed out requestId={}: {}", requestId, e.getMessage());
            log.debug(
                    "[shadow-debug] requestId={} phase=candidate-timeout thread={} ts={}",
                    requestId,
                    Thread.currentThread().getName(),
                    System.currentTimeMillis());
            metricsTracker.recordCandidateFailure();
            return;
        } catch (Exception e) {
            log.warn("Candidate LLM failed requestId={}: {}", requestId, e.getMessage());
            log.debug(
                    "[shadow-debug] requestId={} phase=candidate-failure thread={} ts={}",
                    requestId,
                    Thread.currentThread().getName(),
                    System.currentTimeMillis());
            metricsTracker.recordCandidateFailure();
            return;
        }

        normalizeAndCompare(requestId, primaryResponse.content(), candidateResponse.content());
    }

    void normalizeAndCompare(String requestId, String primaryContent, String candidateContent) {
        if (OutputNormalizer.normalizeAndCompare(primaryContent, candidateContent)) {
            metricsTracker.recordMatch();
            log.debug(
                    "[shadow-debug] requestId={} phase=compare-complete thread={} matched=true ts={}",
                    requestId,
                    Thread.currentThread().getName(),
                    System.currentTimeMillis());
            return;
        }

        metricsTracker.recordMismatch();
        log.debug(
                "[shadow-debug] requestId={} phase=compare-complete thread={} matched=false ts={}",
                requestId,
                Thread.currentThread().getName(),
                System.currentTimeMillis());
        logMismatch(requestId, primaryContent, candidateContent);
    }

    private void logMismatch(String requestId, String primaryContent, String candidateContent) {
        MismatchLog mismatch = new MismatchLog(
                requestId,
                OutputNormalizer.normalize(primaryContent),
                OutputNormalizer.normalize(candidateContent),
                primaryContent,
                candidateContent
        );

        try {
            log.warn("Shadow mismatch payload={}", MISMATCH_LOG_MAPPER.writeValueAsString(mismatch));
        } catch (JsonProcessingException e) {
            log.warn(
                    "Shadow mismatch requestId={} normalizedPrimary={} normalizedCandidate={}",
                    mismatch.requestId(),
                    mismatch.normalizedPrimary(),
                    mismatch.normalizedCandidate()
            );
        }
    }
}
