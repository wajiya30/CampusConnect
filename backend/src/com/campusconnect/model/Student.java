package com.campusconnect.model;

public class Student extends User {
    private String registrationNumber;

    public Student(String userId, String name, String email, String registrationNumber) {
        super(userId, name, email, "STUDENT");
        this.registrationNumber = registrationNumber;
    }

    @Override
    public String getDashboardPermissions() {
        return "SUBMIT_COMPLAINT, VIEW_OWN_COMPLAINTS, TRACK_STATUS";
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }
}