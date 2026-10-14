package net.vivans.dcim.module.lora.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.lora.domain.LoraIdType;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraApiListResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraApiResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraDeviceLookupResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraEndpointResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraModelPointResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraOverridePointResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraMqttSourceResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraMqttSourceStatusReportRequest;
import net.vivans.dcim.module.manager.infrastructure.ManagerServiceProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/** 공통 MQTT 설정을 기존 LoRa 처리 모델로 변환한다. LoRa 소스 관리는 기존 API를 유지한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerLoraConfigClient {

    private final RestClient managerRestClient;
    private final ManagerServiceProperties properties;
    private final net.vivans.dcim.module.manager.infrastructure.ManagerMqttClient mqttClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public List<LoraEndpointResponse> findAllEnabledEndpoints() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            return mqttClient.identities().stream()
                    .filter(item -> item.enabled() && ("DEV_EUI".equals(item.identityType()) || "DEVICE_NAME".equals(item.identityType())))
                    .map(item -> new LoraEndpointResponse(item.identityId(), item.deviceId(), item.deviceName(),
                            item.deviceModelId(), item.deviceModelName(), LoraIdType.valueOf(item.identityType()),
                            item.externalId(), item.normalizedExternalId(), item.enabled())).toList();
        } catch (Exception exception) {
            log.warn("[LORA_CONFIG_FETCH_ERROR] target=endpoints exception={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return List.of();
        }
    }

    public List<LoraModelPointResponse> findAllEnabledModelMappings() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            LoraApiListResponse<com.fasterxml.jackson.databind.JsonNode> response = managerRestClient.get()
                    .uri("/api/manager/mqtt/mappings/models/bulk")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null || response.data() == null ? List.of() : response.data().stream()
                    .filter(item -> "DEVICE".equals(item.path("componentScope").asText()) && item.path("enabled").asBoolean())
                    .map(item -> objectMapper.convertValue(item, LoraModelPointResponse.class)).toList();
        } catch (Exception exception) {
            log.warn("[LORA_CONFIG_FETCH_ERROR] target=model-mappings exception={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return List.of();
        }
    }

    public List<LoraOverridePointResponse> findAllEnabledOverrides() {
        // Manager에서 장비별 override 테이블/API가 제거되었다.
        // 기존 LoRa 캐시 인터페이스는 유지하고 모델별 공통 MQTT 포인트만 사용한다.
        return List.of();
    }

    /** MQTT 연결 단위 소스 목록. 장비 endpoint와 달리 source 하나가 여러 LoRa 장비 메시지를 받는다. */
    public List<LoraMqttSourceResponse> findAllSources() {
        if (!properties.isEnabled()) return List.of();
        try {
            LoraApiListResponse<LoraMqttSourceResponse> response = managerRestClient.get()
                    .uri("/api/manager/lora/sources/bulk")
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            return response == null || response.data() == null ? List.of() : response.data();
        } catch (Exception exception) {
            log.warn("[LORA_SOURCE_FETCH_ERROR] message={}", exception.getMessage());
            return List.of();
        }
    }

    public void reportSourceStatus(Integer sourceId, LoraMqttSourceStatusReportRequest request) {
        if (!properties.isEnabled()) return;
        try {
            managerRestClient.post().uri("/api/manager/lora/sources/{sourceId}/status", sourceId)
                    .body(request).retrieve().toBodilessEntity();
        } catch (Exception exception) {
            log.debug("[LORA_SOURCE_STATUS_REPORT_ERROR] sourceId={} message={}", sourceId, exception.getMessage());
        }
    }

    /** 캐시 미스 시 즉시 조회용 (TTL 만료 전 새로 등록된 장비 대응). */
    public Optional<LoraDeviceLookupResponse> resolve(LoraIdType idType, String externalId) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            return mqttClient.resolve(idType.name(), externalId).filter(item -> item.enabled())
                    .map(item -> new LoraDeviceLookupResponse(item.deviceId(), item.deviceName(),
                            item.deviceModelId(), item.deviceModelName(), item.enabled()));
        } catch (Exception exception) {
            log.debug("[LORA_RESOLVE_MISS] idType={} externalId={} message={}", idType, externalId, exception.getMessage());
            return Optional.empty();
        }
    }
}
