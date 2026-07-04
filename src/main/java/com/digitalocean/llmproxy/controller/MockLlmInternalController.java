package com.digitalocean.llmproxy.controller;

import com.digitalocean.llmproxy.config.AsyncConfig.LlmTimingProperties;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Internal mock LLM controller simulating primary and candidate upstream providers.
 * Invoked in-process via {@link org.springframework.web.client.RestClient} to model
 * external latency (primary ~100 ms, candidate ~500 ms) and failure/mismatch scenarios.
 */
@RestController
@RequestMapping("/internal/mock")
@RequiredArgsConstructor
class MockLlmInternalController {

    private final LlmTimingProperties timing;

    @PostMapping("/primary")
    LLMResponse primary(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody PromptRequest request) throws TimeoutException {
        return simulate("primary-mock", requestId, request, timing.primaryDelayMs(), timing.primaryTimeoutMs(), false);
    }

    @PostMapping("/candidate")
    LLMResponse candidate(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody PromptRequest request) throws TimeoutException {
        if (request.isSimulateCandidateFailure()) {
            throw new IllegalStateException("Simulated candidate LLM failure");
        }
        return simulate(
                "candidate-mock",
                requestId,
                request,
                timing.candidateDelayMs(),
                timing.candidateTimeoutMs(),
                request.isForceMismatch()
        );
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

        String resolvedId = requestId != null ? requestId : UUID.randomUUID().toString();
        String content = forceMismatch ? "candidate-only: " + request.prompt() : "Answer: " + request.prompt();

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
