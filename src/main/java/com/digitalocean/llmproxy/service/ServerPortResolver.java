package com.digitalocean.llmproxy.service;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Resolves the embedded web server's listening port at startup.
 * Used by {@link LLMMockService} to build self-referential localhost URLs for mock LLM calls.
 */
@Component
public class ServerPortResolver {

    private volatile int port = 8080;

    @EventListener
    void onWebServerReady(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    public int getPort() {
        return port;
    }
}
