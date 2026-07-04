package com.digitalocean.llmproxy.exception;

/**
 * Raised when an LLM upstream returns an error or an unexpected transport failure occurs.
 */
public class LlmUpstreamException extends RuntimeException {

    public LlmUpstreamException(String message) {
        super(message);
    }

    public LlmUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
