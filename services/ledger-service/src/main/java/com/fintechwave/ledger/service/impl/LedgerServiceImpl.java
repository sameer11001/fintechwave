package com.fintechwave.ledger.service.impl;

import com.fintechwave.ledger.domain.entity.Account;
import com.fintechwave.ledger.domain.entity.Balance;
import com.fintechwave.ledger.domain.entity.LedgerEntry;
import com.fintechwave.ledger.domain.enums.AccountCode;
import com.fintechwave.ledger.domain.enums.AccountType;
import com.fintechwave.ledger.domain.enums.EntryType;
import com.fintechwave.ledger.dto.request.DoubleEntryRequest;
import com.fintechwave.ledger.dto.response.WalletResponse;
import com.fintechwave.ledger.exception.InsufficientBalanceException;
import com.fintechwave.ledger.exception.LedgerBalanceViolationException;
import com.fintechwave.ledger.exception.WalletNotFoundException;
import com.fintechwave.ledger.domain.entity.OutboxEvent;
import com.fintechwave.ledger.repository.AccountRepository;
import com.fintechwave.ledger.repository.BalanceRepository;
import com.fintechwave.ledger.repository.LedgerEntryRepository;
import com.fintechwave.ledger.repository.OutboxEventRepository;
import com.fintechwave.ledger.service.ILedgerService;
import com.fintechwave.events.GenericDomainEvent;
import com.fintechwave.core.messaging.OutboxEventHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.ledger.mapper.WalletMapper;
import io.micrometer.tracing.annotation.NewSpan;
import io.micrometer.tracing.annotation.SpanTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fintechwave.core.observability.BusinessContextMdc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LedgerServiceImpl implements ILedgerService {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final WalletMapper walletMapper;

    @Override
    @Transactional
    public WalletResponse provisionWallet(UUID userId, String currency) {
        try (var ctx = BusinessContextMdc.of(userId, null, "WALLET_PROVISIONED")) {
            if (accountRepository.existsByOwnerIdAndAccountCode(userId, AccountCode.USER_WALLET.getCode())) {
                log.warn("Wallet already exists for userId={} — idempotent skip", userId);
                return getWalletBalance(userId);
            }

            Account account = Account.builder()
                    .ownerId(userId)
                    .accountType(AccountType.LIABILITY)
                    .accountCode(AccountCode.USER_WALLET.getCode())
                    .currency(currency)
                    .status("ACTIVE")
                    .build();
            account = accountRepository.save(account);

            Balance balance = Balance.builder()
                    .accountId(account.getId())
                    .account(account)
                    .amount(BigDecimal.ZERO)
                    .currency(currency)
                    .updatedAt(Instant.now())
                    .build();
            balanceRepository.save(balance);

            log.info("Wallet provisioned: accountId={} userId={}", account.getId(), userId);

            return walletMapper.toResponse(account, balance);
        }
    }

    @Override
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

                GenericDomainEvent domainEvent = OutboxEventHelper.buildDomainEvent(
                        "LEDGER_COMMITTED", 1, request.transactionId(), "LEDGER", enrichedPayload);
                String payloadJson = OutboxEventHelper.toJson(objectMapper, domainEvent);

                outboxEventRepository.save(OutboxEvent.builder()
                        .aggregateId(domainEvent.getAggregateId())
                        .aggregateType(domainEvent.getAggregateType())
                        .eventType(domainEvent.getEventType())
                        .topic("ledger.transaction-results")
                        .payload(payloadJson)
                        .idempotencyKey(UUID.randomUUID())
                        .published(false)
                        .build());
            }

            log.info("Double-entry committed: transactionId={}", request.transactionId());
        }
    }

    @Override
    @Transactional
    @NewSpan("ledger.reserve")
    public void reserve(@SpanTag("transaction.id") UUID transactionId,
            @SpanTag("account.source") UUID sourceAccountId,
            @SpanTag("amount") BigDecimal amount,
            @SpanTag("currency") String currency) {
        try (var ctx = BusinessContextMdc.of(null, transactionId, sourceAccountId, "LEDGER_RESERVE")) {
            Account suspense = getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

            commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                    new DoubleEntryRequest.EntryLine(sourceAccountId, "DEBIT", amount, currency,
                            UUID.nameUUIDFromBytes((transactionId.toString() + "-reserve-debit").getBytes()),
                            "RESERVE: lock funds"),
                    new DoubleEntryRequest.EntryLine(suspense.getId(), "CREDIT", amount, currency,
                            UUID.nameUUIDFromBytes((transactionId.toString() + "-reserve-credit").getBytes()),
                            "RESERVE: credit suspense"))));

            log.info("RESERVE: transactionId={} amount={} currency={}", transactionId, amount, currency);
        }
    }

    @Override
    @Transactional
    @NewSpan("ledger.commit")
    public void commit(@SpanTag("transaction.id") UUID transactionId,
            @SpanTag("account.destination") UUID destinationAccountId,
            @SpanTag("amount") BigDecimal amount,
            @SpanTag("currency") String currency) {
        try (var ctx = BusinessContextMdc.of(null, transactionId, destinationAccountId, "LEDGER_COMMIT")) {
            Account suspense = getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

            commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                    new DoubleEntryRequest.EntryLine(suspense.getId(), "DEBIT", amount, currency,
                            UUID.nameUUIDFromBytes((transactionId.toString() + "-commit-debit").getBytes()),
                            "COMMIT: debit suspense"),
                    new DoubleEntryRequest.EntryLine(destinationAccountId, "CREDIT", amount, currency,
                            UUID.nameUUIDFromBytes((transactionId.toString() + "-commit-credit").getBytes()),
                            "COMMIT: credit destination"))));

            log.info("COMMIT: transactionId={} amount={} currency={}", transactionId, amount, currency);
        }
    }

    @Override
    @Transactional
    @NewSpan("ledger.release")
    public void release(@SpanTag("transaction.id") UUID transactionId,
            @SpanTag("account.source") UUID sourceAccountId,
            @SpanTag("amount") BigDecimal amount,
            @SpanTag("currency") String currency) {
        try (var ctx = BusinessContextMdc.of(null, transactionId, sourceAccountId, "LEDGER_RELEASE")) {
            Account suspense = getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

            commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                    new DoubleEntryRequest.EntryLine(suspense.getId(), "DEBIT", amount, currency,
                            UUID.nameUUIDFromBytes((transactionId.toString() + "-release-debit").getBytes()),
                            "RELEASE: return from suspense"),
                    new DoubleEntryRequest.EntryLine(sourceAccountId, "CREDIT", amount, currency,
                            UUID.nameUUIDFromBytes((transactionId.toString() + "-release-credit").getBytes()),
                            "RELEASE: credit back to source"))));

            log.info("RELEASE: transactionId={} amount={} currency={}", transactionId, amount, currency);
        }
    }

    @Override
    public WalletResponse getWalletBalance(UUID userId) {
        Account account = accountRepository
                .findByOwnerIdAndAccountCode(userId, AccountCode.USER_WALLET.getCode())
                .orElseThrow(() -> new WalletNotFoundException(userId));

        Balance balance = balanceRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new WalletNotFoundException(account.getId()));

        return walletMapper.toResponse(account, balance);
    }

    @Override
    @Transactional
    public void reconcile() {
        try (var ctx = BusinessContextMdc.of(null, null, "LEDGER_RECONCILE")) {
            BigDecimal totalLiabilities = balanceRepository.sumAllLiabilityBalances();
            BigDecimal platformFloat = balanceRepository.platformFloatBalance();

            log.info("Reconciliation: totalLiabilities={} platformFloat={}", totalLiabilities, platformFloat);

            if (totalLiabilities.compareTo(platformFloat) != 0) {
                BigDecimal divergence = totalLiabilities.subtract(platformFloat);
                log.error("RECONCILIATION FAILURE: divergence={} — SEV-1 alert required", divergence);
                throw new LedgerBalanceViolationException(
                        "Reconciliation mismatch: liabilities=" + totalLiabilities +
                                " float=" + platformFloat + " divergence=" + divergence);
            }

            log.info("Reconciliation PASSED: balance={}", totalLiabilities);
        }
    }

    @Override
    @Transactional
    public void simulateDivergence() {
        try (var ctx = BusinessContextMdc.of(null, null, "LEDGER_SIMULATE_DIVERGENCE")) {
            Account floatAccount = getOrCreatePlatformAccount(AccountCode.PLATFORM_FLOAT, "JOD");
            Balance balance = balanceRepository.findByAccountId(floatAccount.getId())
                    .orElseThrow(() -> new WalletNotFoundException(floatAccount.getId()));

            balance.setAmount(balance.getAmount().add(new BigDecimal("2000.00")));
            balance.setUpdatedAt(Instant.now());
            balanceRepository.save(balance);

            log.warn("SIMULATING DIVERGENCE: Platform float artificially inflated by $2,000.00");

            java.util.Map<String, Object> enrichedPayload = new java.util.HashMap<>();
            enrichedPayload.put("transactionId", UUID.randomUUID().toString());

            List<Map<String, Object>> fakeEntries = new ArrayList<>();
            Map<String, Object> fakeEntry = new HashMap<>();
            fakeEntry.put("accountId", floatAccount.getId().toString());
            fakeEntry.put("accountCode", floatAccount.getAccountCode());
            fakeEntry.put("accountType", floatAccount.getAccountType().name());
            fakeEntry.put("entryType", "CREDIT");
            fakeEntry.put("amount", new BigDecimal("2000.00"));
            fakeEntry.put("currency", "USD");
            fakeEntry.put("idempotencyKey", UUID.randomUUID().toString());
            fakeEntry.put("description", "DIVERGENCE SIMULATION");
            fakeEntries.add(fakeEntry);

            enrichedPayload.put("entries", fakeEntries);

            GenericDomainEvent domainEvent = OutboxEventHelper.buildDomainEvent(
                    "LEDGER_COMMITTED", 1, UUID.randomUUID(), "LEDGER", enrichedPayload);
            String payloadJson = OutboxEventHelper.toJson(objectMapper, domainEvent);

            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateId(domainEvent.getAggregateId())
                    .aggregateType(domainEvent.getAggregateType())
                    .eventType(domainEvent.getEventType())
                    .topic("ledger.transaction-results")
                    .payload(payloadJson)
                    .idempotencyKey(UUID.randomUUID())
                    .published(false)
                    .build());
        }
    }

    @Override
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

    private void validateBalance(DoubleEntryRequest request) {
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

    @Override
    @Transactional
    public void recordManualReconciliation(UUID transactionId, String reason) {
        try (var ctx = BusinessContextMdc.of(null, transactionId, "LEDGER_MANUAL_RECONCILIATION")) {
            log.error("SEV-1: Manual reconciliation recorded for transactionId={}. Reason: {}", transactionId, reason);
            // TODO: In a production system, this could save to a specific
            // ManualReconciliation entity table.
            // For now, we will log it heavily and rely on the observability stack to catch
            // this SEV-1.
        }
    }
}
