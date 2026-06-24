package com.fintechwave.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "app.fraud")
public class FraudProperties {

    private final Window window = new Window();
    private final Threshold threshold = new Threshold();

    public Window getWindow() {
        return window;
    }

    public Threshold getThreshold() {
        return threshold;
    }

    /**
     * Velocity window durations in seconds.
     */
    public static class Window {
        /** TX count sliding window (60 s). */
        private int txCount60s = 60;
        /** TX volume sliding window (1 h). */
        private int txVolume1h = 3600;
        /** TX volume sliding window (24 h). */
        private int txVolume24h = 86400;
        /** Auth-failure sliding window (15 m). */
        private int authFail15m = 900;

        public int getTxCount60s() {
            return txCount60s;
        }

        public void setTxCount60s(int v) {
            this.txCount60s = v;
        }

        public int getTxVolume1h() {
            return txVolume1h;
        }

        public void setTxVolume1h(int v) {
            this.txVolume1h = v;
        }

        public int getTxVolume24h() {
            return txVolume24h;
        }

        public void setTxVolume24h(int v) {
            this.txVolume24h = v;
        }

        public int getAuthFail15m() {
            return authFail15m;
        }

        public void setAuthFail15m(int v) {
            this.authFail15m = v;
        }
    }

    /**
     * Velocity thresholds mapped to decision rules.
     */
    public static class Threshold {
        /** Max transaction count per 60-second window. */
        private int maxTxCount60s = 10;
        /** Max transaction volume per 1-hour window. */
        private BigDecimal maxTxVolume1h = new BigDecimal("500.00");
        /** Max transaction volume per 24-hour window. */
        private BigDecimal maxTxVolume24h = new BigDecimal("2000.00");
        /** Max authentication failures per 15-minute window. */
        private int maxAuthFail15m = 5;

        public int getMaxTxCount60s() {
            return maxTxCount60s;
        }

        public void setMaxTxCount60s(int v) {
            this.maxTxCount60s = v;
        }

        public BigDecimal getMaxTxVolume1h() {
            return maxTxVolume1h;
        }

        public void setMaxTxVolume1h(BigDecimal v) {
            this.maxTxVolume1h = v;
        }

        public BigDecimal getMaxTxVolume24h() {
            return maxTxVolume24h;
        }

        public void setMaxTxVolume24h(BigDecimal v) {
            this.maxTxVolume24h = v;
        }

        public int getMaxAuthFail15m() {
            return maxAuthFail15m;
        }

        public void setMaxAuthFail15m(int v) {
            this.maxAuthFail15m = v;
        }
    }
}
