package net.vivans.dcim.module.lora.infrastructure.dto;

import net.vivans.dcim.module.lora.domain.LoraIdType;

public record LoraEndpointResponse(
        Integer id,
        Integer deviceId,
        String deviceName,
        Integer deviceModelId,
        String deviceModelName,
        LoraIdType idType,
        String externalId,
        String normalizedExternalId,
        boolean enabled
) {
}
