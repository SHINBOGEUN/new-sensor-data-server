package net.vivans.dcim.module.manager.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "manager.service")
public class ManagerServiceProperties {

    private String url = "http://localhost:8080";
    private String apiKey = "manager-server";
    private boolean enabled = true;
}
