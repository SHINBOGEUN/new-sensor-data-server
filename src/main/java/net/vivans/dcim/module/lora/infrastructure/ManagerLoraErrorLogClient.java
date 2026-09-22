package net.vivans.dcim.module.lora.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.vivans.dcim.module.lora.infrastructure.dto.LoraIngestErrorLogRequest;
import net.vivans.dcim.module.manager.infrastructure.ManagerServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sensor Data는 자체 RDB가 없어 Manager API로 LoRa 수집 오류 이력을 적재한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerLoraErrorLogClient {

    private final RestClient managerRestClient;
    private final ManagerServiceProperties properties;

    public void record(LoraIngestErrorLogRequest request) {
        if (!properties.isEnabled()) {
            log.debug("[LORA_ERROR_LOG_SKIP] reason=CLIENT_DISABLED requestReason={}", request.reason());
            return;
        }
        try {
            managerRestClient.post()
                    .uri("/api/manager/lora/error-logs")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.warn("[LORA_ERROR_LOG_WRITE_FAILED] reason={} exception={} message={}",
                    request.reason(), exception.getClass().getSimpleName(), exception.getMessage());
        }
    }
}
