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
        if (!properties.isEnabled() || writeApi == null) {
            log.debug("Influx write skipped (disabled) deviceId={}", deviceId);
            return;
        }
        List<Point> points = SensorInfluxPointMapper.toPoints(
                properties.getMeasurement(),
                deviceId,
                device,
                values,
                collectedAt
        );
        if (points.isEmpty()) {
            log.warn("Influx write skipped (no valid points) deviceId={}", deviceId);
            return;
        }
        writeApi.writePoints(points);
        log.info("Influx write deviceId={} {}", deviceId, formatKeyValues(values));
    }

    public void writePue(Integer definitionId, Integer configVersion, Double value, Double totalPower, Double coolerPower, Instant collectedAt) {
        if (!properties.isEnabled() || writeApi == null) {
            log.debug("[PUE_INFLUX_SKIP] definitionId={} reason=CLIENT_DISABLED", definitionId);
            return;
        }
        if (definitionId == null || value == null || !Double.isFinite(value)) {
            log.warn("[PUE_INFLUX_SKIP] definitionId={} reason=INVALID_PAYLOAD", definitionId);
            return;
        }
        try {
            Point point = Point.measurement(properties.getMeasurement())
                    .addTag("metric_kind", "pue")
                    .addTag("pue_definition_id", String.valueOf(definitionId))
                    .addTag("pue_config_version", String.valueOf(configVersion == null ? 1 : configVersion))
                    .addTag("point_name", "PUE")
                    .addTag("protocol", "derived")
                    .addField("value", value)
                    .addField("total_power", totalPower == null ? 0D : totalPower)
                    .addField("cooler_power", coolerPower == null ? 0D : coolerPower)
                    .time(collectedAt == null ? Instant.now() : collectedAt, com.influxdb.client.domain.WritePrecision.MS);
            writeApi.writePoint(point);
            log.info("[PUE_INFLUX_END] action=WRITE definitionId={} configVersion={} measurement={}",
                    definitionId, configVersion == null ? 1 : configVersion, properties.getMeasurement());
        } catch (Exception exception) {
            log.warn("[PUE_INFLUX_ERROR] action=WRITE definitionId={} exception={} message={}",
                    definitionId, exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
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
