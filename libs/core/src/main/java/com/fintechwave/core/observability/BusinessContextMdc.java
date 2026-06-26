package com.fintechwave.core.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class BusinessContextMdc implements AutoCloseable {

    private static final String USER_ID = "user_id";
    private static final String TRANSACTION_ID = "transaction_id";
    private static final String EVENT_TYPE = "event_type";

    private BusinessContextMdc() {
    }

    public static BusinessContextMdc of(UUID userId, UUID transactionId, String eventType) {
        if (userId != null)
            MDC.put(USER_ID, userId.toString());
        if (transactionId != null)
            MDC.put(TRANSACTION_ID, transactionId.toString());
        if (eventType != null)
            MDC.put(EVENT_TYPE, eventType);
        return new BusinessContextMdc();
    }

    @Override
    public void close() {
        MDC.remove(USER_ID);
        MDC.remove(TRANSACTION_ID);
        MDC.remove(EVENT_TYPE);
    }
}
