package com.fintechwave.iam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakProperties {

    private String issuerUri;
    private String jwkSetUri;
    private String expectedAudience;
    private Admin admin = new Admin();

    @Data
    public static class Admin {
        private String baseUrl;
        private String realm;
        private String clientId;
        private String clientSecret;

        public String tokenUrl() {
            return baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        }

        public String userUrl(String userId) {
            return baseUrl + "/admin/realms/" + realm + "/users/" + userId;
        }
    }
}
