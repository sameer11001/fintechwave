package com.fintechwave.iam.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record KeycloakUserEventRequest(
        @JsonProperty("type") String eventType,
        @NotBlank String userId,
        Map<String, String> details) {

    public String getEmail() {
        return details != null ? details.get("email") : null;
    }
}
