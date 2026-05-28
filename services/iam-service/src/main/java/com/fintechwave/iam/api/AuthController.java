package com.fintechwave.iam.api;

import com.fintechwave.iam.dto.request.LoginRequest;
import com.fintechwave.iam.dto.request.LogoutRequest;
import com.fintechwave.iam.dto.request.RefreshTokenRequest;
import com.fintechwave.iam.dto.request.RegisterRequest;
import com.fintechwave.iam.dto.response.ApiResponse;
import com.fintechwave.iam.dto.response.AuthResponse;
import com.fintechwave.iam.service.IAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "Authentication", description = "User registration, login, token refresh and logout")
public class AuthController {

    private final IAuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @RequestBody @Valid RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and receive new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody @Valid RefreshTokenRequest request) {

        AuthResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the provided refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody @Valid LogoutRequest request) {

        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
