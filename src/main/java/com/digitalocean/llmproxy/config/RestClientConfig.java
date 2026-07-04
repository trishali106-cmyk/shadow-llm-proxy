package com.digitalocean.llmproxy.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Pooled HTTP clients for primary and candidate LLM upstream calls.
 */
@Configuration
public class RestClientConfig {

    @Bean(name = "primaryRestClient")
    RestClient primaryRestClient(
            RestClient.Builder builder,
            @Value("${llm.timeout.primary-ms:500}") long primaryTimeoutMs) {
        return builder
                .requestFactory(pooledRequestFactory(primaryTimeoutMs))
                .build();
    }

    @Bean(name = "candidateRestClient")
    RestClient candidateRestClient(
            RestClient.Builder builder,
            @Value("${llm.timeout.candidate-ms:2000}") long candidateTimeoutMs) {
        return builder
                .requestFactory(pooledRequestFactory(candidateTimeoutMs))
                .build();
    }

    private static HttpComponentsClientHttpRequestFactory pooledRequestFactory(long timeoutMs) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();

        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(200)
                .setMaxConnPerRoute(50)
                .setDefaultConnectionConfig(connectionConfig)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .build();

        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
