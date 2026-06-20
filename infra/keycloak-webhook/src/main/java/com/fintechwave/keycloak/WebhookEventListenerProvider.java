package com.fintechwave.keycloak;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WebhookEventListenerProvider implements EventListenerProvider {

    private final String webhookUrl;
    private final HttpClient httpClient;

    public WebhookEventListenerProvider(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void onEvent(Event event) {
        if (EventType.REGISTER.equals(event.getType())) {
            sendWebhook(event);
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Ignore admin events for now
    }

    private void sendWebhook(Event event) {
        try {
            String email = event.getDetails() != null ? event.getDetails().get("email") : "";
            
            // Build the JSON payload matching the IAM service expectation
            String jsonPayload = String.format(
                "{\"type\":\"%s\",\"userId\":\"%s\",\"details\":{\"email\":\"%s\"}}",
                event.getType().toString(),
                event.getUserId(),
                email
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            System.err.println("Webhook failed: " + response.statusCode() + " " + response.body());
                        } else {
                            System.out.println("Webhook sent successfully for user " + event.getUserId());
                        }
                    })
                    .exceptionally(ex -> {
                        System.err.println("Webhook error: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("Failed to build/send webhook: " + e.getMessage());
        }
    }

    @Override
    public void close() {
    }
}
