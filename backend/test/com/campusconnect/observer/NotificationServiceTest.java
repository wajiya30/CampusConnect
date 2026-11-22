package com.campusconnect.observer;

import com.campusconnect.model.Complaint;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {

    @Test
    void newestNotificationIsFirst() {
        NotificationService service = new NotificationService();
        Complaint c = new Complaint("CMP-1", "Leak", "Electrical", "Block A", "High", "desc", "Student (1)");

        service.onStatusChanged(c, "Pending", "Assigned");
        service.onStatusChanged(c, "Assigned", "In Progress");

        var list = service.getNotificationsFor(Set.of("CMP-1"));
        assertEquals(2, list.size());
        assertTrue(list.get(0).contains("from Assigned to In Progress"));
    }

    @Test
    void usersOnlySeeNotificationsForComplaintsTheyCanAccess() {
        NotificationService service = new NotificationService();
        Complaint c1 = new Complaint("CMP-1", "Leak", "Electrical", "Block A", "High", "desc", "Student (1)");
        Complaint c2 = new Complaint("CMP-2", "Stool", "Laboratory", "Lab 1", "Low", "desc", "Student (2)");

        service.onStatusChanged(c1, "Pending", "Assigned");
        service.onStatusChanged(c2, "Pending", "Assigned");

        var visible = service.getNotificationsFor(Set.of("CMP-1"));
        assertEquals(1, visible.size());
        assertTrue(visible.get(0).startsWith("Complaint CMP-1"));
    }

    @Test
    void emptyAccessSetReturnsNothing() {
        NotificationService service = new NotificationService();
        Complaint c = new Complaint("CMP-1", "Leak", "Electrical", "Block A", "High", "desc", "Student (1)");
        service.onStatusChanged(c, "Pending", "Assigned");

        assertTrue(service.getNotificationsFor(Set.of()).isEmpty());
    }
}
