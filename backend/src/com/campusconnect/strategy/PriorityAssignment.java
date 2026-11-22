package com.campusconnect.strategy;

import com.campusconnect.model.Complaint;

public class PriorityAssignment implements AssignmentStrategy {

    @Override
    public String selectStaff(Complaint complaint) {
        String priority = complaint.getPriority() == null ? "Medium" : complaint.getPriority();
        if ("High".equalsIgnoreCase(priority)) {
            return "Senior Rapid Response Team";
        } else if ("Low".equalsIgnoreCase(priority)) {
            return "Scheduled Maintenance Crew";
        }
        return "Bilal Ahmed (Standard Dispatch)";
    }
}