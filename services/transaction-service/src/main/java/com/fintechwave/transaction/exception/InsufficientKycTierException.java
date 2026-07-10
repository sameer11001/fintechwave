package com.fintechwave.transaction.exception;

import com.fintechwave.core.exception.BaseServiceException;
import org.springframework.http.HttpStatus;

public class InsufficientKycTierException extends BaseServiceException {
    public InsufficientKycTierException(String message) {
        super(HttpStatus.FORBIDDEN, "KYC_TIER_INSUFFICIENT", message);
    }
}
