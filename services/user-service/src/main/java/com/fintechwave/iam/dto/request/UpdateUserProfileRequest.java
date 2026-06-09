package com.fintechwave.iam.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
                @Size(min = 1, max = 100) String firstName,
                @Size(min = 1, max = 100) String lastName,
                @Size(min = 7, max = 20) String phone) {
}