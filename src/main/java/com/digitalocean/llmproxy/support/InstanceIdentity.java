package com.digitalocean.llmproxy.support;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Stable identifier for the running container/process, exposed in {@code GET /metrics}.
 */
@Component
public class InstanceIdentity {

    private final String instanceId;

    public InstanceIdentity() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = System.getenv("INSTANCE_ID");
        }
        if (hostname == null || hostname.isBlank()) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ignored) {
                hostname = null;
            }
        }
        if (hostname == null || hostname.isBlank()) {
            hostname = UUID.randomUUID().toString();
        }
        this.instanceId = hostname;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
