package com.campusconnect.model;

public class Complaint {
    private String complaintId;
    private String title;
    private String category;
    private String location;
    private String priority;
    private String status;
    private String description;
    private String submittedBy;
    private String assignedStaff;

    public Complaint(String complaintId, String title, String category, String location,
                     String priority, String description, String submittedBy) {
        this.complaintId = complaintId;
        this.title = title;
        this.category = category;
        this.location = location;
        this.priority = (priority == null || priority.isBlank()) ? "Medium" : priority;
        this.status = "Pending";
        this.description = description;
        this.submittedBy = submittedBy;
        this.assignedStaff = "Unassigned";
    }

    public String getComplaintId() { return complaintId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public String getSubmittedBy() { return submittedBy; }
    public String getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(String assignedStaff) { this.assignedStaff = assignedStaff; }
}