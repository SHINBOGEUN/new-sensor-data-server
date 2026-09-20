package net.vivans.dcim.module.mqtt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dragino/LoRa(ChirpStack 등 LoRaWAN 네트워크 서버) 전용 MQTT 브로커 설정.
 * 확정 전제: 브로커 host/port는 DB/UI가 아니라 이 환경설정으로만 관리한다.
 * 게이트웨이/네트워크 서버가 이미 필요한 토픽만 발행하므로 구독 토픽은 application/# 고정이며
 * 별도 토픽 관리 테이블·UI는 만들지 않는다 (환경변수로만 조정 가능하게 남겨둔다).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sensor.mqtt.lora")
public class LoraMqttProperties {

    private boolean enabled = false;
    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "new-sensor-data-server-lora";
    private String topic = "application/#";
    private String username = "";
    private String password = "";

    /** ChirpStack uplink event에서 devEUI를 찾을 JSON 경로. 실제 payload로 미확인 — 확인 전까지 가정값. */
    private String devEuiPath = "deviceInfo.devEui";

    /** ChirpStack uplink event에서 deviceName을 찾을 JSON 경로. 레거시 sensor-data-service 코드로 확인됨. */
    private String deviceNamePath = "deviceInfo.deviceName";

    /** Manager 설정(endpoint/매핑) TTL 캐시 갱신 주기(ms) */
    private long configCacheRefreshMs = 300_000L;

    /** 오류 로그로 보낼 raw payload 최대 길이 (Manager DB 컬럼 한도인 4000보다 작거나 같게 유지) */
    private int errorRawPayloadMaxLength = 2000;
}
