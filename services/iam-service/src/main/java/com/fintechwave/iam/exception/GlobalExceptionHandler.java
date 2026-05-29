package com.fintechwave.iam.exception;

import com.fintechwave.core.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
                        MethodArgumentNotValidException ex) {

                Map<String, String> errors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .collect(Collectors.toMap(
                                                FieldError::getField,
                                                fe -> Objects.requireNonNullElse(fe.getDefaultMessage(),
                                                                "Invalid value")));

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.validationError(errors));
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
                log.warn("Resource not found: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
        }

        @ExceptionHandler(DuplicateResourceException.class)
        public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
                log.warn("Duplicate resource: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
        }

        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
                log.warn("Authentication failure: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
        }

        @ExceptionHandler(InvalidTokenException.class)
        public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
                log.warn("Token validation failure: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(
                        HttpMessageNotReadableException ex) {

                log.warn("Malformed JSON request: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("MALFORMED_JSON", ex.getMessage()));
        }

        @ExceptionHandler({
                        MethodArgumentTypeMismatchException.class,
                        MissingServletRequestParameterException.class,
                        MissingRequestHeaderException.class
        })
        public ResponseEntity<ApiResponse<Object>> handleMissingOrTypeMismatch(
                        Exception ex) {

                log.warn("Bad request parameter: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
                log.error("Unhandled exception: ", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
}
