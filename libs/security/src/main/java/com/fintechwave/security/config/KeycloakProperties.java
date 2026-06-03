package com.fintechwave.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.keycloak")
@Getter
@Setter
public class KeycloakProperties {

    private String jwkSetUri;

    private String issuerUri;

    private String expectedAudience;
}
