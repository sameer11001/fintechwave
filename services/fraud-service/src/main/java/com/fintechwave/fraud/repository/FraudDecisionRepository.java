package com.fintechwave.fraud.repository;

import com.fintechwave.fraud.domain.entity.FraudDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudDecisionRepository extends JpaRepository<FraudDecision, UUID> {
    Page<FraudDecision> findByUserIdOrderByDecidedAtDesc(UUID userId, Pageable pageable);

    boolean existsByTransactionId(UUID transactionId);
}
