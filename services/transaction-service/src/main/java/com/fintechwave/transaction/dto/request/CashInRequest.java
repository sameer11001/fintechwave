package com.fintechwave.transaction.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CashInRequest(
                @NotNull @DecimalMin(value = "1.00", message = "Minimum cash-in is 1.00") @Digits(integer = 15, fraction = 4) BigDecimal amount,

                @NotBlank @Size(min = 3, max = 3) String currency,

                /**
                 * Stripe PaymentMethod ID (pm_xxxx) retrieved from user-service.
                 * The transaction service does NOT store raw card data.
                 */
                @NotBlank(message = "Stripe payment method ID is required") String stripePaymentMethodId,

                @NotNull UUID idempotencyKey) {
}
