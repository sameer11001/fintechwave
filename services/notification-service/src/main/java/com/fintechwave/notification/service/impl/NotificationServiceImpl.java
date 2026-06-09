package com.fintechwave.notification.service.impl;

import com.fintechwave.notification.domain.entity.Notification;
import com.fintechwave.notification.domain.entity.ProcessedEvent;
import com.fintechwave.notification.domain.enums.NotificationChannel;
import com.fintechwave.notification.domain.enums.NotificationStatus;
import com.fintechwave.notification.repository.NotificationRepository;
import com.fintechwave.notification.repository.ProcessedEventRepository;
import com.fintechwave.notification.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
    private final JavaMailSender mailSender;

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
            switch (channel) {
                case EMAIL -> sendEmail(recipientId, subject, body);
                case SMS -> sendSms(recipientId, body);
                case PUSH -> sendPush(recipientId, subject, body);
            }
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

    // ─── Channel adapters ─────────────────────────────────────────────────────

    private void sendEmail(UUID recipientId, String subject, String body) {
        // In production: resolve email from user-service via Feign or inject from event
        // payload.
        // Placeholder uses recipientId as identifier — replace with actual email
        // resolution.
        log.debug("EMAIL dispatch: recipientId={} subject={}", recipientId, subject);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("user+" + recipientId + "@fintechwave.internal");
        message.setSubject(subject != null ? subject : "FintechWave Notification");
        message.setText(body);
        mailSender.send(message);
    }

    private void sendSms(UUID recipientId, String body) {
        // Twilio integration point — stubbed for Phase 3 baseline.
        // Replace with TwilioSmsAdapter when SMS channel goes live.
        log.info("SMS dispatch (stubbed): recipientId={} body_length={}", recipientId, body.length());
    }

    private void sendPush(UUID recipientId, String title, String body) {
        // FCM/APNs integration point — stubbed for Phase 3 baseline.
        // Replace with FcmPushAdapter when push channel goes live.
        log.info("PUSH dispatch (stubbed): recipientId={} title={}", recipientId, title);
    }
}
