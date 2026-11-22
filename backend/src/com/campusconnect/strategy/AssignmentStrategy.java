package com.campusconnect.strategy;

import com.campusconnect.model.Complaint;

public interface AssignmentStrategy {
    String selectStaff(Complaint complaint);
}