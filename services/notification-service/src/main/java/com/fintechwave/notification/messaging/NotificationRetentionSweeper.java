package com.fintechwave.notification.messaging;

import com.fintechwave.notification.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled retention sweeper — purges notification records older than retention-days.
 * Runs daily at 02:00 UTC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetentionSweeper {

    private final INotificationService notificationService;

    @Value("${app.notification.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 2 * * *")
    public void sweep() {
        log.info("Notification retention sweep started: purging records older than {} days", retentionDays);
        int deleted = notificationService.purgeOldNotifications(retentionDays);
        log.info("Notification retention sweep complete: {} records deleted", deleted);
    }
}
