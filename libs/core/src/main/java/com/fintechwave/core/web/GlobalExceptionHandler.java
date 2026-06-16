package com.fintechwave.core.web;

import com.fintechwave.core.exception.BaseServiceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(BaseServiceException.class)
        public ResponseEntity<ApiResponse<Void>> handleDomainException(BaseServiceException ex) {
                log.warn("Domain exception [{}] status={}: {}", ex.getErrorCode(), ex.getStatus(), ex.getMessage());
                return ResponseEntity.status(ex.getStatus())
                                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
                        MethodArgumentNotValidException ex) {

                Map<String, String> errors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .collect(Collectors.toMap(
                                                FieldError::getField,
                                                fe -> Objects.requireNonNullElse(fe.getDefaultMessage(),
                                                                "Invalid value"),
                                                (first, second) -> first // keep first error per field
                                ));

                return ResponseEntity.badRequest()
                                .body(ApiResponse.validationError(errors));
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
                        ConstraintViolationException ex) {

                Map<String, String> errors = ex.getConstraintViolations()
                                .stream()
                                .collect(Collectors.toMap(
                                                cv -> extractLeafPath(cv),
                                                ConstraintViolation::getMessage,
                                                (first, second) -> first));

                log.warn("Constraint violations: {}", errors);
                return ResponseEntity.badRequest()
                                .body(ApiResponse.validationError(errors));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Void>> handleBadBody(HttpMessageNotReadableException ex) {
                log.warn("Malformed JSON request body: {}", ex.getMessage());
                return ResponseEntity.badRequest()
                                .body(ApiResponse.error("MALFORMED_JSON", "Malformed or unreadable request body"));
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
                String msg = "Parameter '%s' must be of type %s".formatted(
                                ex.getName(),
                                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
                log.warn("Type mismatch: {}", msg);
                return ResponseEntity.badRequest()
                                .body(ApiResponse.error("TYPE_MISMATCH", msg));
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
                log.warn("Missing required parameter: {}", ex.getParameterName());
                return ResponseEntity.badRequest()
                                .body(ApiResponse.error("MISSING_PARAMETER",
                                                "Missing required parameter: " + ex.getParameterName()));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
                log.warn("Illegal argument: {}", ex.getMessage());
                return ResponseEntity.badRequest()
                                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage()));
        }

        @ExceptionHandler(MissingRequestHeaderException.class)
        public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
                log.warn("Missing required header: {}", ex.getHeaderName());
                return ResponseEntity.badRequest()
                                .body(ApiResponse.error("MISSING_HEADER",
                                                "Missing required header: " + ex.getHeaderName()));
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleNoRoute(NoResourceFoundException ex) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error("ROUTE_NOT_FOUND", "The requested endpoint does not exist"));
        }

        // ── Security ──────────────────────────────────────────────────────────────

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
                log.warn("Access denied: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error("ACCESS_DENIED",
                                                "You do not have permission to perform this action"));
        }

        // ── Data integrity ────────────────────────────────────────────────────────

        /**
         * Handles database unique constraint violations that escape the service layer.
         */
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
                log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error("DATA_INTEGRITY_VIOLATION",
                                                "A duplicate or conflicting record already exists"));
        }

        @ExceptionHandler(OptimisticLockingFailureException.class)
        public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
                log.warn("Optimistic locking failure: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error("CONFLICT",
                                                "The record was updated by another transaction. Please refresh and try again."));
        }

        // ── Web / Protocol Exceptions ─────────────────────────────────────────────

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
                log.warn("Method not supported: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(ApiResponse.error("METHOD_NOT_ALLOWED", ex.getMessage()));
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
                log.warn("Media type not supported: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                                .body(ApiResponse.error("UNSUPPORTED_MEDIA_TYPE", ex.getMessage()));
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
                log.warn("File size exceeded: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                                .body(ApiResponse.error("PAYLOAD_TOO_LARGE",
                                                "The uploaded file exceeds the maximum allowed size."));
        }

        // ── Additional Security Exceptions ────────────────────────────────────────

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
                log.warn("Authentication failed: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error("UNAUTHORIZED",
                                                "Invalid or missing authentication credentials"));
        }

        @ExceptionHandler(JwtValidationException.class)
        public ResponseEntity<ApiResponse<Void>> handleJwtValidation(JwtValidationException ex) {
                log.warn("Keycloak JWT validation failed downstream: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error("TOKEN_EXPIRED_OR_INVALID",
                                                "The authentication token failed validation checks."));
        }

        // ── Catch-all ─────────────────────────────────────────────────────────────

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleAll(Exception ex) {
                log.error("Unhandled exception", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private String extractLeafPath(ConstraintViolation<?> cv) {
                return StreamSupport
                                .stream(cv.getPropertyPath().spliterator(), false)
                                .reduce((first, second) -> second)
                                .map(Path.Node::getName)
                                .orElse(cv.getPropertyPath().toString());
        }
}
