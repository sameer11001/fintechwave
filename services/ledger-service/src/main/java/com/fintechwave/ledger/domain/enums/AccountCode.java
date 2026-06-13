package com.fintechwave.ledger.domain.enums;

import java.util.Arrays;

public enum AccountCode {

    // Assets
    PLATFORM_FLOAT   ("1000", AccountType.ASSET),
    STRIPE_ESCROW    ("1001", AccountType.ASSET),
    // Liabilities
    USER_WALLET      ("2000", AccountType.LIABILITY),
    SUSPENSE         ("2001", AccountType.LIABILITY),

    // Revenue
    P2P_FEE_REVENUE      ("3000", AccountType.REVENUE),
    CASHOUT_FEE_REVENUE  ("3001", AccountType.REVENUE),
    BILLPAY_FEE_REVENUE  ("3002", AccountType.REVENUE),

    // Expenses
    STRIPE_PROCESSING_COST   ("4000", AccountType.EXPENSE),
    AGGREGATOR_COST          ("4001", AccountType.EXPENSE);

    private final String      code;
    private final AccountType type;

    AccountCode(String code, AccountType type) {
        this.code = code;
        this.type = type;
    }

    public String      getCode() { return code; }
    public AccountType getType() { return type; }

    public static AccountCode fromCode(String code) {
        return Arrays.stream(values())
            .filter(ac -> ac.code.equals(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown account code: " + code));
    }
}
