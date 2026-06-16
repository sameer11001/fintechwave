package com.fintechwave.ledger.repository;

import com.fintechwave.ledger.domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    boolean existsByIdempotencyKey(UUID idempotencyKey);
}
