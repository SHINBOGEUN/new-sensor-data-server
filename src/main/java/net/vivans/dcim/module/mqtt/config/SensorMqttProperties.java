package net.vivans.dcim.module.mqtt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "sensor.mqtt")
public class SensorMqttProperties {

    private boolean enabled = true;
    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "new-sensor-data-server";
    private String topic = "dcim/sensor/data";
    private String pueTopic = "dcim/derived/pue";
    private String username = "";
    private String password = "";
}
