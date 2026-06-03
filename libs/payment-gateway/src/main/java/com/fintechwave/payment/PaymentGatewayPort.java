package com.fintechwave.payment;

public interface PaymentGatewayPort {

    /*
     * Creates a Payment Intent for card cash-in.
     */
    CardPaymentIntent createCardPaymentIntent(Money amount, String stripeCustomerId);

    /**
     * Initiates an instant payout to the user's original card.
     */
    PayoutResult initiateInstantPayout(String stripePaymentMethodId, Money amount);

    /**
     * Parses and cryptographically validates an incoming provider webhook.
     * Throws {@link BaseServiceException} if signature is invalid.
     */
    WebhookEvent parseAndValidateWebhook(String payload, String signature);
}
