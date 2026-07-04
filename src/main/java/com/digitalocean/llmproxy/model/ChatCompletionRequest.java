package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI-compatible chat completion request for upstream LLM providers.
 */
public record ChatCompletionRequest(
        String model,
        List<Message> messages
) {
    public record Message(String role, String content) {}

    public static ChatCompletionRequest fromPrompt(String model, String prompt) {
        return new ChatCompletionRequest(model, List.of(new Message("user", prompt)));
    }
}
