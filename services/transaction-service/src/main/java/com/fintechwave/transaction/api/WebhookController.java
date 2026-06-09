package com.fintechwave.transaction.api;

import com.fintechwave.transaction.service.ITransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook receiver for Stripe payment events.
 * These endpoints are NOT JWT-protected — authentication is via Stripe HMAC signature.
 *
 * Stripe-Signature header is ALWAYS validated before any processing.
 * The raw body must be passed unmodified to the signature verifier.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ITransactionService transactionService;

    /**
     * POST /api/v1/webhooks/stripe
     * Receives Stripe payment events:
     * - payment_intent.succeeded → complete cash-in, credit wallet
     * - payment_intent.payment_failed → mark cash-in failed
     * - payout.paid → complete cash-out, commit ledger
     * - payout.failed → mark cash-out failed, release reserved funds
     */
    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String rawPayload,
            @RequestHeader("Stripe-Signature") String signature) {
        log.debug("Stripe webhook received");
        transactionService.handleStripeWebhook(rawPayload, signature);
        return ResponseEntity.ok().build();
    }
}
