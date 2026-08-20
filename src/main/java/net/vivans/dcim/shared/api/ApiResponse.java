package net.vivans.dcim.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int status;
    private String message;
    private String error;
    private Instant timestamp;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
}
