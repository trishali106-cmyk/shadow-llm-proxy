package com.digitalocean.llmproxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI-compatible chat completion response from upstream LLM providers.
 */
public record ChatCompletionResponse(
        String model,
        List<Choice> choices
) {
    public record Choice(Message message) {}

    public record Message(String content) {}

    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
            return "";
        }
        String content = choices.get(0).message().content();
        return content != null ? content : "";
    }
}
