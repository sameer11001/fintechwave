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
import com.fintechwave.ledger.repository.AccountRepository;
import com.fintechwave.ledger.repository.BalanceRepository;
import com.fintechwave.ledger.repository.LedgerEntryRepository;
import com.fintechwave.ledger.service.ILedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LedgerServiceImpl implements ILedgerService {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional
    public WalletResponse provisionWallet(UUID userId, String currency) {
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
                .account(account)
                .amount(BigDecimal.ZERO)
                .currency(currency)
                .version(0L)
                .updatedAt(Instant.now())
                .build();
        balanceRepository.save(balance);

        log.info("Wallet provisioned: accountId={} userId={}", account.getId(), userId);

        return WalletResponse.builder()
                .accountId(account.getId())
                .ownerId(userId)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .build();
    }

    @Override
    @Transactional
    public void commitDoubleEntry(DoubleEntryRequest request) {
        validateBalance(request);

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
            if (EntryType.CREDIT == entry.getEntryType()) {
                // LIABILITY/EQUITY accounts: CREDIT increases balance
                // ASSET/EXPENSE accounts: CREDIT decreases balance
                // For simplicity: wallet (LIABILITY) CREDIT = increase
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
        }

        log.info("Double-entry committed: transactionId={}", request.transactionId());
    }

    @Override
    @Transactional
    public void reserve(UUID transactionId, UUID sourceAccountId, BigDecimal amount, String currency) {
        Account suspense = getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

        commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                new DoubleEntryRequest.EntryLine(sourceAccountId, "DEBIT", amount, currency, UUID.randomUUID(),
                        "RESERVE: lock funds"),
                new DoubleEntryRequest.EntryLine(suspense.getId(), "CREDIT", amount, currency, UUID.randomUUID(),
                        "RESERVE: credit suspense"))));

        log.info("RESERVE: transactionId={} amount={} currency={}", transactionId, amount, currency);
    }

    @Override
    @Transactional
    public void commit(UUID transactionId, UUID destinationAccountId, BigDecimal amount, String currency) {
        Account suspense = getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

        commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                new DoubleEntryRequest.EntryLine(suspense.getId(), "DEBIT", amount, currency, UUID.randomUUID(),
                        "COMMIT: debit suspense"),
                new DoubleEntryRequest.EntryLine(destinationAccountId, "CREDIT", amount, currency, UUID.randomUUID(),
                        "COMMIT: credit destination"))));

        log.info("COMMIT: transactionId={} amount={} currency={}", transactionId, amount, currency);
    }

    @Override
    @Transactional
    public void release(UUID transactionId, UUID sourceAccountId, BigDecimal amount, String currency) {
        Account suspense = getOrCreatePlatformAccount(AccountCode.SUSPENSE, currency);

        commitDoubleEntry(new DoubleEntryRequest(transactionId, List.of(
                new DoubleEntryRequest.EntryLine(suspense.getId(), "DEBIT", amount, currency, UUID.randomUUID(),
                        "RELEASE: return from suspense"),
                new DoubleEntryRequest.EntryLine(sourceAccountId, "CREDIT", amount, currency, UUID.randomUUID(),
                        "RELEASE: credit back to source"))));

        log.info("RELEASE: transactionId={} amount={} currency={}", transactionId, amount, currency);
    }

    @Override
    public WalletResponse getWalletBalance(UUID userId) {
        Account account = accountRepository
                .findByOwnerIdAndAccountCode(userId, AccountCode.USER_WALLET.getCode())
                .orElseThrow(() -> new WalletNotFoundException(userId));

        Balance balance = balanceRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new WalletNotFoundException(account.getId()));

        return WalletResponse.builder()
                .accountId(account.getId())
                .ownerId(userId)
                .balance(balance.getAmount())
                .currency(balance.getCurrency())
                .createdAt(account.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void reconcile() {
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
                            .account(acc)
                            .amount(BigDecimal.ZERO)
                            .currency(currency)
                            .version(0L)
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
}
