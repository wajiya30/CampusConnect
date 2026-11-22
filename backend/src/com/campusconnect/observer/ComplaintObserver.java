package com.campusconnect.observer;

import com.campusconnect.model.Complaint;

public interface ComplaintObserver {
    void onStatusChanged(Complaint complaint, String oldStatus, String newStatus);
}