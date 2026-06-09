package com.fintechwave.reporting.repository;

import com.fintechwave.reporting.domain.entity.TransactionSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionSummaryRepository extends JpaRepository<TransactionSummary, UUID> {
    Page<TransactionSummary> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);
    Optional<TransactionSummary> findByTransactionId(UUID transactionId);
    boolean existsByTransactionId(UUID transactionId);
}
