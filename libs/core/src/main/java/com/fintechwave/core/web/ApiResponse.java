package com.fintechwave.core.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;

    private String errorCode;

    private String message;

    private T data;

    private Map<String, String> validationErrors;

    private String traceId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    // ── Success factories ────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .traceId(MDC.get("requestId"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .traceId(MDC.get("requestId"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> noContent(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .traceId(MDC.get("requestId"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    // ── Error factories ──────────────────────────────────────────────────────

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .traceId(MDC.get("requestId"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> validationError(Map<String, String> validationErrors) {
        return ApiResponse.<T>builder()
                .success(false)
                .errorCode("VALIDATION_FAILED")
                .message("Request validation failed")
                .validationErrors(validationErrors)
                .traceId(MDC.get("requestId"))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
