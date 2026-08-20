package net.vivans.dcim.module.health.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.vivans.dcim.module.health.api.dto.HealthResponse;
import net.vivans.dcim.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Health")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "서비스 헬스 체크")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP", "new-sensor-data-server"));
    }
}
