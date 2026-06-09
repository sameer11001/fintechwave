package com.fintechwave.notification.service;

import com.fintechwave.notification.domain.enums.NotificationChannel;

import java.util.UUID;

public interface INotificationService {

    /**
     * Send a notification to a recipient.
     * Idempotent: duplicate idempotencyKey is silently discarded.
     *
     * @param idempotencyKey unique key from the originating domain event
     * @param recipientId    Keycloak user UUID
     * @param channel        delivery channel (EMAIL, SMS, PUSH)
     * @param templateCode   template identifier (e.g. WALLET_PROVISIONED)
     * @param subject        message subject (nullable for SMS/Push)
     * @param body           rendered message body
     */
    void send(UUID idempotencyKey, UUID recipientId, NotificationChannel channel,
              String templateCode, String subject, String body);

    /**
     * Purge notifications older than retention days (called by scheduled sweeper).
     */
    int purgeOldNotifications(int retentionDays);
}
