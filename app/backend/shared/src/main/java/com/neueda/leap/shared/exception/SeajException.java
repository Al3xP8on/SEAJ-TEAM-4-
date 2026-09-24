package com.neueda.leap.shared.exception;

/**
 * Base exception for all SEAJ application errors
 */
public class SeajException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public SeajException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public SeajException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
