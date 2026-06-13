package com.fintechwave.ledger.domain.enums;

public enum AccountType {
    ASSET    (EntryType.DEBIT),
    LIABILITY(EntryType.CREDIT),
    EQUITY   (EntryType.CREDIT),
    REVENUE  (EntryType.CREDIT),
    EXPENSE  (EntryType.DEBIT);

    private final EntryType normalBalance;

    AccountType(EntryType normalBalance) {
        this.normalBalance = normalBalance;
    }

    public EntryType getNormalBalance() { 
        return normalBalance; 
    }
}