package net.vivans.dcim.module.mqtt.infrastructure;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.mqtt.application.SensorMqttMessageHandler;
import net.vivans.dcim.module.mqtt.config.SensorMqttProperties;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sensor.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class PahoMqttSubscriber {

    private final SensorMqttProperties properties;
    private final SensorMqttMessageHandler messageHandler;
    private MqttClient client;

    @PostConstruct
    public void connectAndSubscribe() {
        if (!properties.isEnabled()) {
            log.info("MQTT subscribe disabled");
            return;
        }
        try {
            client = new MqttClient(properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
                options.setUserName(properties.getUsername());
                options.setPassword(
                        properties.getPassword() == null ? new char[0] : properties.getPassword().toCharArray()
                );
            }
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    messageHandler.handle(topic, message.getPayload());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            client.connect(options);
            client.subscribe(properties.getTopic());
            client.subscribe(properties.getPueTopic());
            log.info("[MQTT_SUBSCRIBE_END] topics=[{}, {}] broker={}",
                    properties.getTopic(), properties.getPueTopic(), properties.getBrokerUrl());
        } catch (MqttException exception) {
            log.warn("[MQTT_SUBSCRIBE_ERROR] broker={} exception={} message={}",
                    properties.getBrokerUrl(), exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException exception) {
            log.warn("MQTT close failed: {}", exception.getMessage());
        }
    }
}
