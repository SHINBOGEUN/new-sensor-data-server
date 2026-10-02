package net.vivans.dcim.module.influx.application;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.influx.config.InfluxProperties;
import net.vivans.dcim.module.manager.infrastructure.dto.ManagerDeviceResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfluxWriteService {

    private final InfluxProperties properties;
    private InfluxDBClient client;
    private WriteApiBlocking writeApi;

    @PostConstruct
    public void connect() {
        if (!properties.isEnabled()) {
            log.info("InfluxDB write disabled");
            return;
        }
        client = InfluxDBClientFactory.create(
                properties.getUrl(),
                properties.getToken().toCharArray(),
                properties.getOrg(),
                properties.getBucket()
        );
        writeApi = client.getWriteApiBlocking();
        log.info("InfluxDB connected: url={} org={} bucket={}", properties.getUrl(), properties.getOrg(), properties.getBucket());
    }

    public void writeSensorPoints(
            int deviceId,
            ManagerDeviceResponse device,
            Map<String, Object> values,
            Instant collectedAt
    ) {
        writeSensorPoints(deviceId, device, values, collectedAt, null, null);
    }

    /**
     * protocol/component을 호출 측에서 지정할 수 있는 오버로드.
     * LoRa/MQTT 수집 경로는 protocol="mqtt" 로 호출한다. 기존 호출부는 위 4-arg 오버로드를 그대로 사용하며
     * 내부적으로 protocol=null(=snmp로 처리)로 위임되므로 동작 변화가 없다.
     */
    public void writeSensorPoints(
            int deviceId,
            ManagerDeviceResponse device,
            Map<String, Object> values,
            Instant collectedAt,
            String component,
            String protocol
    ) {
        if (!properties.isEnabled() || writeApi == null) {
            log.debug("Influx write skipped (disabled) deviceId={}", deviceId);
            return;
        }
        List<Point> points = SensorInfluxPointMapper.toPoints(
                properties.getMeasurement(),
                deviceId,
                device,
                values,
                collectedAt,
                component,
                protocol == null || protocol.isBlank() ? "snmp" : protocol
        );
        if (points.isEmpty()) {
            log.warn("Influx write skipped (no valid points) deviceId={}", deviceId);
            return;
        }
        writeApi.writePoints(points);
        log.info("Influx write deviceId={} protocol={} {}", deviceId, protocol, formatKeyValues(values));
    }


    public void writeCalculated(Integer definitionId, Integer configVersion, Double value,
                                Map<String, Double> inputs, Instant collectedAt) {
        if (!properties.isEnabled() || writeApi == null) return;
        if (definitionId == null || value == null || !Double.isFinite(value)
                || inputs == null || inputs.isEmpty() || inputs.values().stream().anyMatch(v -> v == null || !Double.isFinite(v))) {
            throw new IllegalArgumentException("invalid calculated metric payload");
        }
        Point point = Point.measurement(properties.getMeasurement())
                .addTag("metric_kind", "calculated")
                .addTag("calculated_metric_id", String.valueOf(definitionId))
                .addTag("calculated_config_version", String.valueOf(configVersion == null ? 1 : configVersion))
                .addTag("point_name", "CALCULATED")
                .addTag("protocol", "derived")
                .addField("value", value)
                .time(collectedAt == null ? Instant.now() : collectedAt, com.influxdb.client.domain.WritePrecision.MS);
        for (Map.Entry<String, Double> input : inputs.entrySet()) {
            if (input.getKey() == null || !input.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,31}")) {
                throw new IllegalArgumentException("invalid calculated source alias");
            }
            point.addField("input_" + input.getKey(), input.getValue());
        }
        writeApi.writePoint(point);
        log.info("[CALCULATED_INFLUX_END] definitionId={} version={} sourceCount={}",
                definitionId, configVersion, inputs.size());
    }

    /** 실제 적재되는 point만 `key=value` 형태로 연결 */
    private static String formatKeyValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .map(e -> {
                    Double value = toDoubleOrNull(e.getValue());
                    return value == null ? null : e.getKey() + "=" + value;
                })
                .filter(s -> s != null)
                .collect(Collectors.joining(" "));
    }

    private static Double toDoubleOrNull(Object value) {
        if (value instanceof Number number) {
            double converted = number.doubleValue();
            return Double.isFinite(converted) ? converted : null;
        }
        if (value == null) {
            return null;
        }
        try {
            double converted = Double.parseDouble(String.valueOf(value).trim());
            return Double.isFinite(converted) ? converted : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
