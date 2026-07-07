package com.fintechwave.notification.service.impl;

import com.fintechwave.notification.domain.enums.NotificationChannel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final JavaMailSender mailSender;

    @CircuitBreaker(name = "notificationSender", fallbackMethod = "fallbackDispatch")
    public void dispatch(NotificationChannel channel, UUID recipientId, String subject, String body) {
        switch (channel) {
            case EMAIL -> sendEmail(recipientId, subject, body);
            case SMS -> sendSms(recipientId, body);
            case PUSH -> sendPush(recipientId, subject, body);
        }
    }

    public void fallbackDispatch(NotificationChannel channel, UUID recipientId, String subject, String body, Throwable t) {
        log.error("Circuit breaker OPEN. Dispatch unavailable for channel={}. Error: {}", channel, t.getMessage());
        throw new RuntimeException("Notification dispatch unavailable (circuit open).", t);
    }

    private void sendEmail(UUID recipientId, String subject, String body) {
        log.debug("EMAIL dispatch: recipientId={} subject={}", recipientId, subject);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("user+" + recipientId + "@fintechwave.internal");
        message.setSubject(subject != null ? subject : "FintechWave Notification");
        message.setText(body);
        mailSender.send(message);
    }

    private void sendSms(UUID recipientId, String body) {
        log.info("SMS dispatch (stubbed): recipientId={} body_length={}", recipientId, body.length());
    }

    private void sendPush(UUID recipientId, String title, String body) {
        log.info("PUSH dispatch (stubbed): recipientId={} title={}", recipientId, title);
    }
}
