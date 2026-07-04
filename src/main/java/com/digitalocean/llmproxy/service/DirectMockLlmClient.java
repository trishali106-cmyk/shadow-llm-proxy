package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.exception.LlmUpstreamException;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeoutException;

/**
 * Direct in-process mock LLM client for the {@code mock} profile (avoids HTTP loopback latency).
 */
@Service
@Profile("mock")
public class DirectMockLlmClient implements LlmUpstreamClient {

    private final MockLlmService mockLlmService;

    public DirectMockLlmClient(MockLlmService mockLlmService) {
        this.mockLlmService = mockLlmService;
    }

    @Override
    public LLMResponse generatePrimary(String requestId, PromptRequest request) throws TimeoutException {
        return mockLlmService.generatePrimary(requestId, request);
    }

    @Override
    public LLMResponse generateCandidate(String requestId, PromptRequest request)
            throws TimeoutException, LlmUpstreamException {
        try {
            return mockLlmService.generateCandidate(requestId, request);
        } catch (IllegalStateException e) {
            throw new LlmUpstreamException(e.getMessage(), e);
        }
    }
}
