package com.fintechwave.transaction.adapter;

import com.fintechwave.payment.CardPaymentIntent;
import com.fintechwave.payment.Money;
import com.fintechwave.payment.PaymentGatewayPort;
import com.fintechwave.payment.PayoutResult;
import com.fintechwave.payment.WebhookEvent;
import com.fintechwave.transaction.config.StripeProperties;
import com.fintechwave.transaction.exception.PaymentGatewayException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Payout;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PayoutCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.Semaphore;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

@Component
@RequiredArgsConstructor
@Slf4j
public class StripeGatewayAdapter implements PaymentGatewayPort {

    private static final Semaphore STRIPE_SEMAPHORE = new Semaphore(50, true);
    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeProperties.getApiKey();
        log.info("Stripe adapter initialized");
    }

    /**
     * Creates a Stripe PaymentIntent for card cash-in.
     * The client must confirm this intent using Stripe.js on the frontend.
     *
     * @param amount                Amount + currency
     * @param stripePaymentMethodId Stripe Payment Method ID (pm_...)
     * @return CardPaymentIntent with client secret for frontend confirmation
     */
    @Override
    @Retryable(
        retryFor = { PaymentGatewayException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 500)
    )
    public CardPaymentIntent createCardPaymentIntent(Money amount, String stripePaymentMethodId) {
        try {
            STRIPE_SEMAPHORE.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Interrupted while waiting for Stripe rate limit permit", e);
        }
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amount.toMinorUnits()) // Stripe uses minor units (cents)
                    .setCurrency(amount.currencyCode().toLowerCase())
                    .setPaymentMethod(stripePaymentMethodId)
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                    // Automatically confirm if testing via API without a frontend
                    .setConfirm(true)
                    .setReturnUrl("https://localhost:8084/api/v1/transactions/return")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);

            log.info("Stripe PaymentIntent created: id={} status={}", intent.getId(), intent.getStatus());
            return new CardPaymentIntent(intent.getId(), intent.getClientSecret(), intent.getStatus());

        } catch (StripeException e) {
            log.error("Stripe PaymentIntent creation failed: code={}", e.getCode());
            throw new PaymentGatewayException("Failed to create payment intent: " + e.getMessage(), e);
        } finally {
            STRIPE_SEMAPHORE.release();
        }
    }

    /**
     * Fallback method executed if all Stripe PaymentIntent retries fail.
     */
    @Recover
    public CardPaymentIntent fallbackCreateCardPaymentIntent(PaymentGatewayException e, Money amount, String stripePaymentMethodId) {
        log.error("Stripe API completely unavailable for amount {}. Fallback triggered.", amount, e);
        throw new PaymentGatewayException("Payment processor is currently down. Please try again later.", e);
    }

    /**
     * Initiates an Instant Payout to the user's original card.
     * Stripe Instant Payouts require the payout destination to support instant
     * transfer.
     *
     * @param stripePaymentMethodId Payment method ID (pm_xxxx) — the card to
     *                              receive funds
     * @param amount                Amount to pay out
     * @return PayoutResult with payout ID and status
     */
    @Override
    public PayoutResult initiateInstantPayout(String stripePaymentMethodId, Money amount) {
        try {
            STRIPE_SEMAPHORE.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Interrupted while waiting for Stripe rate limit permit", e);
        }
        try {
            Map<String, Object> destination = new HashMap<>();
            destination.put("type", "card");
            destination.put("card", stripePaymentMethodId);

            PayoutCreateParams params = PayoutCreateParams.builder()
                    .setAmount(amount.toMinorUnits())
                    .setCurrency(amount.currencyCode().toLowerCase())
                    .setMethod(PayoutCreateParams.Method.INSTANT)
                    .setDestination(stripePaymentMethodId)
                    .build();

            Payout payout = Payout.create(params);

            log.info("Stripe Instant Payout created: id={} status={}", payout.getId(), payout.getStatus());
            return new PayoutResult(payout.getId(), payout.getStatus(), payout.getArrivalDate());

        } catch (StripeException e) {
            log.error("Stripe Instant Payout failed: code={}", e.getCode());
            throw new PaymentGatewayException("Failed to initiate payout: " + e.getMessage(), e);
        } finally {
            STRIPE_SEMAPHORE.release();
        }
    }

    /**
     * Validates the Stripe-Signature header on incoming webhook events.
     * Throws PaymentGatewayException if the signature is invalid.
     *
     * @param payload   Raw request body (must not be parsed before this call)
     * @param signature Stripe-Signature header value
     * @return Parsed WebhookEvent with type and object ID
     */
    @Override
    public WebhookEvent parseAndValidateWebhook(String payload, String signature) {
        try {
            Event event = Webhook.constructEvent(
                    payload, signature, stripeProperties.getWebhookSecret());

            Object deserializedObj = event.getDataObjectDeserializer().getObject().orElse(null);
            if (deserializedObj == null) {
                deserializedObj = event.getDataObjectDeserializer().deserializeUnsafe();
            }

            String objectId = "unknown";
            if (deserializedObj instanceof PaymentIntent pi) {
                objectId = pi.getId();
            } else if (deserializedObj instanceof Payout p) {
                objectId = p.getId();
            }

            log.info("Stripe webhook validated: type={} objectId={}", event.getType(), objectId);
            return new WebhookEvent(event.getId(), event.getType(), objectId, payload);

        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed");
            throw new PaymentGatewayException("Invalid webhook signature", e);
        } catch (Exception e) {
            log.error("Stripe webhook parsing failed", e);
            throw new PaymentGatewayException("Failed to parse webhook: " + e.getMessage(), e);
        }
    }
}
