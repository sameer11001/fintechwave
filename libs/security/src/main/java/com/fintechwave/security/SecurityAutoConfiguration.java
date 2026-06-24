package com.fintechwave.security;

import com.fintechwave.security.config.KeycloakProperties;
import com.fintechwave.security.converter.KeycloakJwtAuthenticationConverter;
import com.fintechwave.security.exception.KeycloakAuthenticationEntryPoint;
import com.fintechwave.security.validator.AudienceValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(KeycloakProperties.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(KeycloakProperties keycloakProperties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(keycloakProperties.getJwkSetUri())
                .build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(keycloakProperties.getIssuerUri());

        if (StringUtils.hasText(keycloakProperties.getExpectedAudience())) {
            OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
                    withIssuer,
                    new AudienceValidator(keycloakProperties.getExpectedAudience()));
            jwtDecoder.setJwtValidator(withAudience);
        } else {
            jwtDecoder.setJwtValidator(withIssuer);
        }

        return jwtDecoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakAuthenticationEntryPoint keycloakAuthenticationEntryPoint(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new KeycloakAuthenticationEntryPoint(objectMapper);
    }
}
