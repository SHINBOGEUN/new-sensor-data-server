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
            return;
        }
        writeApi.writePoints(points);
        log.debug("Influx write deviceId={} pointCount={}", deviceId, points.size());
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
