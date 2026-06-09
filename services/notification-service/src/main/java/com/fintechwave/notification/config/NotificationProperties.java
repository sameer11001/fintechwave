package com.fintechwave.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private String fromEmail = "noreply@fintechwave.com";

    private String fromName = "FintechWave";

    /**
     * Comma-separated list of active delivery channels.
     * Supported values: EMAIL, SMS, PUSH.
     */
    private String enabledChannels = "EMAIL";

    private int retentionDays = 90;

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getEnabledChannels() {
        return enabledChannels;
    }

    public void setEnabledChannels(String enabledChannels) {
        this.enabledChannels = enabledChannels;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
