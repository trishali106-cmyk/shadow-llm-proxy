package com.digitalocean.llmproxy.support;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * Stable identifier for the running container or process.
 *
 * <p>Exposed as {@code instance_id} in {@code GET /metrics} so operators can tell which replica
 * served a snapshot when counters are per-instance ({@code scope: "instance"}).
 *
 * <p>Resolution order: {@code HOSTNAME} env → {@code INSTANCE_ID} env → local hostname → random UUID.
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

    /** Container hostname or fallback identifier for this JVM. */
    public String getInstanceId() {
        return instanceId;
    }
}
