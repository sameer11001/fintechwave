package com.fintechwave.ledger.util;

import java.util.UUID;

public final class IdempotencyKeyFactory {

    private IdempotencyKeyFactory() {
    }

    public static UUID from(UUID transactionId, String role) {
        return UUID.nameUUIDFromBytes((transactionId.toString() + "-" + role).getBytes());
    }
}
