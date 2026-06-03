package com.fintechwave.ledger.repository;

import com.fintechwave.ledger.domain.entity.Balance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, UUID> {

    /**
     * Pessimistic write lock — mandatory before any balance mutation.
     * Prevents concurrent overdraft: two simultaneous transfers cannot
     * both pass the balance check because the first RESERVE holds the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Balance b WHERE b.accountId = :accountId")
    Optional<Balance> findByIdWithLock(@Param("accountId") UUID accountId);

    Optional<Balance> findByAccountId(UUID accountId);

    /** Reconciliation query — sum of all LIABILITY account balances. */
    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM Balance b " +
           "JOIN b.account a WHERE a.accountType = 'LIABILITY'")
    BigDecimal sumAllLiabilityBalances();

    /** Platform Float balance for reconciliation check. */
    @Query("SELECT COALESCE(b.amount, 0) FROM Balance b " +
           "JOIN b.account a WHERE a.accountCode = '1000' AND a.ownerId IS NULL")
    BigDecimal platformFloatBalance();
}
