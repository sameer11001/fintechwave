package com.fintechwave.reporting.service;

import com.fintechwave.reporting.domain.search.LedgerEntryDocument;
import com.fintechwave.reporting.domain.search.WalletBalanceDocument;
import com.fintechwave.reporting.repository.search.LedgerEntrySearchRepository;
import com.fintechwave.reporting.repository.search.WalletBalanceSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerIndexingService {

    private final LedgerEntrySearchRepository ledgerEntryRepo;
    private final WalletBalanceSearchRepository walletBalanceRepo;

    public void indexEntry(String idempotencyKey, String transactionId,
                           String accountCode, String accountType,
                           String entryType, BigDecimal amount,
                           String currency, String description, Instant occurredAt) {
        LedgerEntryDocument doc = LedgerEntryDocument.builder()
            .id(idempotencyKey)
            .transactionId(transactionId)
            .accountCode(accountCode)
            .accountType(accountType)
            .entryType(entryType)
            .amount(amount)
            .currency(currency)
            .description(description)
            .occurredAt(occurredAt)
            .build();
        ledgerEntryRepo.save(doc);
        log.debug("Indexed ledger entry: idempotencyKey={} accountCode={}", idempotencyKey, accountCode);
    }

    public void upsertWalletBalance(String ownerId, String entryType,
                                    BigDecimal amount, String currency) {
        WalletBalanceDocument doc = walletBalanceRepo.findById(ownerId)
            .orElse(WalletBalanceDocument.builder()
                .userId(ownerId)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .build());

        // LIABILITY account: CREDIT increases balance, DEBIT decreases
        if ("CREDIT".equals(entryType)) {
            doc.setBalance(doc.getBalance().add(amount));
        } else {
            doc.setBalance(doc.getBalance().subtract(amount));
        }
        doc.setLastUpdatedAt(Instant.now());
        walletBalanceRepo.save(doc);
        log.debug("Upserted wallet balance: userId={} balance={}", ownerId, doc.getBalance());
    }
}
