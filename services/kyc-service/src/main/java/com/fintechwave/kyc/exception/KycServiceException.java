package com.fintechwave.kyc.exception;

import com.fintechwave.core.exception.BaseServiceException;
import org.springframework.http.HttpStatus;

public abstract class KycServiceException extends BaseServiceException {

    protected KycServiceException(String message, HttpStatus status) {
        super(status, deriveErrorCode(status), message);
    }

    protected KycServiceException(String message, HttpStatus status, String errorCode) {
        super(status, errorCode, message);
    }

    protected KycServiceException(String message, HttpStatus status, Throwable cause) {
        super(status, deriveErrorCode(status), message, cause);
    }

    private static String deriveErrorCode(HttpStatus status) {
        return status.name().replace(' ', '_');
    }

    @Deprecated(since = "1.1.0", forRemoval = true)
    public HttpStatus getHttpStatus() {
        return getStatus();
    }
}
