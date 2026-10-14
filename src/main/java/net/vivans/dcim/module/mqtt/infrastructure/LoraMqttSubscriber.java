package net.vivans.dcim.module.mqtt.infrastructure;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.lora.application.LoraConfigCache;
import net.vivans.dcim.module.lora.application.LoraMqttMessageHandler;
import net.vivans.dcim.module.lora.infrastructure.ManagerLoraConfigClient;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraMqttSourceResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraMqttSourceStatusReportRequest;
import net.vivans.dcim.module.mqtt.config.LoraMqttProperties;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manager가 소유한 MQTT 수집 소스(Broker + Topic)를 동적으로 연결·재조정한다.
 * device_lora_endpoint는 메시지 내부 devEUI/deviceName 매핑용이고 MQTT 연결 단위가 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoraMqttSubscriber {

    private static final String DEFAULT_CLIENT_ID_PREFIX = "new-sensor-data-server-lora";

    private final LoraMqttProperties properties;
    private final LoraMqttMessageHandler messageHandler;
    private final LoraConfigCache loraConfigCache;
    private final ManagerLoraConfigClient managerLoraConfigClient;
    private final Map<Integer, ManagedSource> sources = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialRefresh() {
        refreshSources();
    }

    /** Manager의 즉시 이벤트가 유실되어도 1분마다 원하는 상태로 맞춘다. */
    @Scheduled(fixedDelayString = "${sensor.mqtt.lora.source-sync-fixed-delay-ms:60000}")
    public synchronized void refreshSources() {
        // 기능 on/off는 환경변수가 아닌 Manager의 활성 LoRa endpoint 존재 여부가 기준이다.
        loraConfigCache.refresh();
        if (!loraConfigCache.hasConfiguredEndpoint()) {
            if (!sources.isEmpty()) {
                sources.values().forEach(source -> source.close("NOT_SYNCED"));
                sources.clear();
            }
            log.info("[LORA_MQTT_SUBSCRIBE_SKIPPED] reason=no_enabled_lora_endpoint");
            return;
        }

        List<LoraMqttSourceResponse> configuredSources = managerLoraConfigClient.findAllSources();
        if (configuredSources.isEmpty()) {
            log.info("[LORA_MQTT_SUBSCRIBE_SKIPPED] reason=no_mqtt_source");
        }
        Map<Integer, LoraMqttSourceResponse> desired = new HashMap<>();
        for (LoraMqttSourceResponse source : configuredSources) {
            if (source.id() != null) desired.put(source.id(), source);
        }

        sources.entrySet().removeIf(entry -> {
            if (desired.containsKey(entry.getKey())) return false;
            entry.getValue().close("DISCONNECTED");
            return true;
        });

        for (LoraMqttSourceResponse source : desired.values()) {
            ManagedSource current = sources.get(source.id());
            if (!source.enabled()) {
                if (current != null) {
                    current.close("DISABLED");
                } else {
                    report(source.id(), "DISABLED", null, null, 0, 0, 0, null);
                }
                sources.remove(source.id());
                continue;
            }
            if (current != null && current.configVersion == source.configVersion()) {
                current.reportHeartbeat();
                continue;
            }
            if (current != null) current.close("DISCONNECTED");
            ManagedSource created = new ManagedSource(source);
            sources.put(source.id(), created);
            created.connectAndSubscribe();
        }
        log.info("[LORA_MQTT_SOURCE_RECONCILE_END] configured={} connectedClients={}", desired.size(), sources.size());
    }

    /** Manager가 source CRUD 후 호출하는 즉시 재조정 endpoint가 위임한다. */
    public void requestRefresh() {
        refreshSources();
    }

    @PreDestroy
    public void close() {
        sources.values().forEach(source -> source.close("DISCONNECTED"));
        sources.clear();
    }

    private void report(Integer sourceId, String status, Instant lastConnectedAt, Instant lastMessageAt,
                        long messageCount, long errorCount, long reconnectCount, String lastError) {
        managerLoraConfigClient.reportSourceStatus(sourceId, new LoraMqttSourceStatusReportRequest(
                status, lastConnectedAt, lastMessageAt, messageCount, errorCount, reconnectCount, lastError, Instant.now()));
    }

    private final class ManagedSource {
        private final LoraMqttSourceResponse source;
        private final long configVersion;
        private final AtomicLong messageCount = new AtomicLong();
        private final AtomicLong errorCount = new AtomicLong();
        private final AtomicLong reconnectCount = new AtomicLong();
        private volatile MqttClient client;
        private volatile Instant lastConnectedAt;
        private volatile Instant lastMessageAt;
        private volatile String lastError;

        private ManagedSource(LoraMqttSourceResponse source) {
            this.source = source;
            this.configVersion = source.configVersion();
        }

        private void connectAndSubscribe() {
            reportState("CONNECTING");
            try {
                String clientId = source.clientId() == null || source.clientId().isBlank()
                        ? DEFAULT_CLIENT_ID_PREFIX + "-" + source.id() : source.clientId();
                client = new MqttClient(source.brokerUrl(), clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                LoraMqttProperties.Credentials credentials = properties.credentialsFor(source.credentialKey());
                if (credentials.getUsername() != null && !credentials.getUsername().isBlank()) {
                    options.setUserName(credentials.getUsername());
                    options.setPassword(credentials.getPassword() == null ? new char[0] : credentials.getPassword().toCharArray());
                }
                client.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverUri) {
                        if (!reconnect) return;
                        try {
                            client.subscribe(source.topic());
                            lastConnectedAt = Instant.now();
                            lastError = null;
                            log.info("[LORA_MQTT_RESUBSCRIBE_END] sourceId={} topic={} broker={}",
                                    source.id(), source.topic(), serverUri);
                            reportState("CONNECTED");
                        } catch (MqttException exception) {
                            errorCount.incrementAndGet();
                            lastError = exception.getMessage();
                            reportState("ERROR");
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        reconnectCount.incrementAndGet();
                        lastError = cause == null ? "connection lost" : cause.getMessage();
                        log.warn("[LORA_MQTT_CONNECTION_LOST] sourceId={} name={} message={}", source.id(), source.name(), lastError);
                        reportState("DISCONNECTED");
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        lastMessageAt = Instant.now();
                        messageCount.incrementAndGet();
                        try {
                            messageHandler.handle(topic, message.getPayload());
                            reportState("CONNECTED");
                        } catch (Exception exception) {
                            errorCount.incrementAndGet();
                            lastError = exception.getMessage();
                            log.warn("[LORA_MQTT_RECEIVE_ERROR] sourceId={} topic={} message={}", source.id(), topic, lastError);
                            reportState("ERROR");
                        }
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                    }
                });
                client.connect(options);
                client.subscribe(source.topic());
                lastConnectedAt = Instant.now();
                lastError = null;
                log.info("[LORA_MQTT_SUBSCRIBE_END] sourceId={} name={} topic={} broker={}",
                        source.id(), source.name(), source.topic(), source.brokerUrl());
                reportState("CONNECTED");
            } catch (MqttException exception) {
                errorCount.incrementAndGet();
                lastError = exception.getMessage();
                log.warn("[LORA_MQTT_SUBSCRIBE_ERROR] sourceId={} name={} broker={} exception={} message={}",
                        source.id(), source.name(), source.brokerUrl(), exception.getClass().getSimpleName(), lastError);
                reportState("ERROR");
            }
        }

        private void reportHeartbeat() {
            reportState(client != null && client.isConnected() ? "CONNECTED" : "DISCONNECTED");
        }

        private void reportState(String state) {
            report(source.id(), state, lastConnectedAt, lastMessageAt, messageCount.get(), errorCount.get(), reconnectCount.get(), lastError);
        }

        private void close(String state) {
            try {
                if (client != null && client.isConnected()) client.disconnect();
                if (client != null) client.close();
            } catch (MqttException exception) {
                lastError = exception.getMessage();
            } finally {
                reportState(state);
            }
        }
    }
}
