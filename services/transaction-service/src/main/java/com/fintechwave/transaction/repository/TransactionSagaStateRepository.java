package com.fintechwave.transaction.repository;

import com.fintechwave.transaction.domain.entity.TransactionSagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionSagaStateRepository extends JpaRepository<TransactionSagaState, UUID> {

    @Query("SELECT s FROM TransactionSagaState s " +
           "WHERE s.currentStep NOT IN ('COMPLETED', 'FAILED') " +
           "AND s.createdAt < :cutoff")
    List<TransactionSagaState> findStuckSagas(@Param("cutoff") Instant cutoff);

    List<TransactionSagaState> findBySenderId(UUID senderId);
}
