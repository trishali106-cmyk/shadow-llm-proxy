package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.config.LlmProperties;
import com.digitalocean.llmproxy.exception.LlmUpstreamException;
import com.digitalocean.llmproxy.model.ChatCompletionRequest;
import com.digitalocean.llmproxy.model.ChatCompletionResponse;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * HTTP-backed LLM client for production upstreams with OpenAI-compatible request/response mapping.
 */
@Service
@Profile("!mock")
public class HttpLlmUpstreamClient implements LlmUpstreamClient {

    private static final String DEFAULT_PRIMARY_MODEL = "primary";
    private static final String DEFAULT_CANDIDATE_MODEL = "candidate";

    private final LlmProperties llmProperties;
    private final RestClient primaryRestClient;
    private final RestClient candidateRestClient;

    public HttpLlmUpstreamClient(
            LlmProperties llmProperties,
            @Qualifier("primaryRestClient") RestClient primaryRestClient,
            @Qualifier("candidateRestClient") RestClient candidateRestClient) {
        this.llmProperties = llmProperties;
        this.primaryRestClient = primaryRestClient;
        this.candidateRestClient = candidateRestClient;
    }

    @Override
    public LLMResponse generatePrimary(String requestId, PromptRequest request)
            throws TimeoutException, LlmUpstreamException {
        return invoke(primaryRestClient, llmProperties.primary().url(), DEFAULT_PRIMARY_MODEL, requestId, request);
    }

    @Override
    public LLMResponse generateCandidate(String requestId, PromptRequest request)
            throws TimeoutException, LlmUpstreamException {
        return invoke(candidateRestClient, llmProperties.candidate().url(), DEFAULT_CANDIDATE_MODEL, requestId, request);
    }

    private LLMResponse invoke(
            RestClient client,
            String url,
            String model,
            String requestId,
            PromptRequest request) throws TimeoutException, LlmUpstreamException {
        long start = System.nanoTime();
        try {
            var spec = client.post()
                    .uri(url)
                    .header("X-Request-Id", requestId);

            if (!llmProperties.apiKey().isBlank()) {
                spec = spec.header("Authorization", "Bearer " + llmProperties.apiKey());
            }

            ChatCompletionResponse upstream = spec.body(ChatCompletionRequest.fromPrompt(model, request.prompt()))
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (upstream == null) {
                throw new LlmUpstreamException("LLM upstream returned an empty body");
            }

            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return LLMResponse.builder()
                    .requestId(requestId)
                    .model(upstream.model() != null ? upstream.model() : model)
                    .content(upstream.firstContent())
                    .latencyMs(latencyMs)
                    .build();
        } catch (RestClientResponseException e) {
            throw new LlmUpstreamException(
                    "LLM upstream returned HTTP " + e.getStatusCode().value() + ": " + e.getStatusText(), e);
        } catch (RestClientException e) {
            if (isTimeout(e)) {
                throw new TimeoutException(e.getMessage());
            }
            throw new LlmUpstreamException("LLM upstream call failed: " + e.getMessage(), e);
        }
    }

    private static boolean isTimeout(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
