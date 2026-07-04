package com.digitalocean.llmproxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

/**
 * Async and timing configuration for shadow processing.
 * Defines the virtual-thread executor, mock delay/timeout properties, and shadow enable flag.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Java 21 virtual threads for shadow processing — lightweight, decoupled from servlet threads,
     * and survives client disconnect because work is scheduled before the response is flushed.
     */
    @Bean(name = "shadowTaskExecutor")
    public Executor shadowTaskExecutor() {
        return new VirtualThreadTaskExecutor("shadow-vt-");
    }

    @Bean
    LlmTimingProperties llmTimingProperties(
            @Value("${llm.mock.primary-delay-ms:100}") long primaryDelayMs,
            @Value("${llm.mock.candidate-delay-ms:500}") long candidateDelayMs,
            @Value("${llm.timeout.primary-ms:500}") long primaryTimeoutMs,
            @Value("${llm.timeout.candidate-ms:2000}") long candidateTimeoutMs,
            @Value("${llm.shadow.enabled:true}") boolean shadowEnabled) {
        return new LlmTimingProperties(primaryDelayMs, candidateDelayMs, primaryTimeoutMs, candidateTimeoutMs, shadowEnabled);
    }

    public record LlmTimingProperties(
            long primaryDelayMs,
            long candidateDelayMs,
            long primaryTimeoutMs,
            long candidateTimeoutMs,
            boolean shadowEnabled
    ) {}
}
