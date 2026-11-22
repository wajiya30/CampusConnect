package com.campusconnect.model;

public class Staff extends User {
    private String specialization; // e.g., Electrical, Laboratory, Cleanliness

    public Staff(String userId, String name, String email, String specialization) {
        super(userId, name, email, "STAFF");
        this.specialization = specialization;
    }

    @Override
    public String getDashboardPermissions() {
        return "VIEW_ASSIGNED, ACCEPT_TASK, UPDATE_PROGRESS, MARK_RESOLVED";
    }

    public String getSpecialization() {
        return specialization;
    }
}