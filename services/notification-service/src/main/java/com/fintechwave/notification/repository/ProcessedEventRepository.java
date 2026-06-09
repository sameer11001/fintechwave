package com.fintechwave.notification.repository;

import com.fintechwave.notification.domain.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(value = "INSERT INTO notif_processed_events (idempotency_key, processed_at) VALUES (:idempotencyKey, :processedAt) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfNotExists(@Param("idempotencyKey") UUID idempotencyKey, @Param("processedAt") Instant processedAt);
}
