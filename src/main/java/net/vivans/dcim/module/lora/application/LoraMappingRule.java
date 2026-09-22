package net.vivans.dcim.module.lora.application;

/** LoRa 모델 매핑을 수집 경로에서 다루기 위한 내부 표현. */
public record LoraMappingRule(
        String payloadField,
        String pointName,
        String dataPointTypeCode,
        String unit,
        Double scale,
        String valueMap
) {
}
