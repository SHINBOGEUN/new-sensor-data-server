package net.vivans.dcim.module.lora.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.vivans.dcim.module.influx.application.InfluxWriteService;
import net.vivans.dcim.module.lora.domain.LoraIdType;
import net.vivans.dcim.module.lora.infrastructure.ManagerLoraErrorLogClient;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraIngestErrorLogRequest;
import net.vivans.dcim.module.manager.infrastructure.ManagerDeviceClient;
import net.vivans.dcim.module.mqtt.config.LoraMqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 장비 식별 우선순위(요청 기준):
 * 1) 설정된 devEUI 경로에서 값 추출 성공 시 devEUI로 매칭
 * 2) devEUI가 payload에 없으면 deviceInfo.deviceName으로 매칭 (레거시 운영 호환)
 * 3) 둘 다 없거나 매칭(등록 조회)에 실패하면 오류 로그만 남기고 종료 — devEUI 매칭이
 *    실패했다고 deviceName으로 다시 시도하지는 않는다(레지스트리 조회 실패와 "값 없음"은 다르게 취급).
 */
class LoraMqttMessageHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LoraMqttProperties properties;
    private LoraConfigCache configCache;
    private LoraValueConverter valueConverter;
    private ManagerDeviceClient managerDeviceClient;
    private ManagerLoraErrorLogClient errorLogClient;
    private InfluxWriteService influxWriteService;
    private LoraMqttMessageHandler handler;

    @BeforeEach
    void setUp() {
        properties = new LoraMqttProperties();
        properties.setDevEuiPath("deviceInfo.devEui");
        properties.setDeviceNamePath("deviceInfo.deviceName");
        properties.setErrorRawPayloadMaxLength(2000);

        configCache = mock(LoraConfigCache.class);
        valueConverter = new LoraValueConverter(objectMapper);
        managerDeviceClient = mock(ManagerDeviceClient.class);
        errorLogClient = mock(ManagerLoraErrorLogClient.class);
        influxWriteService = mock(InfluxWriteService.class);

        handler = new LoraMqttMessageHandler(
                objectMapper, properties, configCache, valueConverter,
                managerDeviceClient, errorLogClient, influxWriteService);
    }

    private LoraResolvedDevice device(int id, int modelId) {
        return new LoraResolvedDevice(id, "dev-" + id, modelId, "model-" + modelId);
    }

    @Test
    void devEuiPresent_matchesByDevEui() {
        byte[] payload = ("{\"deviceInfo\":{\"devEui\":\"24E124710C123456\",\"deviceName\":\"unused-name\"},"
                + "\"object\":{\"TempC_SHT\":23.5}}").getBytes(StandardCharsets.UTF_8);

        LoraResolvedDevice resolved = device(9, 10);
        when(configCache.resolveDevice(eq(LoraIdType.DEV_EUI), eq("24E124710C123456")))
                .thenReturn(Optional.of(resolved));
        when(configCache.overrideFieldsOf(9)).thenReturn(Set.of());
        when(configCache.modelFieldsOf(10)).thenReturn(Set.of("object.TempC_SHT"));
        when(configCache.resolveMapping(9, 10, "object.TempC_SHT"))
                .thenReturn(Optional.of(new LoraMappingRule("object.TempC_SHT", "TEMPERATURE", "TEMPERATURE", "C", null, null)));
        when(managerDeviceClient.findDevice(9)).thenReturn(Optional.empty());

        handler.handle("application/dev1/event/up", payload);

        verify(configCache).resolveDevice(LoraIdType.DEV_EUI, "24E124710C123456");
        verify(configCache, never()).resolveDevice(eq(LoraIdType.DEVICE_NAME), any());
        verify(errorLogClient, never()).record(any());

        ArgumentCaptor<Map<String, Object>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(influxWriteService).writeSensorPoints(eq(9), any(), valuesCaptor.capture(), any(), isNull(), eq("mqtt"));
        assertThat(valuesCaptor.getValue()).containsEntry("TEMPERATURE", 23.5);
    }

    @Test
    void devEuiAbsent_fallsBackToDeviceName() {
        byte[] payload = ("{\"deviceInfo\":{\"deviceName\":\"dragino-lht65n-01\"},"
                + "\"object\":{\"TempC_SHT\":23.5}}").getBytes(StandardCharsets.UTF_8);

        LoraResolvedDevice resolved = device(11, 12);
        when(configCache.resolveDevice(eq(LoraIdType.DEVICE_NAME), eq("dragino-lht65n-01")))
                .thenReturn(Optional.of(resolved));
        when(configCache.overrideFieldsOf(11)).thenReturn(Set.of());
        when(configCache.modelFieldsOf(12)).thenReturn(Set.of("object.TempC_SHT"));
        when(configCache.resolveMapping(11, 12, "object.TempC_SHT"))
                .thenReturn(Optional.of(new LoraMappingRule("object.TempC_SHT", "TEMPERATURE", "TEMPERATURE", "C", null, null)));
        when(managerDeviceClient.findDevice(11)).thenReturn(Optional.empty());

        handler.handle("application/dev2/event/up", payload);

        verify(configCache, never()).resolveDevice(eq(LoraIdType.DEV_EUI), any());
        verify(configCache).resolveDevice(LoraIdType.DEVICE_NAME, "dragino-lht65n-01");
        verify(errorLogClient, never()).record(any());
        verify(influxWriteService).writeSensorPoints(eq(11), any(), any(), any(), isNull(), eq("mqtt"));
    }

    @Test
    void devEuiBlank_treatedAsAbsentAndFallsBackToDeviceName() {
        byte[] payload = ("{\"deviceInfo\":{\"devEui\":\"\",\"deviceName\":\"dragino-lht65n-02\"},"
                + "\"object\":{\"TempC_SHT\":23.5}}").getBytes(StandardCharsets.UTF_8);

        LoraResolvedDevice resolved = device(13, 14);
        when(configCache.resolveDevice(eq(LoraIdType.DEVICE_NAME), eq("dragino-lht65n-02")))
                .thenReturn(Optional.of(resolved));
        when(configCache.overrideFieldsOf(13)).thenReturn(Set.of());
        when(configCache.modelFieldsOf(14)).thenReturn(Set.of());

        handler.handle("application/dev3/event/up", payload);

        verify(configCache).resolveDevice(LoraIdType.DEVICE_NAME, "dragino-lht65n-02");
        verify(configCache, never()).resolveDevice(eq(LoraIdType.DEV_EUI), any());
    }

    @Test
    void neitherDevEuiNorDeviceNamePresent_recordsErrorWithoutCallingConfigCache() {
        byte[] payload = "{\"deviceInfo\":{},\"object\":{\"TempC_SHT\":23.5}}".getBytes(StandardCharsets.UTF_8);

        handler.handle("application/unknown/event/up", payload);

        verify(configCache, never()).resolveDevice(any(), any());
        ArgumentCaptor<LoraIngestErrorLogRequest> captor = ArgumentCaptor.forClass(LoraIngestErrorLogRequest.class);
        verify(errorLogClient).record(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("NO_IDENTIFIER_IN_PAYLOAD");
        assertThat(captor.getValue().idType()).isNull();
        assertThat(captor.getValue().externalId()).isNull();
        verify(influxWriteService, never()).writeSensorPoints(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void devEuiPresentButUnregistered_ignoresWithoutErrorAndDoesNotFallBackToDeviceName() {
        byte[] payload = ("{\"deviceInfo\":{\"devEui\":\"24E124710C123456\",\"deviceName\":\"dragino-lht65n-03\"},"
                + "\"object\":{\"TempC_SHT\":23.5}}").getBytes(StandardCharsets.UTF_8);

        when(configCache.resolveDevice(eq(LoraIdType.DEV_EUI), eq("24E124710C123456")))
                .thenReturn(Optional.empty());

        handler.handle("application/dev4/event/up", payload);

        verify(configCache).resolveDevice(LoraIdType.DEV_EUI, "24E124710C123456");
        verify(configCache, never()).resolveDevice(eq(LoraIdType.DEVICE_NAME), any());

        verify(errorLogClient, never()).record(any());
        verify(influxWriteService, never()).writeSensorPoints(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void noMappingConfigured_recordsErrorWithoutInfluxWrite() {
        byte[] payload = "{\"deviceInfo\":{\"devEui\":\"AABBCCDDEEFF0011\"}}".getBytes(StandardCharsets.UTF_8);

        LoraResolvedDevice resolved = device(20, 21);
        when(configCache.resolveDevice(eq(LoraIdType.DEV_EUI), eq("AABBCCDDEEFF0011")))
                .thenReturn(Optional.of(resolved));
        when(configCache.overrideFieldsOf(20)).thenReturn(Set.of());
        when(configCache.modelFieldsOf(21)).thenReturn(Set.of());

        handler.handle("application/dev5/event/up", payload);

        ArgumentCaptor<LoraIngestErrorLogRequest> captor = ArgumentCaptor.forClass(LoraIngestErrorLogRequest.class);
        verify(errorLogClient).record(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("NO_MAPPING_CONFIGURED");
        verify(influxWriteService, never()).writeSensorPoints(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void sentinelValueIsSkippedSilentlyNotTreatedAsError() {
        byte[] payload = ("{\"deviceInfo\":{\"devEui\":\"AABBCCDDEEFF0022\"},"
                + "\"object\":{\"TempC_SHT\":327.67}}").getBytes(StandardCharsets.UTF_8);

        LoraResolvedDevice resolved = device(30, 31);
        when(configCache.resolveDevice(eq(LoraIdType.DEV_EUI), eq("AABBCCDDEEFF0022")))
                .thenReturn(Optional.of(resolved));
        when(configCache.overrideFieldsOf(30)).thenReturn(Set.of());
        when(configCache.modelFieldsOf(31)).thenReturn(Set.of("object.TempC_SHT"));
        when(configCache.resolveMapping(30, 31, "object.TempC_SHT"))
                .thenReturn(Optional.of(new LoraMappingRule("object.TempC_SHT", "TEMPERATURE", "TEMPERATURE", "C", null, null)));

        handler.handle("application/dev6/event/up", payload);

        // sentinel(no-data)는 오류 로그 대상이 아니다. 다만 유효 포인트가 하나도 없으므로 Influx 기록도 없다.
        verify(errorLogClient, never()).record(any());
        verify(influxWriteService, never()).writeSensorPoints(anyInt(), any(), any(), any(), any(), any());
    }
}
