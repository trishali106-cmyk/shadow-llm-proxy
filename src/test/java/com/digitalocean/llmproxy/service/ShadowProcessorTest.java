package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.LlmProperties;
import com.digitalocean.llmproxy.util.OutputNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ShadowProcessor} output comparison and metrics recording.
 */
@ExtendWith(MockitoExtension.class)
class ShadowProcessorTest {

    @Mock
    private MetricsTracker metricsTracker;

    private ShadowProcessor shadowProcessor;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties(null, null, "", null, null,
                new LlmProperties.Shadow(true, 1.0, 100));
        OutputNormalizer normalizer = new OutputNormalizer(new ObjectMapper());
        shadowProcessor = new ShadowProcessor(
                null,
                metricsTracker,
                properties,
                normalizer,
                new ObjectMapper(),
                new com.digitalocean.llmproxy.config.AsyncConfig.ShadowConcurrencyLimiter(100),
                CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void normalizeAndCompare_recordsMatchWhenOutputsEquivalent() {
        shadowProcessor.normalizeAndCompare("req-1", "Hello World", "hello world!!!");

        verify(metricsTracker).recordMatch();
        verify(metricsTracker, never()).recordMismatch();
    }

    @Test
    void normalizeAndCompare_recordsMismatchWhenOutputsDiffer() {
        shadowProcessor.normalizeAndCompare("req-1", "primary", "candidate");

        verify(metricsTracker).recordMismatch();
        verify(metricsTracker, never()).recordMatch();
    }
}
