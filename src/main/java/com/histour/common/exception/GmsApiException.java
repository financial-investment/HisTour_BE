package com.histour.common.exception;

public class GmsApiException extends RuntimeException {
    public GmsApiException(String message) {
        super(message);
    }
    public GmsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
