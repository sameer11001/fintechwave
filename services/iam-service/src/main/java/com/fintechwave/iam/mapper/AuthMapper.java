package com.fintechwave.iam.mapper;

import com.fintechwave.iam.domain.entity.RefreshToken;
import com.fintechwave.iam.dto.response.AuthResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public AuthResponse toAuthResponse(String accessToken, RefreshToken refreshToken) {
        return AuthResponse.of(accessToken, refreshToken.getToken());
    }

    public AuthResponse toAuthResponse(String accessToken, String rawRefreshToken) {
        return AuthResponse.of(accessToken, rawRefreshToken);
    }
}
