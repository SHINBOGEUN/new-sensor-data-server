package net.vivans.dcim.module.influx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "influx")
public class InfluxProperties {

    private boolean enabled;
    private String url = "http://localhost:8086";
    private String token = "";
    private String org = "vivans";
    private String bucket = "dcim";
    private String measurement = "dcim_sensor";
}
