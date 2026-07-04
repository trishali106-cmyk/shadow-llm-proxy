package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.AsyncConfig.ShadowConcurrencyLimiter;
import com.digitalocean.llmproxy.config.LlmProperties;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.MismatchLog;
import com.digitalocean.llmproxy.model.PromptRequest;
import com.digitalocean.llmproxy.util.OutputNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

/**
 * Asynchronous shadow processor with probabilistic sampling, concurrency limits, and circuit-breaker protection.
 *
 * <p>Runs on {@code shadowTaskExecutor} virtual threads. Before invoking the candidate:
 * <ol>
 *   <li>Checks {@code llm.shadow.enabled}</li>
 *   <li>Applies {@code llm.shadow.sample-rate} — skipped requests increment {@code shadow_skipped}
 *       (10% in {@code prod} profile, 100% in {@code mock})</li>
 *   <li>Enforces {@code llm.shadow.max-concurrency} — excess work increments {@code shadow_dropped}</li>
 * </ol>
 *
 * <p>Candidate failures are isolated; they never affect the primary response already returned to the client.
 */
@Slf4j
@Service
public class ShadowProcessor {

    private final LlmUpstreamClient llmUpstreamClient;
    private final MetricsTracker metricsTracker;
    private final LlmProperties llmProperties;
    private final OutputNormalizer outputNormalizer;
    private final ObjectMapper objectMapper;
    private final ShadowConcurrencyLimiter concurrencyLimiter;
    private final CircuitBreaker candidateCircuitBreaker;

    public ShadowProcessor(
            LlmUpstreamClient llmUpstreamClient,
            MetricsTracker metricsTracker,
            LlmProperties llmProperties,
            OutputNormalizer outputNormalizer,
            ObjectMapper objectMapper,
            ShadowConcurrencyLimiter concurrencyLimiter,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.llmUpstreamClient = llmUpstreamClient;
        this.metricsTracker = metricsTracker;
        this.llmProperties = llmProperties;
        this.outputNormalizer = outputNormalizer;
        this.objectMapper = objectMapper;
        this.concurrencyLimiter = concurrencyLimiter;
        this.candidateCircuitBreaker = circuitBreakerRegistry.circuitBreaker("candidateLlm");
    }

    @Async("shadowTaskExecutor")
    public void processShadow(String requestId, PromptRequest request, LLMResponse primaryResponse) {
        log.debug(
                "[shadow-debug] requestId={} phase=shadow-started thread={} ts={}",
                requestId,
                Thread.currentThread().getName(),
                System.currentTimeMillis());

        if (!llmProperties.shadow().enabled()) {
            log.debug("[shadow-debug] requestId={} phase=shadow-disabled", requestId);
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() >= llmProperties.shadow().sampleRate()) {
            metricsTracker.recordShadowSkipped();
            log.debug("[shadow-debug] requestId={} phase=shadow-skipped-by-sampling", requestId);
            return;
        }

        if (!concurrencyLimiter.tryAcquire()) {
            metricsTracker.recordShadowDropped();
            log.warn("Shadow work dropped due to concurrency limit requestId={}", requestId);
            return;
        }

        try {
            metricsTracker.recordShadowRequest();
            invokeCandidateAndCompare(requestId, request, primaryResponse);
        } finally {
            concurrencyLimiter.release();
        }
    }

    private void invokeCandidateAndCompare(String requestId, PromptRequest request, LLMResponse primaryResponse) {
        LLMResponse candidateResponse;
        try {
            log.debug("[shadow-debug] requestId={} phase=candidate-call-start", requestId);
            candidateResponse = candidateCircuitBreaker.executeCallable(
                    () -> llmUpstreamClient.generateCandidate(requestId, request));
            log.debug(
                    "[shadow-debug] requestId={} phase=candidate-call-complete candidateLatencyMs={} model={}",
                    requestId,
                    candidateResponse.latencyMs(),
                    candidateResponse.model());
        } catch (CallNotPermittedException e) {
            log.warn("Candidate circuit breaker open requestId={}", requestId);
            metricsTracker.recordCandidateFailure();
            return;
        } catch (TimeoutException e) {
            log.warn("Candidate LLM timed out requestId={}: {}", requestId, e.getMessage());
            metricsTracker.recordCandidateFailure();
            return;
        } catch (Exception e) {
            log.warn("Candidate LLM failed requestId={}: {}", requestId, e.getMessage());
            metricsTracker.recordCandidateFailure();
            return;
        }

        normalizeAndCompare(requestId, primaryResponse.content(), candidateResponse.content());
    }

    void normalizeAndCompare(String requestId, String primaryContent, String candidateContent) {
        if (outputNormalizer.normalizeAndCompare(primaryContent, candidateContent)) {
            metricsTracker.recordMatch();
            log.debug("[shadow-debug] requestId={} phase=compare-complete matched=true", requestId);
            return;
        }

        metricsTracker.recordMismatch();
        log.debug("[shadow-debug] requestId={} phase=compare-complete matched=false", requestId);
        logMismatch(requestId, primaryContent, candidateContent);
    }

    private void logMismatch(String requestId, String primaryContent, String candidateContent) {
        MismatchLog mismatch = new MismatchLog(
                requestId,
                outputNormalizer.normalize(primaryContent),
                outputNormalizer.normalize(candidateContent),
                outputNormalizer.preview(primaryContent),
                outputNormalizer.preview(candidateContent),
                outputNormalizer.sha256(primaryContent),
                outputNormalizer.sha256(candidateContent),
                primaryContent == null ? 0 : primaryContent.length(),
                candidateContent == null ? 0 : candidateContent.length()
        );

        try {
            log.warn("Shadow mismatch payload={}", objectMapper.writeValueAsString(mismatch));
        } catch (JsonProcessingException e) {
            log.warn("Shadow mismatch requestId={} normalizedPrimary={} normalizedCandidate={}",
                    mismatch.requestId(), mismatch.normalizedPrimary(), mismatch.normalizedCandidate());
        }
    }
}
