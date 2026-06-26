package com.fintechwave.transaction.messaging;

import com.fintechwave.transaction.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {

    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(cron = "0 0 3 * * *")  // 3 AM UTC daily
    @SchedulerLock(
        name = "transaction_outbox_cleanup",
        lockAtLeastFor = "PT5M",
        lockAtMostFor = "PT10M"
    )
    @Transactional
    public void purgeStaleOutboxRows() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxEventRepository.deleteByOccurredAtBefore(cutoff);
        log.info("Outbox cleanup: deleted {} stale rows older than {}", deleted, cutoff);
    }
}
