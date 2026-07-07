package com.fintechwave.notification.service.impl;

import com.fintechwave.notification.domain.entity.Notification;
import com.fintechwave.notification.domain.enums.NotificationChannel;
import com.fintechwave.notification.domain.enums.NotificationStatus;
import com.fintechwave.notification.repository.NotificationRepository;
import com.fintechwave.notification.repository.ProcessedEventRepository;
import com.fintechwave.notification.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements INotificationService {

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional
    public void send(UUID idempotencyKey, UUID recipientId, NotificationChannel channel,
            String templateCode, String subject, String body) {

        // ── 1. Idempotency guard ─────────────────────────────────────────────
        int inserted = processedEventRepository.insertIfNotExists(idempotencyKey, Instant.now());
        if (inserted == 0) {
            log.warn("Duplicate notification skipped: idempotencyKey={} template={}", idempotencyKey, templateCode);
            return;
        }

        // ── 2. Persist notification record ───────────────────────────────────
        Notification notification = Notification.builder()
                .idempotencyKey(idempotencyKey)
                .recipientId(recipientId)
                .channel(channel)
                .templateCode(templateCode)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        notificationRepository.save(notification);

        // ── 3. Dispatch by channel ───────────────────────────────────────────
        try {
            notificationDispatcher.dispatch(channel, recipientId, subject, body);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Notification sent: recipientId={} template={} channel={}", recipientId, templateCode, channel);
        } catch (Exception ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(ex.getMessage());
            log.error("Notification delivery failed: recipientId={} template={} channel={}", recipientId, templateCode,
                    channel, ex);
        } finally {
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public int purgeOldNotifications(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = notificationRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Notification retention sweep: deleted {} records older than {} days", deleted, retentionDays);
        }
        return deleted;
    }

    // ─── Channel adapters moved to NotificationDispatcher ──────────────
}
