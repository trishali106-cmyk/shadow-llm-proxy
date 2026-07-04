package com.digitalocean.llmproxy.integration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the proxy API using a live Spring Boot context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"mock", "test"})
class ProxyIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void generate_respondsInUnder150msWhileCandidateRunsInBackground() {
        var body = Map.of("prompt", "latency test");

        Instant start = Instant.now();
        ResponseEntity<Map> response = postGenerate(body);
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elapsed).isLessThan(Duration.ofMillis(150));
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    void generate_returnsPrimaryContentImmediately() {
        var body = Map.of("prompt", "hello");

        ResponseEntity<Map> response = postGenerate(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("content")).isEqualTo("Answer: hello");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void metrics_reflectsShadowComparisonAfterBackgroundProcessing() {
        postGenerate(Map.of("prompt", "metrics test"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    ResponseEntity<Map> metrics = restTemplate.getForEntity("/metrics", Map.class);
                    assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(((Number) metrics.getBody().get("total_shadow_requests")).intValue())
                            .isGreaterThanOrEqualTo(1);
                    assertThat(metrics.getBody()).containsKey("real_time_match_rate");
                    assertThat(metrics.getBody()).containsKey("instance_id");
                    assertThat(metrics.getBody().get("scope")).isEqualTo("instance");
                });
    }

    @Test
    void forcedMismatchIsTrackedInMetrics() {
        postGenerate(Map.of(
                "prompt", "mismatch test",
                "force_mismatch", true
        ));

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    ResponseEntity<Map> metrics = restTemplate.getForEntity("/metrics", Map.class);
                    assertThat(((Number) metrics.getBody().get("mismatches")).intValue()).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void candidateFailureDoesNotAffectPrimaryResponse() {
        ResponseEntity<Map> response = postGenerate(Map.of(
                "prompt", "failure test",
                "simulate_candidate_failure", true
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("content")).isEqualTo("Answer: failure test");

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    ResponseEntity<Map> metrics = restTemplate.getForEntity("/metrics", Map.class);
                    assertThat(((Number) metrics.getBody().get("candidate_failures")).intValue())
                            .isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void blankPromptReturnsBadRequest() {
        ResponseEntity<Map> response = postGenerate(Map.of("prompt", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postGenerate(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/generate", new HttpEntity<>(body, headers), Map.class);
    }
}
