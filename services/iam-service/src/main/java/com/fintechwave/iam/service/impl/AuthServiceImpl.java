package com.fintechwave.iam.service.impl;

import com.fintechwave.iam.domain.entity.RefreshToken;
import com.fintechwave.iam.domain.entity.User;
import com.fintechwave.iam.dto.request.LoginRequest;
import com.fintechwave.iam.dto.request.RegisterRequest;
import com.fintechwave.iam.dto.response.AuthResponse;
import com.fintechwave.iam.exception.DuplicateResourceException;
import com.fintechwave.iam.exception.InvalidCredentialsException;
import com.fintechwave.iam.mapper.AuthMapper;
import com.fintechwave.iam.repository.UserRepository;
import com.fintechwave.security.jwt.JwtProvider;
import com.fintechwave.iam.service.IAuthService;
import com.fintechwave.iam.service.ITokenService;
import com.fintechwave.iam.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthServiceImpl implements IAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ITokenService tokenService;
    private final AuthMapper authMapper;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhoneHash(HashUtil.sha256(request.phone().trim()));
        }

        User saved = userRepository.save(user);
        log.info("User registered: id={}", saved.getId());

        return buildResponse(saved);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getStatus().isActive()) {
            throw new InvalidCredentialsException("Account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User logged in: id={}", user.getId());
        return buildResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String incomingRefreshToken) {
        RefreshToken rotated = tokenService.rotate(incomingRefreshToken);
        String accessToken = jwtProvider.generateAccessToken(rotated.getUser().getId());
        log.info("Token rotated for userId={}", rotated.getUser().getId());
        return authMapper.toAuthResponse(accessToken, rotated);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
        log.info("Refresh token revoked");
    }

    private AuthResponse buildResponse(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        RefreshToken refresh = tokenService.issue(user);
        return authMapper.toAuthResponse(accessToken, refresh);
    }
}
