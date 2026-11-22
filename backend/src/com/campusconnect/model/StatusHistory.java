package com.campusconnect.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StatusHistory {
    private String complaintId;
    private String status;
    private String remarks;
    private String timestamp;

    public StatusHistory(String complaintId, String status, String remarks) {
        this.complaintId = complaintId;
        this.status = status;
        this.remarks = remarks;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getComplaintId() { return complaintId; }
    public String getStatus() { return status; }
    public String getRemarks() { return remarks; }
    public String getTimestamp() { return timestamp; }
}