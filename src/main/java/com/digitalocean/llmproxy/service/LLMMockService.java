package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.TimeoutException;

/**
 * HTTP client facade for invoking the in-process mock primary and candidate LLM endpoints.
 * Translates {@link org.springframework.web.client.RestClient} failures into timeout exceptions.
 */
@Service
public class LLMMockService {

    private final ServerPortResolver serverPortResolver;
    private final RestClient primaryRestClient;
    private final RestClient candidateRestClient;

    public LLMMockService(
            ServerPortResolver serverPortResolver,
            @Qualifier("primaryRestClient") RestClient primaryRestClient,
            @Qualifier("candidateRestClient") RestClient candidateRestClient) {
        this.serverPortResolver = serverPortResolver;
        this.primaryRestClient = primaryRestClient;
        this.candidateRestClient = candidateRestClient;
    }

    public LLMResponse generatePrimary(String requestId, PromptRequest request) throws TimeoutException {
        return invoke(primaryRestClient, "/internal/mock/primary", requestId, request);
    }

    public LLMResponse generateCandidate(String requestId, PromptRequest request) throws TimeoutException {
        return invoke(candidateRestClient, "/internal/mock/candidate", requestId, request);
    }

    private LLMResponse invoke(RestClient client, String path, String requestId, PromptRequest request)
            throws TimeoutException {
        try {
            return client.post()
                    .uri("http://localhost:" + serverPortResolver.getPort() + path)
                    .header("X-Request-Id", requestId)
                    .body(request)
                    .retrieve()
                    .body(LLMResponse.class);
        } catch (RestClientException e) {
            if (isTimeout(e)) {
                throw new TimeoutException(e.getMessage());
            }
            throw e;
        }
    }

    private static boolean isTimeout(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
