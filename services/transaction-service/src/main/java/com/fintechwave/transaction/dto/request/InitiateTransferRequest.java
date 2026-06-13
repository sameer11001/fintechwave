package com.fintechwave.transaction.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiateTransferRequest(
                @NotNull(message = "Receiver ID is required") UUID receiverId,

                @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be greater than 0") @Digits(integer = 15, fraction = 4) BigDecimal amount,

                @NotBlank(message = "Currency is required") @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code") String currency,

                @Pattern(regexp = "^[^<>]*$", message = "Description contains invalid HTML characters") @Size(max = 500) String description,

                /**
                 * Client-generated idempotency key.
                 * Prevents duplicate submission on network retry.
                 */
                @NotNull(message = "Idempotency key is required") UUID idempotencyKey) {
}
