package com.campusconnect.model;

public abstract class User {
    protected String userId;
    protected String name;
    protected String email;
    protected String role;

    public User(String userId, String name, String email, String role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public abstract String getDashboardPermissions();

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}