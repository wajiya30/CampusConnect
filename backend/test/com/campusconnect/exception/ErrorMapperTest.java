package com.campusconnect.exception;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ErrorMapperTest {

    @Test
    void databaseErrorsAreServiceUnavailable() {
        assertEquals(503, ErrorMapper.statusFor(new DatabaseException("down", null)));
    }

    @Test
    void forbiddenActionsMapTo403() {
        assertEquals(403, ErrorMapper.statusFor(new SecurityException("not yours")));
    }

    @Test
    void missingOrHiddenItemsMapTo404() {
        assertEquals(404, ErrorMapper.statusFor(new NoSuchElementException("Complaint CMP-9 not found.")));
    }

    @Test
    void invalidStateMapsTo409() {
        assertEquals(409, ErrorMapper.statusFor(new IllegalStateException("Cannot change status.")));
    }

    @Test
    void badInputMapsTo400() {
        assertEquals(400, ErrorMapper.statusFor(new IllegalArgumentException("Title is required.")));
    }

    @Test
    void malformedJsonMapsTo400() {
        assertEquals(400, ErrorMapper.statusFor(new JsonParseException("bad token")));
    }

    @Test
    void apiExceptionKeepsItsOwnStatus() {
        assertEquals(413, ErrorMapper.statusFor(new ApiException(413, "too big")));
    }

    @Test
    void unknownErrorsAreInternalAndHideTheirDetails() {
        RuntimeException secret = new RuntimeException("SELECT password_hash FROM users");
        assertEquals(500, ErrorMapper.statusFor(secret));

        String message = ErrorMapper.messageFor(secret, "abc12345");
        assertFalse(message.contains("SELECT"));
        assertTrue(message.contains("abc12345"));
    }
}
