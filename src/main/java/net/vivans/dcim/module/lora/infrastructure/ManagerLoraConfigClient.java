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

/** Sensor Data가 Manager의 LoRa 설정(endpoint/모델매핑/override)을 읽어오는 클라이언트. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerLoraConfigClient {

    private final RestClient managerRestClient;
    private final ManagerServiceProperties properties;

    public List<LoraEndpointResponse> findAllEnabledEndpoints() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            LoraApiListResponse<LoraEndpointResponse> response = managerRestClient.get()
                    .uri("/api/manager/lora/endpoints/bulk")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null || response.data() == null ? List.of() : response.data();
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
            LoraApiListResponse<LoraModelPointResponse> response = managerRestClient.get()
                    .uri("/api/manager/lora/mappings/models/bulk")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null || response.data() == null ? List.of() : response.data();
        } catch (Exception exception) {
            log.warn("[LORA_CONFIG_FETCH_ERROR] target=model-mappings exception={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return List.of();
        }
    }

    public List<LoraOverridePointResponse> findAllEnabledOverrides() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            LoraApiListResponse<LoraOverridePointResponse> response = managerRestClient.get()
                    .uri("/api/manager/lora/mappings/overrides/bulk")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null || response.data() == null ? List.of() : response.data();
        } catch (Exception exception) {
            log.warn("[LORA_CONFIG_FETCH_ERROR] target=overrides exception={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return List.of();
        }
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
            LoraApiResponse<LoraDeviceLookupResponse> response = managerRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/manager/lora/endpoints/resolve")
                            .queryParam("idType", idType)
                            .queryParam("externalId", externalId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response == null ? Optional.empty() : Optional.ofNullable(response.data());
        } catch (Exception exception) {
            log.debug("[LORA_RESOLVE_MISS] idType={} externalId={} message={}", idType, externalId, exception.getMessage());
            return Optional.empty();
        }
    }
}
