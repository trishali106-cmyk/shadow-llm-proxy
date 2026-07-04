package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.LlmProperties;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * In-process mock LLM simulation used by the {@code mock} profile (no HTTP loopback).
 */
@Service
@Profile("mock")
public class MockLlmService {

    private final LlmProperties llmProperties;

    public MockLlmService(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    public LLMResponse generatePrimary(String requestId, PromptRequest request) throws TimeoutException {
        return simulate(
                "primary-mock",
                requestId,
                request,
                llmProperties.mock().primaryDelayMs(),
                llmProperties.timeout().primaryMs(),
                false);
    }

    public LLMResponse generateCandidate(String requestId, PromptRequest request) throws TimeoutException {
        if (request.isSimulateCandidateFailure()) {
            throw new IllegalStateException("Simulated candidate LLM failure");
        }
        return simulate(
                "candidate-mock",
                requestId,
                request,
                llmProperties.mock().candidateDelayMs(),
                llmProperties.timeout().candidateMs(),
                request.isForceMismatch());
    }

    private LLMResponse simulate(
            String model,
            String requestId,
            PromptRequest request,
            long delayMs,
            long timeoutMs,
            boolean forceMismatch) throws TimeoutException {
        long start = System.nanoTime();
        sleepBounded(delayMs, timeoutMs, model);
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        String content = forceMismatch ? "candidate-only: " + request.prompt() : "Answer: " + request.prompt();

        String resolvedId = requestId != null ? requestId : UUID.randomUUID().toString();

        return LLMResponse.builder()
                .requestId(resolvedId)
                .model(model)
                .content(content)
                .latencyMs(latencyMs)
                .build();
    }

    private static void sleepBounded(long delayMs, long timeoutMs, String label) throws TimeoutException {
        if (delayMs > timeoutMs) {
            throw new TimeoutException(label + " LLM delay exceeds configured timeout");
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(label + " LLM interrupted", e);
        }
    }
}
