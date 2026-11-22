package com.campusconnect.strategy;

import com.campusconnect.model.Complaint;

public class CategoryAssignment implements AssignmentStrategy {

    @Override
    public String selectStaff(Complaint complaint) {
        String category = complaint.getCategory() == null ? "" : complaint.getCategory().toLowerCase();
        switch (category) {
            case "electrical":
                return "Tariq Mehmood (Electrical)";
            case "laboratory":
                return "Kamran Raza (Lab Maint.)";
            case "cleanliness":
                return "Sanitation Team A";
            default:
                return "Bilal Ahmed (General Facility)";
        }
    }
}