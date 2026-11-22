package com.campusconnect.service;

import com.campusconnect.exception.ApiException;
import com.campusconnect.factory.UserFactory;
import com.campusconnect.model.User;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.util.PasswordUtil;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    private final UserRepository userRepo;
    private final Map<String, String> tokensToUsers = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public Optional<String> login(String userId, String password) {
        if (isBlank(userId) || isBlank(password)) {
            return Optional.empty();
        }
        String normalizedId = userId.trim();
        Optional<String> storedHash = userRepo.findPasswordHash(normalizedId);
        if (storedHash.isEmpty()) {
            return Optional.empty();
        }
        if (!PasswordUtil.verify(password, storedHash.get())) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        tokensToUsers.put(token, normalizedId);
        return Optional.of(token);
    }

    public Optional<User> userForToken(String token) {
        if (isBlank(token)) {
            return Optional.empty();
        }
        String userId = tokensToUsers.get(token.trim());
        if (userId == null) {
            return Optional.empty();
        }
        return userRepo.findById(userId);
    }

    public void logout(String token) {
        if (!isBlank(token)) {
            tokensToUsers.remove(token.trim());
        }
    }

    public User register(String role, String userId, String name, String email,
                         String password, String extraInfo) {
        if (isBlank(userId) || isBlank(name) || isBlank(email) || isBlank(password)) {
            throw new ApiException(400, "User ID, name, email, and password are required.");
        }
        String normalizedId = userId.trim();
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepo.findById(normalizedId).isPresent()) {
            throw new ApiException(409, "A user with that ID already exists.");
        }
        if (userRepo.existsByEmail(normalizedEmail)) {
            throw new ApiException(409, "A user with that email already exists.");
        }

        String effectiveRole = isBlank(role) ? "STUDENT" : role.trim();
        User user = UserFactory.createUser(effectiveRole, normalizedId, name.trim(), normalizedEmail, extraInfo);
        userRepo.save(user, PasswordUtil.hash(password));
        return user;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static void validateRegistration(String role, String userId, String name, String email,
                                           String password, String extraInfo) {
        if (role != null && !role.isBlank() && !"STUDENT".equalsIgnoreCase(role.trim())) {
            throw new SecurityException("Only STUDENT registration is allowed.");
        }
        if (userId == null || !userId.matches("^[A-Za-z0-9_-]{1,50}$")) {
            throw new IllegalArgumentException("User ID contains invalid characters.");
        }
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Invalid email address.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
    }
}