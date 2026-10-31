package io.github.prakarshsrivastav.jobengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

@Getter
@Setter
@ConfigurationProperties(prefix = "jobengine")
public class JobEngineProperties {

    private String nodeId;
    private Worker worker = new Worker();
    private Retry retry = new Retry();

    public String getNodeId() {
        if (nodeId == null || nodeId.isBlank()) {
            nodeId = defaultNodeId();
        }
        return nodeId;
    }

    private static String defaultNodeId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "node";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    @Getter
    @Setter
    public static class Worker {
        private boolean enabled = true;
        private int threads = 4;
        private Duration pollInterval = Duration.ofMillis(500);
        private Duration heartbeatInterval = Duration.ofSeconds(5);
        private Duration leaseTimeout = Duration.ofSeconds(30);
        private Duration reaperInterval = Duration.ofSeconds(10);
    }

    @Getter
    @Setter
    public static class Retry {
        private Duration baseBackoff = Duration.ofSeconds(2);
        private Duration maxBackoff = Duration.ofMinutes(5);
    }
}
