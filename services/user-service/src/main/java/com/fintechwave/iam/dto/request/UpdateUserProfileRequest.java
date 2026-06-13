package com.fintechwave.iam.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
                @Pattern(regexp = "^[^<>]*$", message = "First name contains invalid characters") @Size(min = 1, max = 100) String firstName,
                @Pattern(regexp = "^[^<>]*$", message = "Last name contains invalid characters") @Size(min = 1, max = 100) String lastName,
                @Pattern(regexp = "^[0-9+\\-() ]*$", message = "Invalid phone number format") @Size(min = 7, max = 20) String phone) {
}