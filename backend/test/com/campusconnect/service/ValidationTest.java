package com.campusconnect.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    @Test
    void submissionRejectsTooLongTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> ComplaintService.validateSubmission("x".repeat(201), "Electrical", null, "desc"));
    }

    @Test
    void submissionRejectsTooLongDescription() {
        assertThrows(IllegalArgumentException.class,
                () -> ComplaintService.validateSubmission("Leak", "Electrical", null, "x".repeat(2001)));
    }

    @Test
    void remarksAboveLimitAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ComplaintService.validateRemarks("x".repeat(251)));
    }

    @Test
    void missingRemarksAreAccepted() {
        assertDoesNotThrow(() -> ComplaintService.validateRemarks(null));
    }

    @Test
    void registrationRejectsNonStudentRoles() {
        assertThrows(SecurityException.class,
                () -> AuthService.validateRegistration("ADMIN", "ADM-9", "Name", "a@b.edu", "secret1", "IT"));
    }

    @Test
    void registrationRejectsInvalidEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthService.validateRegistration("STUDENT", "12345", "Name", "not-an-email", "secret1", "12345"));
    }

    @Test
    void registrationRejectsShortPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthService.validateRegistration("STUDENT", "12345", "Name", "a@b.edu", "abc", "12345"));
    }

    @Test
    void registrationRejectsUnsafeUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthService.validateRegistration("STUDENT", "12 3;DROP", "Name", "a@b.edu", "secret1", "12345"));
    }

    @Test
    void registrationAcceptsValidStudent() {
        assertDoesNotThrow(() ->
                AuthService.validateRegistration("STUDENT", "12345", "Name", "a@b.edu", "secret1", "12345"));
    }
}
