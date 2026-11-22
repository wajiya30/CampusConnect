package com.campusconnect.exception;

import com.google.gson.JsonParseException;

import java.util.NoSuchElementException;
import java.util.UUID;

public final class ErrorMapper {

    private ErrorMapper() {}

    public static int statusFor(Throwable t) {
        if (t instanceof ApiException a) return a.getStatus();
        if (t instanceof DatabaseException) return 503;
        if (t instanceof SecurityException) return 403;
        if (t instanceof NoSuchElementException) return 404;
        if (t instanceof IllegalStateException) return 409;
        if (t instanceof IllegalArgumentException || t instanceof JsonParseException) return 400;
        return 500;
    }

    /** The message the client sees. Internal details are never returned for 5xx errors. */
    public static String messageFor(Throwable t, String referenceId) {
        int status = statusFor(t);
        if (status == 503) {
            return "The database is temporarily unavailable. Please try again in a moment.";
        }
        if (status == 500) {
            return "Something went wrong on the server. Reference: " + referenceId;
        }
        if (t instanceof JsonParseException) {
            return "The request body is not valid JSON.";
        }
        return t.getMessage() == null ? "Request failed." : t.getMessage();
    }

    public static String newReferenceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
