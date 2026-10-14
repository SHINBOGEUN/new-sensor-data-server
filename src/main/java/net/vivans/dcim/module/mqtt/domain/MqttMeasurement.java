package net.vivans.dcim.module.mqtt.domain;

public record MqttMeasurement(String pointName, double value,
        String componentType, String componentIndex, String componentUuid) {
}
