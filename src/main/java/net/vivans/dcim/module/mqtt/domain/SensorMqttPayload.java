package net.vivans.dcim.module.mqtt.domain;

import java.util.Map;

public record SensorMqttPayload(
        String datetime,
        Map<String, Map<String, Object>> data,
        String type
) {
}
