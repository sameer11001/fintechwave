package com.fintechwave.ledger.service;

import com.fintechwave.ledger.domain.entity.Account;
import com.fintechwave.ledger.domain.enums.AccountCode;
import com.fintechwave.ledger.dto.request.DoubleEntryRequest;
import com.fintechwave.ledger.dto.response.WalletResponse;

import java.math.BigDecimal;
import java.util.UUID;

public interface ILedgerService {

    /**
     * Provisions a new USER_WALLET account and Balance for a user.
     * Called when KYCVerified event is received.
     * No wallet is ever created without this event — compliance gate.
     */
    WalletResponse provisionWallet(UUID userId, String currency);

    /**
     * Posts a double-entry transaction atomically.
     * Enforces: SUM(DEBIT) = SUM(CREDIT) before commit.
     * Uses SELECT FOR UPDATE on balance rows to prevent concurrent overdraft.
     */
    void commitDoubleEntry(DoubleEntryRequest request);

    /**
     * RESERVE: Dr Source Wallet → Cr Suspense/Hold
     * Funds are locked — concurrent transfers cannot double-spend.
     */
    void reserve(UUID transactionId, UUID sourceAccountId, BigDecimal amount, String currency);

    /**
     * COMMIT: Dr Suspense → Cr Destination
     * Completes a previously reserved transaction.
     */
    void commit(UUID transactionId, UUID destinationAccountId, BigDecimal amount, String currency);

    /**
     * RELEASE: Dr Suspense → Cr Source Wallet
     * Returns funds on failure — reversal of the RESERVE.
     */
    void release(UUID transactionId, UUID sourceAccountId, BigDecimal amount, String currency);

    /**
     * Gets the current balance for a user's wallet account.
     */
    WalletResponse getWalletBalance(UUID userId);

    /**
     * Reconciliation: verifies SUM(all user wallets) = Platform Float balance.
     * Admin-only endpoint. Alerts PagerDuty SEV-1 on divergence.
     */
    void reconcile();

    /** Finds or creates the platform account for the given code (idempotent). */
    Account getOrCreatePlatformAccount(AccountCode code, String currency);
}
