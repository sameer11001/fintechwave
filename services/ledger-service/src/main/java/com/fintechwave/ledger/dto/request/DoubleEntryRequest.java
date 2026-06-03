package com.fintechwave.ledger.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DoubleEntryRequest(
        @NotNull UUID transactionId,
        @NotNull @Size(min = 2) List<EntryLine> entries
) {
    public record EntryLine(
            @NotNull UUID   accountId,
            @NotNull String entryType,
            @NotNull @Positive BigDecimal amount,
            @NotNull String currency,
            @NotNull UUID   idempotencyKey,
            String description
    ) {}
}
