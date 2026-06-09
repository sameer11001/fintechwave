package com.fintechwave.transaction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.stripe")
@Data
public class StripeProperties {
    /**
     * Stripe secret API key (sk_test_xxx or sk_live_xxx).
     * Never logged — injected from environment variable STRIPE_API_KEY.
     */
    private String apiKey;

    /**
     * Stripe webhook signing secret (whsec_xxx).
     * Used to validate the Stripe-Signature header on incoming webhooks.
     * Never logged — injected from environment variable STRIPE_WEBHOOK_SECRET.
     */
    private String webhookSecret;
}
