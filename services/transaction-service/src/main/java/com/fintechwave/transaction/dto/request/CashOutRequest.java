package com.fintechwave.transaction.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CashOutRequest(
                @NotNull @DecimalMin(value = "1.00", message = "Minimum cash-out is 1.00") @Digits(integer = 15, fraction = 4) BigDecimal amount,
                @NotBlank @Size(min = 3, max = 3) String currency,
                @NotBlank(message = "Stripe payment method ID is required for cash-out") String stripePaymentMethodId,
                @NotNull UUID idempotencyKey) {
}
