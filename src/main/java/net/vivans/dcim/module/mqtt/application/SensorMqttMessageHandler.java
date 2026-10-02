package net.vivans.dcim.module.mqtt.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.influx.application.InfluxWriteService;
import net.vivans.dcim.module.manager.infrastructure.ManagerDeviceClient;
import net.vivans.dcim.module.manager.infrastructure.dto.ManagerDeviceResponse;
import net.vivans.dcim.module.mqtt.domain.SensorMqttPayload;
import net.vivans.dcim.module.mqtt.domain.PueMqttPayload;
import net.vivans.dcim.module.mqtt.config.SensorMqttProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorMqttMessageHandler {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final ManagerDeviceClient managerDeviceClient;
    private final InfluxWriteService influxWriteService;
    private final SensorMqttProperties mqttProperties;

    public void handle(String topic, byte[] payload) {
        long startedAt = System.nanoTime();
        try {
            if (topic.equals(mqttProperties.getPueTopic())) {
                PueMqttPayload pue = objectMapper.readValue(payload, PueMqttPayload.class);
                String kind = pue.inputs() == null ? "PUE_LEGACY" : "CALCULATED";
                log.info("[DERIVED_MQTT_RECEIVE_START] kind={} definitionId={} configVersion={} topic={}",
                        kind, pue.pueDefinitionId(), pue.configVersion(), topic);
                if (pue.inputs() != null) {
                    influxWriteService.writeCalculated(pue.pueDefinitionId(), pue.configVersion(), pue.value(),
                            pue.inputs(), parseCollectedAt(pue.datetime()));
                } else {
                    influxWriteService.writePue(pue.pueDefinitionId(), pue.configVersion(), pue.value(),
                            pue.totalPower(), pue.coolerPower(), parseCollectedAt(pue.datetime()));
                }
                log.info("[DERIVED_MQTT_RECEIVE_END] kind={} definitionId={} topic={} elapsedMs={}",
                        kind, pue.pueDefinitionId(), topic, elapsedMillis(startedAt));
                return;
            }
            SensorMqttPayload message = objectMapper.readValue(payload, SensorMqttPayload.class);
            String protocol = message.resolvedProtocol();
            if (message.data() == null || message.data().isEmpty()) {
                log.debug("MQTT payload has no data topic={}", topic);
                return;
            }
            Instant collectedAt = parseCollectedAt(message.datetime());
            for (Map.Entry<String, Map<String, Object>> entry : message.data().entrySet()) {
                int deviceId = Integer.parseInt(entry.getKey());
                ManagerDeviceResponse device = managerDeviceClient.findDevice(deviceId).orElse(null);
                influxWriteService.writeSensorPoints(deviceId, device, entry.getValue(), collectedAt, null, protocol);
                log.info(
                        "sensor message processed deviceId={} pointCount={} type={} protocol={}",
                        deviceId,
                        entry.getValue() == null ? 0 : entry.getValue().size(),
                        message.type(),
                        protocol
                );
            }
        } catch (Exception exception) {
            log.warn("[MQTT_RECEIVE_ERROR] topic={} elapsedMs={} exception={} message={}",
                    topic, elapsedMillis(startedAt), exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    private static long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private Instant parseCollectedAt(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(datetime);
        } catch (Exception ignored) {
            // 기존 센서 payload의 시간대 없는 datetime 형식은 아래에서 처리한다.
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(datetime, DATETIME);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception exception) {
            return Instant.now();
        }
    }
}
