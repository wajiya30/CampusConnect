package com.campusconnect.observer;

import com.campusconnect.model.Complaint;
import com.campusconnect.repository.NotificationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer that records status-change alerts. Uses the database when given a repository,
 * and memory otherwise (unit tests). A failure to save an alert is logged and never
 * blocks the status change that caused it.
 */
public class NotificationService implements ComplaintObserver {

    private record Entry(String complaintId, String message) {}

    private final NotificationRepository repo;
    private final List<Entry> memory = new CopyOnWriteArrayList<>();

    /** Database-backed. Used by the server. */
    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /** In-memory. Used by unit tests. */
    public NotificationService() {
        this.repo = null;
    }

    @Override
    public void onStatusChanged(Complaint complaint, String oldStatus, String newStatus) {
        String id = complaint.getComplaintId();
        String msg = String.format("Complaint %s (%s) changed from %s to %s.",
                id, complaint.getTitle(), oldStatus, newStatus);
        try {
            if (repo != null) {
                repo.save(id, msg);
            } else {
                memory.add(0, new Entry(id, msg));
            }
        } catch (RuntimeException e) {
            System.err.println("Notification for " + id + " was not saved: " + e.getMessage());
        }
    }

    /** Messages about complaints in the given set, newest first. */
    public List<String> getNotificationsFor(Set<String> visibleComplaintIds) {
        if (visibleComplaintIds.isEmpty()) return new ArrayList<>();
        if (repo != null) return repo.findForComplaints(visibleComplaintIds);

        List<String> out = new ArrayList<>();
        for (Entry e : memory) {
            if (visibleComplaintIds.contains(e.complaintId())) out.add(e.message());
        }
        return out;
    }
}
