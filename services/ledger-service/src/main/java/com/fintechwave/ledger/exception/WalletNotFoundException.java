package com.fintechwave.ledger.exception;

import com.fintechwave.core.exception.ResourceNotFoundException;

import java.util.UUID;

public class WalletNotFoundException extends ResourceNotFoundException {
    public WalletNotFoundException(UUID id) {
        super("Wallet or account not found: " + id);
    }
}