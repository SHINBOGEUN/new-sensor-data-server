package net.vivans.dcim.module.lora.api;

import lombok.RequiredArgsConstructor;
import net.vivans.dcim.module.mqtt.infrastructure.LoraMqttSubscriber;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manager가 LoRa MQTT source CRUD 직후 보내는 즉시 재조정 요청. API key 인증은 공통 SecurityConfig가 적용한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/lora/sources")
public class LoraMqttSourceSyncController {
    private final LoraMqttSubscriber loraMqttSubscriber;

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh() {
        loraMqttSubscriber.requestRefresh();
        return ResponseEntity.noContent().build();
    }
}
