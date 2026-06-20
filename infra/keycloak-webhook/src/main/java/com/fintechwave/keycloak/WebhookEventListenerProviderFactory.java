package com.fintechwave.keycloak;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class WebhookEventListenerProviderFactory implements EventListenerProviderFactory {

    private String webhookUrl;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new WebhookEventListenerProvider(webhookUrl);
    }

    @Override
    public void init(Config.Scope config) {
        // Read from KC_SPI_EVENTSLISTENER_WEBHOOK_URL
        this.webhookUrl = config.get("url", System.getenv("KC_SPI_EVENTSLISTENER_WEBHOOK_URL"));
        if (this.webhookUrl == null) {
            this.webhookUrl = "http://host.docker.internal:8081/api/v1/internal/webhook/keycloak/user-registered";
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "webhook";
    }
}
