package com.fintechwave.transaction.repository;

import com.fintechwave.transaction.domain.entity.TransactionRecord;
import com.fintechwave.transaction.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionRecord, UUID> {

    Optional<TransactionRecord> findByIdempotencyKey(UUID idempotencyKey);

    Page<TransactionRecord> findBySenderIdOrReceiverId(UUID senderId, UUID receiverId, Pageable pageable);

    Page<TransactionRecord> findBySenderIdAndTransactionType(UUID senderId, TransactionType type, Pageable pageable);

    boolean existsByIdempotencyKey(UUID idempotencyKey);

    /**
     * Used by Stripe webhook handlers — avoids full-table scan.
     * Requires a DB index on stripe_payment_intent_id.
     */
    Optional<TransactionRecord> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Used by Stripe payout webhook handlers — avoids full-table scan.
     * Requires a DB index on stripe_payout_id.
     */
    Optional<TransactionRecord> findByStripePayoutId(String stripePayoutId);
}
