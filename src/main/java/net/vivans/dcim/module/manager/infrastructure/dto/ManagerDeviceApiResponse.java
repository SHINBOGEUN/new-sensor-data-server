package net.vivans.dcim.module.manager.infrastructure.dto;

import net.vivans.dcim.shared.api.ApiResponse;

public record ManagerDeviceApiResponse(
        int status,
        ManagerDeviceResponse data
) {
}
