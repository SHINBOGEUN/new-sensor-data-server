package net.vivans.dcim.module.lora.infrastructure.dto;

public record LoraModelPointResponse(
        Integer id,
        Integer deviceModelId,
        String payloadField,
        String pointName,
        Integer dataPointTypeId,
        String dataPointTypeCode,
        String unit,
        Double scale,
        String valueMap,
        boolean enabled
) {
}
