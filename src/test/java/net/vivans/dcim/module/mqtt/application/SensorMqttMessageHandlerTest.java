package net.vivans.dcim.module.mqtt.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.vivans.dcim.module.influx.application.InfluxWriteService;
import net.vivans.dcim.module.manager.infrastructure.ManagerDeviceClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SensorMqttMessageHandlerTest {

    private final ManagerDeviceClient manager = mock(ManagerDeviceClient.class);
    private final InfluxWriteService influx = mock(InfluxWriteService.class);
    private final SensorMqttMessageHandler handler = new SensorMqttMessageHandler(new ObjectMapper(), manager, influx);

    @Test
    void forwardsModbusProtocolToExistingInfluxWriter() {
        when(manager.findDevice(101)).thenReturn(Optional.empty());

        handler.handle("dcim/sensor/data", bytes("""
                {"type":"schedule","protocol":"modbus","data":{"101":{"POWER":1250.5}}}
                """));

        verify(influx).writeSensorPoints(eq(101), isNull(), eq(Map.of("POWER", 1250.5)),
                any(Instant.class), isNull(), eq("modbus"));
    }

    @Test
    void legacyMessageStillUsesSnmp() {
        when(manager.findDevice(9)).thenReturn(Optional.empty());

        handler.handle("dcim/sensor/data", bytes("""
                {"type":"schedule","data":{"9":{"V":219}}}
                """));

        verify(influx).writeSensorPoints(eq(9), isNull(), eq(Map.of("V", 219)),
                any(Instant.class), isNull(), eq("snmp"));
    }

    @Test
    void unsupportedProtocolDoesNotWriteOrLookupDevice() {
        handler.handle("dcim/sensor/data", bytes("""
                {"type":"schedule","protocol":"unknown","data":{"9":{"V":219}}}
                """));

        verifyNoInteractions(manager, influx);
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
