package net.vivans.dcim.module.mqtt.domain;
import java.util.Map;
public record PueMqttPayload(String datetime, Integer pueDefinitionId, Integer configVersion, Double value,
                             Double totalPower, Double coolerPower, Map<String, Double> inputs) {
    public PueMqttPayload(String datetime, Integer pueDefinitionId, Integer configVersion, Double value,
                          Double totalPower, Double coolerPower) {
        this(datetime, pueDefinitionId, configVersion, value, totalPower, coolerPower, null);
    }
}
