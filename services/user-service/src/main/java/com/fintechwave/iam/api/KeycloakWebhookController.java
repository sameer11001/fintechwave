package com.fintechwave.iam.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.iam.dto.request.KeycloakUserEventRequest;
import com.fintechwave.iam.service.IUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/webhook/keycloak")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Keycloak Webhook", description = "Internal — receives Keycloak user lifecycle events")
public class KeycloakWebhookController {

    private final IUserProfileService userProfileService;

    @PostMapping("/user-registered")
    @Operation(summary = "Create user profile on Keycloak registration")
    public ResponseEntity<ApiResponse<Void>> onUserRegistered(
            @RequestBody @Valid KeycloakUserEventRequest request) {

        log.info("Keycloak webhook received: event={} keycloakId={}", request.eventType(), request.userId());
        userProfileService.createProfileFromKeycloak(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
