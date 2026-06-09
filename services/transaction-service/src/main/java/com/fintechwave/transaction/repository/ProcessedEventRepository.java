package com.fintechwave.transaction.repository;

import com.fintechwave.transaction.domain.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByIdempotencyKey(UUID idempotencyKey);
}
