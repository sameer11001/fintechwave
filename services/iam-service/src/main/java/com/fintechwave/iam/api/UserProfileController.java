package com.fintechwave.iam.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.iam.dto.request.UpdateUserProfileRequest;
import com.fintechwave.iam.dto.response.UserProfileResponse;
import com.fintechwave.iam.service.IUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "User Profile", description = "User profile management")
public class UserProfileController {

    private final IUserProfileService userProfileService;

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {

        UUID keycloakId = UUID.fromString(jwt.getSubject());
        UserProfileResponse response = userProfileService.findByKeycloakId(keycloakId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateUserProfileRequest request) {

        UUID keycloakId = UUID.fromString(jwt.getSubject());
        UserProfileResponse response = userProfileService.updateProfile(keycloakId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user profile by ID (Admin only)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable UUID userId) {

        UserProfileResponse response = userProfileService.findById(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
