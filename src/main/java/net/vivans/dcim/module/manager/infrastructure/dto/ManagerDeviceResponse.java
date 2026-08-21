package net.vivans.dcim.module.manager.infrastructure.dto;

public record ManagerDeviceResponse(
        Integer id,
        Integer modelId,
        String modelName,
        String manufacturer,
        String deviceTypeCode,
        String locationNodeCode,
        String locationNodeName,
        String name,
        String description,
        boolean enabled
) {
}
