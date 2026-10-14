package net.vivans.dcim.module.lora.application;

public record LoraResolvedDevice(
        Integer deviceId,
        String deviceName,
        Integer deviceModelId,
        String deviceModelName
) {
}
