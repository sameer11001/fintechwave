package com.fintechwave.ledger.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.core.messaging.OutboxEventEnvelope;
import com.fintechwave.core.messaging.OutboxEventHelper;
import com.fintechwave.core.observability.BusinessContextMdc;
import com.fintechwave.ledger.domain.entity.Account;
import com.fintechwave.ledger.domain.entity.Balance;
import com.fintechwave.ledger.domain.entity.LedgerEntry;
import com.fintechwave.ledger.domain.entity.OutboxEvent;
import com.fintechwave.ledger.domain.enums.AccountCode;
import com.fintechwave.ledger.domain.enums.EntryType;
import com.fintechwave.ledger.dto.request.DoubleEntryRequest;
import com.fintechwave.ledger.exception.InsufficientBalanceException;
import com.fintechwave.ledger.exception.LedgerBalanceViolationException;
import com.fintechwave.ledger.exception.WalletNotFoundException;
import com.fintechwave.ledger.repository.AccountRepository;
import com.fintechwave.ledger.repository.BalanceRepository;
import com.fintechwave.ledger.repository.LedgerEntryRepository;
import com.fintechwave.ledger.repository.OutboxEventRepository;
import io.micrometer.tracing.annotation.NewSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DoubleEntryProcessor {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @NewSpan("ledger.commit-double-entry")
    public void commitDoubleEntry(DoubleEntryRequest request) {
        try (var ctx = BusinessContextMdc.of(null, request.transactionId(), "LEDGER_COMMITTED")) {
            validateBalance(request);

            List<Map<String, Object>> enrichedEntries = new ArrayList<>();

            for (DoubleEntryRequest.EntryLine line : request.entries()) {
                // Skip if idempotency key already processed
                if (ledgerEntryRepository.existsByIdempotencyKey(line.idempotencyKey())) {
                    log.warn("Duplicate entry skipped: idempotencyKey={}", line.idempotencyKey());
                    continue;
                }

                Account account = accountRepository.findById(line.accountId())
                        .orElseThrow(() -> new WalletNotFoundException(line.accountId()));

                // Persist the journal entry
                LedgerEntry entry = LedgerEntry.builder()
                        .transactionId(request.transactionId())
                        .account(account)
                        .entryType(EntryType.valueOf(line.entryType()))
                        .amount(line.amount())
                        .currency(line.currency())
                        .idempotencyKey(line.idempotencyKey())
                        .description(line.description())
                        .build();
                ledgerEntryRepository.save(entry);

                Balance balance = balanceRepository.findByIdWithLock(account.getId())
                        .orElseThrow(() -> new WalletNotFoundException(account.getId()));

                BigDecimal newAmount;
                if (entry.getEntryType() == account.getAccountType().getNormalBalance()) {
                    newAmount = balance.getAmount().add(line.amount());
                } else {
                    newAmount = balance.getAmount().subtract(line.amount());
                }

                if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new InsufficientBalanceException(account.getId(), balance.getAmount(), line.amount());
                }

                balance.setAmount(newAmount);
                balance.setUpdatedAt(Instant.now());
                balanceRepository.save(balance);

                Map<String, Object> enrichedLine = new HashMap<>();
                enrichedLine.put("accountId", line.accountId().toString());
                enrichedLine.put("accountCode", account.getAccountCode());
                enrichedLine.put("accountType", account.getAccountType().name());

                if (AccountCode.USER_WALLET.getCode().equals(account.getAccountCode())
                        && account.getOwnerId() != null) {
                    enrichedLine.put("ownerId", account.getOwnerId().toString());
                }

                enrichedLine.put("entryType", line.entryType());
                enrichedLine.put("amount", line.amount());
                enrichedLine.put("currency", line.currency());
                enrichedLine.put("idempotencyKey", line.idempotencyKey().toString());
                enrichedLine.put("description", line.description());
                enrichedEntries.add(enrichedLine);
            }

            if (!enrichedEntries.isEmpty()) {
                java.util.Map<String, Object> enrichedPayload = new java.util.HashMap<>();
                enrichedPayload.put("transactionId", request.transactionId().toString());
                enrichedPayload.put("entries", enrichedEntries);

                OutboxEventEnvelope env = OutboxEventHelper.prepare(
                        objectMapper, "LEDGER_COMMITTED", 1, request.transactionId(), "LEDGER", enrichedPayload);

                outboxEventRepository.save(OutboxEvent.from(env, "ledger.transaction-results"));
            }

            log.info("Double-entry committed: transactionId={}", request.transactionId());
        }
    }

    @Transactional
    public Account getOrCreatePlatformAccount(AccountCode code, String currency) {
        return accountRepository.findByAccountCodeAndOwnerIdIsNull(code.getCode())
                .orElseGet(() -> {
                    Account acc = accountRepository.save(Account.builder()
                            .accountType(code.getType())
                            .accountCode(code.getCode())
                            .currency(currency)
                            .status("ACTIVE")
                            .build());
                    balanceRepository.save(Balance.builder()
                            .accountId(acc.getId())
                            .account(acc)
                            .amount(BigDecimal.ZERO)
                            .currency(currency)
                            .updatedAt(Instant.now())
                            .build());
                    log.info("Platform account provisioned: code={} accountId={}", code.getCode(), acc.getId());
                    return acc;
                });
    }

    private static void validateBalance(DoubleEntryRequest request) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (DoubleEntryRequest.EntryLine line : request.entries()) {
            if ("DEBIT".equals(line.entryType())) {
                totalDebits = totalDebits.add(line.amount());
            } else if ("CREDIT".equals(line.entryType())) {
                totalCredits = totalCredits.add(line.amount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new LedgerBalanceViolationException(
                    "Double-entry balance violation: DEBIT=" + totalDebits +
                            " CREDIT=" + totalCredits + " for transactionId=" + request.transactionId());
        }
    }
}
