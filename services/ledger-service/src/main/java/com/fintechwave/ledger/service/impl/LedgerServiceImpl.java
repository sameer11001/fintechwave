package com.fintechwave.ledger.service.impl;

import com.fintechwave.ledger.domain.entity.Account;
import com.fintechwave.ledger.domain.entity.Balance;
import com.fintechwave.ledger.domain.enums.AccountCode;
import com.fintechwave.ledger.domain.enums.AccountType;
import com.fintechwave.ledger.dto.request.DoubleEntryRequest;
import com.fintechwave.ledger.dto.response.WalletResponse;
import com.fintechwave.ledger.exception.LedgerBalanceViolationException;
import com.fintechwave.ledger.exception.WalletNotFoundException;
import com.fintechwave.ledger.domain.entity.OutboxEvent;
import com.fintechwave.ledger.repository.AccountRepository;
import com.fintechwave.ledger.repository.BalanceRepository;
import com.fintechwave.ledger.repository.OutboxEventRepository;
import com.fintechwave.ledger.service.ILedgerService;
import com.fintechwave.core.messaging.OutboxEventHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.core.messaging.OutboxEventEnvelope;
import com.fintechwave.ledger.util.IdempotencyKeyFactory;
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
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final WalletMapper walletMapper;
    private final DoubleEntryProcessor doubleEntryProcessor;

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
    public void commitDoubleEntry(DoubleEntryRequest request) {
        doubleEntryProcessor.commitDoubleEntry(request);
    }

    @Override
    @Transactional
    @NewSpan("ledger.reserve")
    public void reserve(@SpanTag("transaction.id") UUID transactionId,
            @SpanTag("account.source") UUID sourceAccountId,
            @SpanTag("amount") BigDecimal amount,
            @SpanTag("currency") String currency) {
        try (var ctx = BusinessContextMdc.of(null, transactionId, sourceAccountId, "LEDGER_RESERVE")) {
            Account suspense = doubleEntryProcessor.getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

            doubleEntryProcessor.commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                    new DoubleEntryRequest.EntryLine(sourceAccountId, "DEBIT", amount, currency,
                            IdempotencyKeyFactory.from(transactionId, "reserve-debit"),
                            "RESERVE: lock funds"),
                    new DoubleEntryRequest.EntryLine(suspense.getId(), "CREDIT", amount, currency,
                            IdempotencyKeyFactory.from(transactionId, "reserve-credit"),
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
            Account suspense = doubleEntryProcessor.getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

            doubleEntryProcessor.commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                    new DoubleEntryRequest.EntryLine(suspense.getId(), "DEBIT", amount, currency,
                            IdempotencyKeyFactory.from(transactionId, "commit-debit"),
                            "COMMIT: debit suspense"),
                    new DoubleEntryRequest.EntryLine(destinationAccountId, "CREDIT", amount, currency,
                            IdempotencyKeyFactory.from(transactionId, "commit-credit"),
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
            Account suspense = doubleEntryProcessor.getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

            doubleEntryProcessor.commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                    new DoubleEntryRequest.EntryLine(suspense.getId(), "DEBIT", amount, currency,
                            IdempotencyKeyFactory.from(transactionId, "release-debit"),
                            "RELEASE: return from suspense"),
                    new DoubleEntryRequest.EntryLine(sourceAccountId, "CREDIT", amount, currency,
                            IdempotencyKeyFactory.from(transactionId, "release-credit"),
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
            Account floatAccount = doubleEntryProcessor.getOrCreatePlatformAccount(AccountCode.PLATFORM_FLOAT, "JOD");
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

            OutboxEventEnvelope env = OutboxEventHelper.prepare(
                    objectMapper, "LEDGER_COMMITTED", 1, UUID.randomUUID(), "LEDGER", enrichedPayload);

            outboxEventRepository.save(OutboxEvent.from(env, "ledger.transaction-results"));
        }
    }

    @Override
    @Transactional
    public Account getOrCreatePlatformAccount(AccountCode code, String currency) {
        return doubleEntryProcessor.getOrCreatePlatformAccount(code, currency);
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
