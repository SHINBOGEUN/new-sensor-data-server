package net.vivans.dcim.module.mqtt.application;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.influx.application.InfluxWriteService;
import net.vivans.dcim.module.manager.infrastructure.ManagerDeviceClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentMqttMessageHandler {
    private final MqttConfigurationService configurationService;
    private final MqttPointExtractor extractor;
    private final ManagerDeviceClient devices;
    private final InfluxWriteService writer;

    public void handle(JsonNode root) {
        // 잘못된 시간을 현재 시각으로 대체하지 않는다.
        var time = OffsetDateTime.parse(root.path("time").asText()).toInstant();
        JsonNode info = root.path("deviceInfo");
        String type;
        String externalId;
        if (info.hasNonNull("identityType") && info.hasNonNull("externalId")) {
            type = info.path("identityType").asText();
            externalId = info.path("externalId").asText();
        } else {
            throw new IllegalArgumentException("Agent deviceInfo.identityType/externalId are required; LoRa uses its dedicated handler");
        }
        var resolved = configurationService.resolve(type, externalId).orElse(null);
        if (resolved == null) {
            log.debug("Unregistered or disabled MQTT device skipped identityType={}", type);
            return;
        }
        var identity = resolved.identity();
        var measurements = extractor.extract(root, resolved.points());
        if (measurements.isEmpty()) {
            log.debug("MQTT device has no configured valid values deviceId={}", identity.deviceId());
            return;
        }
        var device = devices.findDevice(identity.deviceId()).orElse(null);
        if (device != null && !device.enabled()) return;
        writer.writeMqttPoints(identity.deviceId(), identity.deviceModelId(), device, measurements, time);
    }
}
