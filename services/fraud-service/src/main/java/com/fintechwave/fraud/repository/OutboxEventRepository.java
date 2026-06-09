package com.fintechwave.fraud.repository;

import com.fintechwave.fraud.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop50ByPublishedFalseOrderByOccurredAtAsc();
}
