package com.digitalocean.llmproxy.controller;

import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import com.digitalocean.llmproxy.service.MockLlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeoutException;

/**
 * Optional HTTP mock endpoints for the {@code mock} profile (e.g. external integration testing).
 */
@RestController
@RequestMapping("/internal/mock")
@Profile("mock")
@RequiredArgsConstructor
public class MockLlmInternalController {

    private final MockLlmService mockLlmService;

    @PostMapping("/primary")
    LLMResponse primary(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody PromptRequest request) throws TimeoutException {
        return mockLlmService.generatePrimary(requestId, request);
    }

    @PostMapping("/candidate")
    LLMResponse candidate(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody PromptRequest request) throws TimeoutException {
        return mockLlmService.generateCandidate(requestId, request);
    }
}
