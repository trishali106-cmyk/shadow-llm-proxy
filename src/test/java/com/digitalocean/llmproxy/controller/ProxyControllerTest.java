package com.digitalocean.llmproxy.controller;

import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.MetricsSnapshot;
import com.digitalocean.llmproxy.model.PromptRequest;
import com.digitalocean.llmproxy.service.MetricsTracker;
import com.digitalocean.llmproxy.service.ProxyOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc unit tests for {@link ProxyController} HTTP mapping and response shape.
 */
@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

    @Mock
    private ProxyOrchestrator proxyOrchestrator;

    @Mock
    private MetricsTracker metricsTracker;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProxyController(proxyOrchestrator, metricsTracker))
                .build();
    }

    @Test
    void generate_returnsOk() throws Exception {
        when(proxyOrchestrator.generate(any(PromptRequest.class)))
                .thenReturn(ResponseEntity.ok(
                        LLMResponse.builder()
                                .requestId("req-1")
                                .model("primary-mock")
                                .content("Answer: hi")
                                .latencyMs(100)
                                .build()
                ));

        mockMvc.perform(post("/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Answer: hi"));
    }

    @Test
    void metrics_returnsSnapshot() throws Exception {
        when(metricsTracker.snapshot()).thenReturn(new MetricsSnapshot(5, 4, 1, 0, 80.0));

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_shadow_requests").value(5))
                .andExpect(jsonPath("$.real_time_match_rate").value(80.0));
    }
}
