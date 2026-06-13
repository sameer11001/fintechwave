package com.fintechwave.fraud.exception;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.core.web.GlobalExceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class FraudExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedisFailure(RedisConnectionFailureException ex) {
        log.error("Redis connection failure in fraud-service — velocity checks unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("REDIS_UNAVAILABLE",
                        "Fraud evaluation is temporarily unavailable. Please try again later."));
    }
}
