package net.vivans.dcim.module.mqtt.domain;
public record PueMqttPayload(String datetime, Integer pueDefinitionId, Integer configVersion, Double value, Double totalPower, Double coolerPower) {}
