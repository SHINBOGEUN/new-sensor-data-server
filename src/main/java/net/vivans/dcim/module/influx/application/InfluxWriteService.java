package net.vivans.dcim.module.influx.application;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.influx.config.InfluxProperties;
import net.vivans.dcim.module.manager.infrastructure.dto.ManagerDeviceResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

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
        if (!properties.isEnabled() || writeApi == null || values == null || values.isEmpty()) {
            return;
        }
        String locationCode = device == null ? "unknown" : nullToUnknown(device.locationNodeName());
        Integer modelId = device == null ? null : device.modelId();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Point point = Point.measurement(properties.getMeasurement())
                    .addTag("device_id", String.valueOf(deviceId))
                    .addTag("model_id", modelId == null ? "unknown" : String.valueOf(modelId))
                    .addTag("location_code", locationCode)
                    .addTag("point_name", entry.getKey())
                    .addTag("protocol", "snmp")
                    .addField("value", toDouble(entry.getValue()))
                    .time(collectedAt, WritePrecision.MS);
            writeApi.writePoint(point);
        }
        log.debug("Influx write deviceId={} points={}", deviceId, values.size());
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0d;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
