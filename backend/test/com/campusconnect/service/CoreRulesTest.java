package com.campusconnect.service;

import com.campusconnect.factory.UserFactory;
import com.campusconnect.model.Complaint;
import com.campusconnect.model.User;
import com.campusconnect.strategy.CategoryAssignment;
import com.campusconnect.strategy.PriorityAssignment;
import com.campusconnect.util.PasswordUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the core business rules. No database or server is needed. */
class CoreRulesTest {

    private final User student = UserFactory.createUser("STUDENT", "59806", "Tayyaba Iqbal", "t@campus.edu", "59806");
    private final User otherStudent = UserFactory.createUser("STUDENT", "58908", "Wajeeha Aqdas", "w@campus.edu", "58908");
    private final User admin = UserFactory.createUser("ADMIN", "ADM-001", "Campus Admin", "a@campus.edu", "Admin");
    private final User bilal = UserFactory.createUser("STAFF", "STF-001", "Bilal Ahmed", "b@campus.edu", "HVAC");
    private final User kamran = UserFactory.createUser("STAFF", "STF-002", "Kamran Raza", "k@campus.edu", "Laboratory");

    private Complaint assignedTo(String assignee) {
        Complaint c = new Complaint("CMP-1", "Leak", "Electrical", "Block A", "High", "desc",
                "Tayyaba Iqbal (59806)");
        c.setAssignedStaff(assignee);
        return c;
    }

    // ----- Password security -----

    @Test
    void correctPasswordIsAccepted() {
        String stored = PasswordUtil.hash("password123");
        assertTrue(PasswordUtil.verify("password123", stored));
    }

    @Test
    void wrongPasswordIsRejected() {
        String stored = PasswordUtil.hash("password123");
        assertFalse(PasswordUtil.verify("wrong-password", stored));
    }

    @Test
    void sameVisiblePasswordGetsDifferentStoredHashes() {
        // A random salt means two users with the same password never share a stored value.
        assertNotEquals(PasswordUtil.hash("password123"), PasswordUtil.hash("password123"));
    }

    // ----- Role creation (Factory pattern) -----

    @Test
    void factoryCreatesStudentForStudentRole() {
        assertEquals("STUDENT", student.getRole());
        assertEquals("59806", student.getUserId());
    }

    @Test
    void factoryRejectsUnknownRole() {
        assertThrows(IllegalArgumentException.class,
                () -> UserFactory.createUser("GUEST", "1", "Name", "e@campus.edu", "x"));
    }

    // ----- Staff assignment (Strategy pattern) -----

    @Test
    void categoryStrategyRoutesElectricalComplaintsToElectrician() {
        Complaint c = new Complaint("CMP-2", "Fan", "Electrical", "Lab 1", "Medium", "desc", "Student (1)");
        assertEquals("Tariq Mehmood (Electrical)", new CategoryAssignment().selectStaff(c));
    }

    @Test
    void priorityStrategySendsHighPriorityToRapidResponse() {
        Complaint c = new Complaint("CMP-3", "Smoke", "Electrical", "Lab 1", "High", "desc", "Student (1)");
        assertEquals("Senior Rapid Response Team", new PriorityAssignment().selectStaff(c));
    }

    @Test
    void suggestedTeamIsMatchedToTheNamedStaffMember() {
        Optional<User> match = ComplaintService.matchStaff(
                "Kamran Raza (Laboratory)", List.of(bilal, kamran));
        assertTrue(match.isPresent());
        assertEquals("STF-002", match.get().getUserId());
    }

    @Test
    void teamNameWithNoStaffAccountMatchesNobody() {
        assertTrue(ComplaintService.matchStaff("Senior Rapid Response Team", List.of(bilal, kamran)).isEmpty());
    }

    // ----- Status workflow -----

    @Test
    void pendingComplaintCanOnlyMoveThroughAssign() {
        // Only the Assign action can move a Pending complaint. A direct status change is refused.
        assertFalse(ComplaintService.isValidTransition("Pending", "Assigned"));
        assertFalse(ComplaintService.isValidTransition("Pending", "Resolved"));
    }

    @Test
    void assignedComplaintCanMoveToInProgressOrResolved() {
        assertTrue(ComplaintService.isValidTransition("Assigned", "In Progress"));
        assertTrue(ComplaintService.isValidTransition("Assigned", "Resolved"));
    }

    @Test
    void resolvedIsFinal() {
        assertFalse(ComplaintService.isValidTransition("Resolved", "In Progress"));
        assertFalse(ComplaintService.isValidTransition("Resolved", "Assigned"));
    }

    // ----- Access control -----

    @Test
    void studentSeesOnlyOwnComplaints() {
        Complaint c = assignedTo("Bilal Ahmed (HVAC)");
        assertTrue(ComplaintService.canView(student, c));
        assertFalse(ComplaintService.canView(otherStudent, c));
    }

    @Test
    void staffSeesOnlyComplaintsAssignedToThem() {
        Complaint c = assignedTo("Bilal Ahmed (HVAC)");
        assertTrue(ComplaintService.canView(bilal, c));
        assertFalse(ComplaintService.canView(kamran, c));
    }

    @Test
    void onlyAdminOrAssignedStaffCanUpdateStatus() {
        Complaint c = assignedTo("Bilal Ahmed (HVAC)");
        assertTrue(ComplaintService.canUpdateStatus(admin, c));
        assertTrue(ComplaintService.canUpdateStatus(bilal, c));
        assertFalse(ComplaintService.canUpdateStatus(kamran, c));
        assertFalse(ComplaintService.canUpdateStatus(student, c));
    }

    @Test
    void onlyAssignedComplaintsCanBeAcceptedOrRejected() {
        assertTrue(ComplaintService.canAccept("Assigned"));
        assertTrue(ComplaintService.canReject("Assigned"));
        assertFalse(ComplaintService.canAccept("Pending"));
        assertFalse(ComplaintService.canReject("In Progress"));
    }
}