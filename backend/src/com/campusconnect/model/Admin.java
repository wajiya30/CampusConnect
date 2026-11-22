package com.campusconnect.model;

public class Admin extends User {
    private String department;

    public Admin(String userId, String name, String email, String department) {
        super(userId, name, email, "ADMIN");
        this.department = department;
    }

    @Override
    public String getDashboardPermissions() {
        return "VIEW_ALL, ASSIGN_STAFF, SET_PRIORITY, VIEW_REPORTS";
    }

    public String getDepartment() {
        return department;
    }
}