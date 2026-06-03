package com.fintechwave.payment;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null)    throw new IllegalArgumentException("amount must not be null");
        if (currency == null)  throw new IllegalArgumentException("currency must not be null");
        if (amount.signum() < 0) throw new IllegalArgumentException("amount must be >= 0");
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount.setScale(4, java.math.RoundingMode.HALF_UP),
                         Currency.getInstance(currencyCode));
    }

    public static Money ofMinorUnits(long minorUnits, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        int fractionDigits = currency.getDefaultFractionDigits();
        BigDecimal divisor = BigDecimal.TEN.pow(fractionDigits);
        return new Money(BigDecimal.valueOf(minorUnits).divide(divisor).setScale(4, java.math.RoundingMode.HALF_UP),
                         currency);
    }

    public long toMinorUnits() {
        int fractionDigits = currency.getDefaultFractionDigits();
        return amount.movePointRight(fractionDigits).longValueExact();
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }
}
