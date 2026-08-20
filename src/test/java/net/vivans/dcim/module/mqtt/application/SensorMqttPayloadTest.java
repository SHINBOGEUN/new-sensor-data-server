package net.vivans.dcim.module.mqtt.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.vivans.dcim.module.mqtt.domain.SensorMqttPayload;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensorMqttPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesLegacySchedulePayload() throws Exception {
        SensorMqttPayload payload = objectMapper.readValue("""
                {
                  "datetime": "2026-08-20 10:51:00",
                  "data": {
                    "9": {
                      "V": 219,
                      "W": 519
                    }
                  },
                  "type": "schedule"
                }
                """, SensorMqttPayload.class);

        assertThat(payload.type()).isEqualTo("schedule");
        assertThat(payload.data()).containsKey("9");
        assertThat(payload.data().get("9")).containsEntry("V", 219);
    }
}
