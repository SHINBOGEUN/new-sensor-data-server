package net.vivans.dcim.module.mqtt.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.influx.application.InfluxWriteService;
import net.vivans.dcim.module.manager.infrastructure.ManagerDeviceClient;
import net.vivans.dcim.module.manager.infrastructure.dto.ManagerDeviceResponse;
import net.vivans.dcim.module.mqtt.domain.SensorMqttPayload;
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

    public void handle(String topic, byte[] payload) {
        try {
            SensorMqttPayload message = objectMapper.readValue(payload, SensorMqttPayload.class);
            if (message.data() == null || message.data().isEmpty()) {
                log.debug("MQTT payload has no data topic={}", topic);
                return;
            }
            Instant collectedAt = parseCollectedAt(message.datetime());
            for (Map.Entry<String, Map<String, Object>> entry : message.data().entrySet()) {
                int deviceId = Integer.parseInt(entry.getKey());
                ManagerDeviceResponse device = managerDeviceClient.findDevice(deviceId).orElse(null);
                influxWriteService.writeSensorPoints(deviceId, device, entry.getValue(), collectedAt);
                log.info(
                        "sensor message processed deviceId={} pointCount={} type={}",
                        deviceId,
                        entry.getValue() == null ? 0 : entry.getValue().size(),
                        message.type()
                );
            }
        } catch (Exception exception) {
            log.warn("MQTT message handling failed topic={}: {}", topic, exception.getMessage());
        }
    }

    private Instant parseCollectedAt(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            return Instant.now();
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(datetime, DATETIME);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception exception) {
            return Instant.now();
        }
    }
}
