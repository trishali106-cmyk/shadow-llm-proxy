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
 *
 * <p>{@code POST /generate} serves the primary LLM synchronously and schedules background shadow work.
 * {@code GET /metrics} returns a {@link com.digitalocean.llmproxy.model.MetricsSnapshot} snapshot;
 * counters update after async candidate comparison (~500 ms), not immediately after generate.
 * Check {@code scope} and {@code instance_id} when running behind a load balancer.
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

    /**
     * Returns current shadow comparison statistics for this instance or cluster.
     *
     * @see com.digitalocean.llmproxy.model.MetricsSnapshot
     */
    @GetMapping("/metrics")
    public MetricsSnapshot metrics() {
        return metricsTracker.snapshot();
    }
}
