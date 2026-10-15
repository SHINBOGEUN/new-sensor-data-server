package net.vivans.dcim.module.mqtt.domain;

import java.util.Map;

public record CalculatedMqttPayload(String datetime, Integer calculatedMetricId, Integer configVersion,
                                    Double value, Map<String, Double> inputs) { }
