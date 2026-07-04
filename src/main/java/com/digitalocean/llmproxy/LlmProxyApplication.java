package com.digitalocean.llmproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot entry point for the shadow LLM proxy service.
 * Boots the application and enables asynchronous shadow processing.
 */
@SpringBootApplication
@EnableAsync
public class LlmProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmProxyApplication.class, args);
    }
}
