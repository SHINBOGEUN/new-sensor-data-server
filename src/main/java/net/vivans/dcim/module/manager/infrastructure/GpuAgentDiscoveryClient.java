package net.vivans.dcim.module.manager.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class GpuAgentDiscoveryClient {
    private final RestClient client;
    private final boolean enabled;

    public GpuAgentDiscoveryClient(ManagerServiceProperties properties, @Value("${security.api-key}") String key) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(properties.getUrl()).requestFactory(factory)
                .defaultHeader("X-Api-Key", key).build();
        enabled = properties.isEnabled();
    }

    public void discover(String uuid, String hostname, JsonNode metadata) {
        if (!enabled) return;
        try {
            client.post().uri("/api/internal/gpu-agents/discover")
                    .body(new Discovery(uuid, hostname, metadata)).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("GPU agent discovery failed: {}", e.getMessage());
        }
    }
    private record Discovery(String uuid, String hostname, JsonNode metadata) {}
}

