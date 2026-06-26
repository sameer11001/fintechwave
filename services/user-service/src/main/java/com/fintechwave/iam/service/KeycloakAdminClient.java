package com.fintechwave.iam.service;

import com.fintechwave.iam.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
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

    public void updateUserProfile(String userId, String firstName, String lastName, String phone) {
        try {
            String token = getServiceAccountToken();
            if (token == null) {
                log.error("Cannot update user {} in Keycloak: no service account token", userId);
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            if (firstName != null) updates.put("firstName", firstName);
            if (lastName != null) updates.put("lastName", lastName);

            if (phone != null) {
                Map<String, List<String>> attributes = new HashMap<>();
                attributes.put("phone", List.of(phone));
                updates.put("attributes", attributes);
            }

            restClient.put()
                    .uri(keycloakProperties.getAdmin().userUrl(userId))
                    .header("Authorization", "Bearer " + token)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(updates)
                    .retrieve()
                    .toBodilessEntity();
            
            log.info("Successfully updated user profile in Keycloak for userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to update user profile in Keycloak for userId={}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to update user profile in Keycloak", e);
        }
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
