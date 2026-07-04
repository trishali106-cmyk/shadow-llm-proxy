package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.exception.LlmUpstreamException;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates the synchronous primary LLM call and schedules background shadow comparison.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyOrchestrator {

    private final LlmUpstreamClient llmUpstreamClient;
    private final ShadowProcessor shadowProcessor;
    private final Environment environment;

    public ResponseEntity<LLMResponse> generate(PromptRequest request) {
        String requestId = UUID.randomUUID().toString();
        PromptRequest effectiveRequest = sanitizeDevFlags(request);

        LLMResponse primaryResponse;
        try {
            primaryResponse = llmUpstreamClient.generatePrimary(requestId, effectiveRequest);
        } catch (TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Primary LLM timed out", e);
        } catch (LlmUpstreamException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Primary LLM call failed", e);
        }

        LLMResponse clientResponse = LLMResponse.builder()
                .requestId(requestId)
                .model(primaryResponse.model())
                .content(primaryResponse.content())
                .latencyMs(primaryResponse.latencyMs())
                .build();

        log.debug(
                "[shadow-debug] requestId={} phase=primary-complete thread={} primaryLatencyMs={} ts={}",
                requestId,
                Thread.currentThread().getName(),
                clientResponse.latencyMs(),
                System.currentTimeMillis());

        shadowProcessor.processShadow(requestId, effectiveRequest, clientResponse);

        log.debug(
                "[shadow-debug] requestId={} phase=shadow-scheduled thread={} ts={}",
                requestId,
                Thread.currentThread().getName(),
                System.currentTimeMillis());

        return ResponseEntity.ok()
                .header("X-Request-Id", requestId)
                .body(clientResponse);
    }

    private PromptRequest sanitizeDevFlags(PromptRequest request) {
        if (environment.acceptsProfiles(Profiles.of("mock"))) {
            return request;
        }
        if (!request.isForceMismatch() && !request.isSimulateCandidateFailure()) {
            return request;
        }
        return PromptRequest.builder().prompt(request.prompt()).build();
    }
}
