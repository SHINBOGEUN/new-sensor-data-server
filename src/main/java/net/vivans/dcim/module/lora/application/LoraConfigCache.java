package net.vivans.dcim.module.lora.application;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.lora.domain.LoraExternalIdNormalizer;
import net.vivans.dcim.module.lora.domain.LoraIdType;
import net.vivans.dcim.module.lora.infrastructure.ManagerLoraConfigClient;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraDeviceLookupResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraEndpointResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraModelPointResponse;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraOverridePointResponse;
import net.vivans.dcim.module.mqtt.config.LoraMqttProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manager의 LoRa 설정(endpoint/모델매핑/override)을 TTL로 캐시한다.
 * 우선순위: device별 override → device model 기본 매핑 → 없으면 미매핑.
 * 캐시 미스(신규 등록 직후 등)는 Manager에 즉시 단건 조회로 보완한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sensor.mqtt.lora.enabled", havingValue = "true")
public class LoraConfigCache {

    private final ManagerLoraConfigClient managerLoraConfigClient;
    private final LoraMqttProperties properties;

    private final AtomicReference<Map<String, LoraResolvedDevice>> endpointsByKey =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Map<Integer, Map<String, LoraMappingRule>>> modelMappingsByModelId =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Map<Integer, Map<String, LoraMappingRule>>> overridesByDeviceId =
            new AtomicReference<>(Map.of());

    @PostConstruct
    public void initialLoad() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${sensor.mqtt.lora.config-cache-refresh-ms:300000}")
    public void refresh() {
        try {
            Map<String, LoraResolvedDevice> endpoints = new HashMap<>();
            for (LoraEndpointResponse endpoint : managerLoraConfigClient.findAllEnabledEndpoints()) {
                String key = key(endpoint.idType(), endpoint.normalizedExternalId());
                endpoints.put(key, new LoraResolvedDevice(
                        endpoint.deviceId(), endpoint.deviceName(), endpoint.deviceModelId(), endpoint.deviceModelName()));
            }
            endpointsByKey.set(Map.copyOf(endpoints));

            Map<Integer, Map<String, LoraMappingRule>> modelMappings = new HashMap<>();
            for (LoraModelPointResponse point : managerLoraConfigClient.findAllEnabledModelMappings()) {
                modelMappings
                        .computeIfAbsent(point.deviceModelId(), ignored -> new HashMap<>())
                        .put(point.payloadField(), new LoraMappingRule(
                                point.payloadField(), point.pointName(), point.dataPointTypeCode(),
                                point.unit(), point.scale(), point.valueMap()));
            }
            modelMappingsByModelId.set(Map.copyOf(modelMappings));

            Map<Integer, Map<String, LoraMappingRule>> overrides = new HashMap<>();
            for (LoraOverridePointResponse point : managerLoraConfigClient.findAllEnabledOverrides()) {
                overrides
                        .computeIfAbsent(point.deviceId(), ignored -> new HashMap<>())
                        .put(point.payloadField(), new LoraMappingRule(
                                point.payloadField(), point.pointName(), point.dataPointTypeCode(),
                                point.unit(), point.scale(), point.valueMap()));
            }
            overridesByDeviceId.set(Map.copyOf(overrides));

            log.info("[LORA_CONFIG_CACHE_REFRESH] endpoints={} models={} overrideDevices={}",
                    endpoints.size(), modelMappings.size(), overrides.size());
        } catch (Exception exception) {
            log.warn("[LORA_CONFIG_CACHE_REFRESH_ERROR] exception={} message={}",
                    exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    public Optional<LoraResolvedDevice> resolveDevice(LoraIdType idType, String externalId) {
        String normalized = LoraExternalIdNormalizer.normalize(externalId);
        LoraResolvedDevice cached = endpointsByKey.get().get(key(idType, normalized));
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<LoraDeviceLookupResponse> fallback = managerLoraConfigClient.resolve(idType, externalId);
        return fallback.map(response -> new LoraResolvedDevice(
                response.deviceId(), response.deviceName(), response.deviceModelId(), response.deviceModelName()));
    }

    /** override 우선, 없으면 model 기본 매핑. 둘 다 없으면 empty (호출 측에서 미매핑 오류로 처리). */
    public Optional<LoraMappingRule> resolveMapping(Integer deviceId, Integer deviceModelId, String payloadField) {
        Map<String, LoraMappingRule> deviceOverrides = overridesByDeviceId.get().get(deviceId);
        if (deviceOverrides != null && deviceOverrides.containsKey(payloadField)) {
            return Optional.of(deviceOverrides.get(payloadField));
        }
        Map<String, LoraMappingRule> modelMappings = modelMappingsByModelId.get().get(deviceModelId);
        if (modelMappings != null && modelMappings.containsKey(payloadField)) {
            return Optional.of(modelMappings.get(payloadField));
        }
        return Optional.empty();
    }

    /** device의 override 매핑이 정의한 payload_field 전체 (model 기본 매핑과 합쳐 순회할 때 사용) */
    public java.util.Set<String> overrideFieldsOf(Integer deviceId) {
        Map<String, LoraMappingRule> deviceOverrides = overridesByDeviceId.get().get(deviceId);
        return deviceOverrides == null ? java.util.Set.of() : deviceOverrides.keySet();
    }

    public java.util.Set<String> modelFieldsOf(Integer deviceModelId) {
        Map<String, LoraMappingRule> modelMappings = modelMappingsByModelId.get().get(deviceModelId);
        return modelMappings == null ? java.util.Set.of() : modelMappings.keySet();
    }

    private static String key(LoraIdType idType, String normalizedExternalId) {
        return idType + "|" + normalizedExternalId;
    }
}
