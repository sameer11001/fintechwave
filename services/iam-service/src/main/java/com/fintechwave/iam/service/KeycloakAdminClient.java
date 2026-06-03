package com.fintechwave.iam.service;

import com.fintechwave.iam.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminClient {

    private final KeycloakProperties keycloakProperties;
    private final RestClient restClient = RestClient.create();

    public String fetchEmailByUserId(String userId) {
        try {
            String token = getServiceAccountToken();
            if (token == null)
                return null;

            Map<?, ?> userInfo = restClient.get()
                    .uri(keycloakProperties.getAdmin().userUrl(userId))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);

            if (userInfo != null && userInfo.containsKey("email")) {
                return (String) userInfo.get("email");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch email from Keycloak Admin API for userId={}: {}", userId, e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getServiceAccountToken() {
        try {
            KeycloakProperties.Admin admin = keycloakProperties.getAdmin();

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", admin.getClientId());
            form.add("client_secret", admin.getClientSecret());

            Map<String, Object> response = restClient.post()
                    .uri(admin.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            return response != null ? (String) response.get("access_token") : null;
        } catch (Exception e) {
            log.warn("Failed to obtain Keycloak service-account token: {}", e.getMessage());
            return null;
        }
    }
}
