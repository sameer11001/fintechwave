package com.fintechwave.iam.service;

import com.fintechwave.iam.dto.request.LoginRequest;
import com.fintechwave.iam.dto.request.RegisterRequest;
import com.fintechwave.iam.dto.response.AuthResponse;

public interface IAuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);

    void logout(String refreshToken);
}
