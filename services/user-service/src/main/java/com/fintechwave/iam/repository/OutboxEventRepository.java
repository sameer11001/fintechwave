package com.fintechwave.iam.repository;

import com.fintechwave.iam.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false ORDER BY o.occurredAt ASC")
    List<OutboxEvent> findUnpublished();

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.published = true, o.publishedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    int markPublished(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@Param("cutoff") java.time.Instant cutoff);
}
