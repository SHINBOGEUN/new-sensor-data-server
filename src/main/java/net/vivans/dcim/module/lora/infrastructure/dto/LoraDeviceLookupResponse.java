package net.vivans.dcim.module.lora.infrastructure.dto;

public record LoraDeviceLookupResponse(
        Integer deviceId,
        String deviceName,
        Integer deviceModelId,
        String deviceModelName,
        boolean enabled
) {
}
