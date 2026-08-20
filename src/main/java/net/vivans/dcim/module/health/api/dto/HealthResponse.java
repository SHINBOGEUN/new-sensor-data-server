package net.vivans.dcim.module.health.api.dto;

public record HealthResponse(
        String status,
        String service
) {
}
