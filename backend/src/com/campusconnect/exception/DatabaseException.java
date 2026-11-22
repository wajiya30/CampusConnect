package com.campusconnect.exception;

/** The database is unreachable or a query failed. Mapped to HTTP 503. */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
