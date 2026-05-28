package com.fintechwave.iam.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(

        @NotBlank(message = "refresh_token is required")
        String refreshToken
) {}
