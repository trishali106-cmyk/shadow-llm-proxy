package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates the synchronous primary LLM call and schedules background shadow comparison.
 * Returns the primary response immediately while delegating candidate evaluation to {@link ShadowProcessor}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyOrchestrator {

    private final LLMMockService llmMockService;
    private final ShadowProcessor shadowProcessor;

    public ResponseEntity<LLMResponse> generate(PromptRequest request) {
        String requestId = UUID.randomUUID().toString();

        LLMResponse primaryResponse;
        try {
            primaryResponse = llmMockService.generatePrimary(requestId, request);
        } catch (TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Primary LLM timed out", e);
        }

        log.debug(
                "[shadow-debug] requestId={} phase=primary-complete thread={} primaryLatencyMs={} ts={}",
                requestId,
                Thread.currentThread().getName(),
                primaryResponse.latencyMs(),
                System.currentTimeMillis());

        // Fire-and-forget on virtual thread — primary response is not blocked by candidate work.
        shadowProcessor.processShadow(requestId, request, primaryResponse);

        log.debug(
                "[shadow-debug] requestId={} phase=shadow-scheduled thread={} ts={}",
                requestId,
                Thread.currentThread().getName(),
                System.currentTimeMillis());

        return ResponseEntity.ok()
                .header("X-Request-Id", requestId)
                .body(primaryResponse);
    }
}
