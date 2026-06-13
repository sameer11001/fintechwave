package com.fintechwave.iam.exception;

import com.fintechwave.core.exception.ResourceNotFoundException;

public class KycNotFoundException extends ResourceNotFoundException {

    public KycNotFoundException(String tier) {
        super("Invalid KYC tier value: '" + tier + "'");
    }

}
