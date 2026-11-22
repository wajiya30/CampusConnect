package com.campusconnect.exception;

/** An error that carries its own HTTP status, such as 401 or 413. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
