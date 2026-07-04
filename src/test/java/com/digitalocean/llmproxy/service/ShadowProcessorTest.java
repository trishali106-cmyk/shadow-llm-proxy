package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.AsyncConfig.LlmTimingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        shadowProcessor = new ShadowProcessor(null, metricsTracker,
                new LlmTimingProperties(100, 500, 500, 2000, true));
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
