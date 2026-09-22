package net.vivans.dcim.module.lora.infrastructure.dto;

/** Manager가 Sensor Data에 내려주는 연결 단위 MQTT 수집 소스 설정. */
public record LoraMqttSourceResponse(
        Integer id,
        String name,
        String sourceType,
        String brokerUrl,
        String topic,
        String clientId,
        String credentialKey,
        boolean enabled,
        long configVersion
) {
}
