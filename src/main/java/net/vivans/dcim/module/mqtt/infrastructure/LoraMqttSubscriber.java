package net.vivans.dcim.module.mqtt.infrastructure;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.lora.application.LoraMqttMessageHandler;
import net.vivans.dcim.module.mqtt.config.LoraMqttProperties;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dragino/LoRa(ChirpStack) 전용 MQTT 구독자.
 * 기존 PahoMqttSubscriber(dcim/sensor/data, dcim/derived/pue 토픽)와는 별도 브로커 연결/토픽(application/#)을
 * 쓰므로 분리된 클라이언트로 둔다. 기존 SNMP/Modbus/PUE MQTT 수집 경로는 전혀 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sensor.mqtt.lora.enabled", havingValue = "true")
public class LoraMqttSubscriber {

    private final LoraMqttProperties properties;
    private final LoraMqttMessageHandler messageHandler;
    private MqttClient client;

    @PostConstruct
    public void connectAndSubscribe() {
        if (!properties.isEnabled()) {
            log.info("LoRa MQTT subscribe disabled");
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
                    log.warn("LoRa MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
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
            log.info("[LORA_MQTT_SUBSCRIBE_END] topic={} broker={}", properties.getTopic(), properties.getBrokerUrl());
        } catch (MqttException exception) {
            log.warn("[LORA_MQTT_SUBSCRIBE_ERROR] broker={} exception={} message={}",
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
            log.warn("LoRa MQTT close failed: {}", exception.getMessage());
        }
    }
}
