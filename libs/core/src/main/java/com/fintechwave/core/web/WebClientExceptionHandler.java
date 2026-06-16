package com.fintechwave.core.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
@ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClientResponseException")
@Slf4j
public class WebClientExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDownstreamError(WebClientResponseException ex) {
        log.error("Downstream service error: Status={}, Body={}", ex.getStatusCode(),
                ex.getResponseBodyAsString());

        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Token expired during downstream processing."));
        }

        if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("ACCESS_DENIED", "Insufficient permissions for downstream resource."));
        }

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("BAD_GATEWAY", "An upstream service failed to process the request"));
    }
}
