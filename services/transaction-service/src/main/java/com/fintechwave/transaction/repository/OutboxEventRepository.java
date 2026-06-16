package com.fintechwave.transaction.repository;

import com.fintechwave.transaction.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByPublishedFalseOrderByOccurredAtAsc();

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
