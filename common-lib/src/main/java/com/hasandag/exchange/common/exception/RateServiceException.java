package com.hasandag.exchange.common.exception;

public class RateServiceException extends RuntimeException {

    public RateServiceException(String message) {
        super(message);
    }

    public RateServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}