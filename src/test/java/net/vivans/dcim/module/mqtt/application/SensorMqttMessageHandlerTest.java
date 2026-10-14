package net.vivans.dcim.module.mqtt.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.vivans.dcim.module.manager.infrastructure.ManagerDeviceClient;
import net.vivans.dcim.module.influx.application.InfluxWriteService;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class SensorMqttMessageHandlerTest {
    private final ManagerDeviceClient devices = mock(ManagerDeviceClient.class);
    private final InfluxWriteService writer = mock(InfluxWriteService.class);
    private final AgentMqttMessageHandler agents = mock(AgentMqttMessageHandler.class);
    private final net.vivans.dcim.module.lora.application.LoraMqttMessageHandler lora = mock(net.vivans.dcim.module.lora.application.LoraMqttMessageHandler.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SensorMqttMessageHandler handler = new SensorMqttMessageHandler(mapper, devices, writer, agents, lora);

    @Test
    void existingCollectorFormatStillUsesLegacyWriter() {
        when(devices.findDevice(40)).thenReturn(Optional.empty());
        handler.handle("dcim/sensor/data", bytes("{\"datetime\":\"2026-10-14 17:11:13\",\"data\":{\"40\":{\"TEMP\":59}}}"));
        verify(writer).writeSensorPoints(eq(40), isNull(), eq(Map.of("TEMP", 59)), any());
        verifyNoInteractions(agents);
    }

    @Test
    void confirmedPayloadIsRoutedToCommonHandlerAndMalformedPayloadIsIsolated() throws Exception {
        String json = "{\"time\":\"2026-10-14T08:11:13Z\",\"deviceInfo\":{\"identityType\":\"MAC\",\"externalId\":\"aabbccddeeff\"},\"object\":{}}";
        handler.handle("dcim/agent/server-01/telemetry", bytes(json));
        verify(agents).handle(mapper.readTree(json));
        handler.handle("dcim/agent/server-01/telemetry", bytes("invalid-json"));
        verifyNoInteractions(writer);
    }

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    @Test
    void draginoPayloadUsesOriginalLoraHandlerInsteadOfAgentWriter() {
        var payload = bytes("{\"deviceInfo\":{\"devEui\":\"a8404124875d9d36\"},\"object\":{\"TempC_DS\":327.67}}");
        handler.handle("application/1/device/1/event/up", payload);
        verify(lora).handle("application/1/device/1/event/up", payload);
        verifyNoInteractions(agents, writer);
    }
}
