package com.fintechwave.transaction.service;

import com.fintechwave.transaction.dto.request.CashInRequest;
import com.fintechwave.transaction.dto.request.CashOutRequest;
import com.fintechwave.transaction.dto.request.InitiateTransferRequest;
import com.fintechwave.transaction.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Core transaction orchestration service.
 * Manages the reservation/commit/release lifecycle and Stripe integration.
 */
public interface ITransactionService {

    /**
     * Initiates a P2P wallet-to-wallet transfer.
     * Flow: RESERVE funds in ledger → publish TransferInitiated for fraud check.
     *
     * @param senderId Initiating user's Keycloak ID (from JWT)
     * @param request  Transfer details with client idempotency key
     */
    TransactionResponse initiateP2PTransfer(UUID senderId, InitiateTransferRequest request);

    /**
     * Initiates a cash-in via Stripe Payment Intent.
     * Creates Stripe intent → returns client_secret for frontend confirmation.
     * Actual ledger credit happens on payment_intent.succeeded webhook.
     *
     * @param userId  Initiating user's Keycloak ID (from JWT)
     * @param request Cash-in amount and Stripe payment method ID
     */
    TransactionResponse initiateCashIn(UUID userId, CashInRequest request);

    /**
     * Initiates a cash-out via Stripe Instant Payout.
     * Flow: RESERVE funds → call Stripe Instant Payout → on payout.paid webhook → COMMIT.
     *
     * @param userId  Initiating user's Keycloak ID (from JWT)
     * @param request Cash-out amount and Stripe payment method ID
     */
    TransactionResponse initiateCashOut(UUID userId, CashOutRequest request);

    /**
     * Handles Stripe webhook events.
     * Routes payment_intent.succeeded → ledger credit.
     * Routes payout.paid → ledger commit.
     * Routes *.failed → release reserved funds + mark FAILED.
     *
     * @param rawPayload  Raw request body — must be passed before any parsing
     * @param signature   Stripe-Signature header value
     */
    void handleStripeWebhook(String rawPayload, String signature);

    /**
     * Returns paginated transaction history for the calling user.
     */
    Page<TransactionResponse> getMyTransactions(UUID userId, Pageable pageable);

    /**
     * Returns a single transaction by ID — caller must be sender or receiver.
     */
    TransactionResponse getTransactionById(UUID transactionId, UUID callerId);

    void handleFraudDecision(UUID transactionId, boolean approved);
}
