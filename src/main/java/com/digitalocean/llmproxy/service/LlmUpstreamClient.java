package com.digitalocean.llmproxy.service;

import com.digitalocean.llmproxy.exception.LlmUpstreamException;
import com.digitalocean.llmproxy.model.LLMResponse;
import com.digitalocean.llmproxy.model.PromptRequest;

import java.util.concurrent.TimeoutException;

/**
 * Abstraction for invoking primary and candidate LLM upstreams.
 */
public interface LlmUpstreamClient {

    LLMResponse generatePrimary(String requestId, PromptRequest request)
            throws TimeoutException, LlmUpstreamException;

    LLMResponse generateCandidate(String requestId, PromptRequest request)
            throws TimeoutException, LlmUpstreamException;
}
