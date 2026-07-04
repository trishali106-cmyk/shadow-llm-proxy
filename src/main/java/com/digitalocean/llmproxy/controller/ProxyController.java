package com.digitalocean.llmproxy.controller;

import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.MetricsSnapshot;
import com.digitalocean.llmproxy.model.PromptRequest;
import com.digitalocean.llmproxy.service.MetricsTracker;
import com.digitalocean.llmproxy.service.ProxyOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST API for prompt generation and shadow-comparison metrics.
 * Exposes {@code POST /generate} for the primary LLM path and {@code GET /metrics} for observability.
 */
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyOrchestrator proxyOrchestrator;
    private final MetricsTracker metricsTracker;

    @PostMapping("/generate")
    public ResponseEntity<LLMResponse> generate(@Valid @RequestBody PromptRequest request) {
        return proxyOrchestrator.generate(request);
    }

    @GetMapping("/metrics")
    public MetricsSnapshot metrics() {
        return metricsTracker.snapshot();
    }
}
