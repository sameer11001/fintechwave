package com.fintechwave.iam.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import com.fintechwave.security.converter.KeycloakJwtAuthenticationConverter;
import com.fintechwave.security.exception.KeycloakAuthenticationEntryPoint;


@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtDecoder jwtDecoder;
        private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    private final KeycloakAuthenticationEntryPoint keycloakAuthenticationEntryPoint;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                return http
                                .csrf(AbstractHttpConfigurer::disable)
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/v1/internal/webhook/**").permitAll()
                                                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs")
                                                .permitAll()
                                                .anyRequest().authenticated())

                                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(keycloakAuthenticationEntryPoint)
                        .jwt(jwt -> jwt
                                                .decoder(jwtDecoder)
                                                .jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))

                                .build();
        }
}
