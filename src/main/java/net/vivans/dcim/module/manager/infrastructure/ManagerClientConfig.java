package net.vivans.dcim.module.manager.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ManagerServiceProperties.class)
public class ManagerClientConfig {

    @Bean
    public RestClient managerRestClient(ManagerServiceProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeader("X-Api-Key", properties.getApiKey())
                .build();
    }
}
