package com.campusconnect.factory;

import com.campusconnect.model.Admin;
import com.campusconnect.model.Staff;
import com.campusconnect.model.Student;
import com.campusconnect.model.User;

public class UserFactory {

    public static User createUser(String role, String userId, String name, String email, String extraInfo) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null.");
        }

        switch (role.trim().toUpperCase()) {
            case "STUDENT":
                return new Student(userId, name, email, extraInfo);
            case "ADMIN":
            case "ADMINISTRATOR":
                return new Admin(userId, name, email, extraInfo);
            case "STAFF":
            case "MAINTENANCE":
                return new Staff(userId, name, email, extraInfo);
            default:
                throw new IllegalArgumentException("Unsupported user role: " + role);
        }
    }
}