package com.fintechwave.iam.mapper;

import com.fintechwave.iam.domain.entity.RefreshToken;
import com.fintechwave.iam.dto.response.AuthResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "refreshToken", source = "refreshToken.token")
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "expiresIn", expression = "java(900L)")
    AuthResponse toAuthResponse(String accessToken, RefreshToken refreshToken);

    @Mapping(target = "refreshToken", source = "rawRefreshToken")
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "expiresIn", expression = "java(900L)")
    AuthResponse toAuthResponse(String accessToken, String rawRefreshToken);
}
