package com.digitalocean.llmproxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration for primary and candidate {@link org.springframework.web.client.RestClient} beans.
 * Applies separate connect/read timeouts for each LLM path via {@code llm.timeout.*} properties.
 */
@Configuration
public class RestClientConfig {

    @Bean(name = "primaryRestClient")
    RestClient primaryRestClient(
            RestClient.Builder builder,
            @Value("${llm.timeout.primary-ms:500}") long primaryTimeoutMs) {
        return builder
                .requestFactory(requestFactory(primaryTimeoutMs))
                .build();
    }

    @Bean(name = "candidateRestClient")
    RestClient candidateRestClient(
            RestClient.Builder builder,
            @Value("${llm.timeout.candidate-ms:2000}") long candidateTimeoutMs) {
        return builder
                .requestFactory(requestFactory(candidateTimeoutMs))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }
}
