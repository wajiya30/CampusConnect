package com.campusconnect.service;

import com.campusconnect.model.Complaint;
import com.campusconnect.model.StatusHistory;
import com.campusconnect.model.User;
import com.campusconnect.observer.ComplaintObserver;
import com.campusconnect.observer.NotificationService;
import com.campusconnect.repository.ComplaintRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.strategy.AssignmentStrategy;
import com.campusconnect.strategy.CategoryAssignment;
import com.campusconnect.strategy.PriorityAssignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Business rules for the whole complaint lifecycle:
 *   Student submits  ->  Admin assigns  ->  Staff accept (or reject)  ->  Staff resolve
 * Rejected tasks return to the admin, who reassigns them.
 */
public class ComplaintService {

    public static final List<String> CATEGORIES = List.of("Electrical", "Classroom", "Laboratory", "Cleanliness", "General");
    public static final List<String> PRIORITIES = List.of("Low", "Medium", "High");
    public static final List<String> STATUSES = List.of("Pending", "Assigned", "In Progress", "Resolved");

    static final int MAX_TITLE = 200;
    static final int MAX_LOCATION = 150;
    static final int MAX_DESCRIPTION = 2000;
    static final int MAX_REMARKS = 250;

    private final ComplaintRepository repo;
    private final UserRepository users;
    private final Map<String, AssignmentStrategy> strategies = new LinkedHashMap<>();
    private final List<ComplaintObserver> observers = new ArrayList<>();

    public ComplaintService(ComplaintRepository repo, UserRepository users, NotificationService notifier) {
        this.repo = repo;
        this.users = users;
        this.strategies.put("category", new CategoryAssignment());
        this.strategies.put("priority", new PriorityAssignment());
        this.observers.add(notifier);
    }

    /** Throws if the database is unreachable. Used by the /health endpoint. */
    public void healthCheck() {
        repo.count();
    }

    // ===== Student =====

    public Complaint submit(User actor, String title, String category, String location,
                            String priority, String description) {
        requireRole(actor, "STUDENT", "Only students can submit complaints.");
        validateSubmission(title, category, priority, description);

        String loc = location == null ? "" : location.trim();
        if (loc.isEmpty()) throw new IllegalArgumentException("Location is required.");
        if (loc.length() > MAX_LOCATION) {
            throw new IllegalArgumentException("Location must be " + MAX_LOCATION + " characters or fewer.");
        }

        String cat = matchOption(CATEGORIES, category, "category");
        String pri = (priority == null || priority.isBlank()) ? null : matchOption(PRIORITIES, priority, "priority");

        Complaint c = new Complaint(
                repo.generateNextId(),
                title.trim(),
                cat,
                loc,
                pri,
                description.trim(),
                actor.getName() + " (" + actor.getUserId() + ")");
        repo.save(c);
        return c;
    }

    // ===== Read actions (scoped by role) =====

    public List<Complaint> listFor(User actor) {
        return repo.findAll().stream()
                .filter(c -> canView(actor, c))
                .collect(Collectors.toList());
    }

    public Complaint getFor(User actor, String complaintId) {
        return find(actor, complaintId);
    }

    public List<StatusHistory> historyFor(User actor, String complaintId) {
        find(actor, complaintId); // access check
        return repo.historyFor(complaintId);
    }

    /** Every history entry for complaints this user can see, newest first. */
    public List<StatusHistory> activityFor(User actor) {
        List<StatusHistory> all = new ArrayList<>();
        for (Complaint c : listFor(actor)) {
            all.addAll(repo.historyFor(c.getComplaintId()));
        }
        all.sort(Comparator.comparing(StatusHistory::getTimestamp).reversed());
        return all;
    }

    public Map<String, Integer> statsFor(User actor) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        List<Complaint> visible = listFor(actor);
        stats.put("total", visible.size());
        for (String s : STATUSES) {
            stats.put(s, (int) visible.stream().filter(c -> s.equals(c.getStatus())).count());
        }
        return stats;
    }

    /** Maintenance staff accounts, so the admin can choose one by name. */
    public List<User> listStaff(User actor) {
        requireRole(actor, "ADMIN", "Only administrators can view the staff list.");
        return users.findByRole("STAFF");
    }

    // ===== Admin: assignment =====

    /**
     * Assigns a complaint to a specific staff member (staffUserId), or to whoever a strategy picks
     * (strategy = "category" or "priority"). Either way the complaint becomes Assigned and waits
     * for the staff member to accept it.
     */
    public Complaint assign(User actor, String complaintId, String strategyKey, String staffUserId) {
        requireRole(actor, "ADMIN", "Only administrators can assign complaints.");
        Complaint c = find(actor, complaintId);
        if ("Resolved".equals(c.getStatus())) {
            throw new IllegalStateException("Resolved complaints cannot be reassigned.");
        }

        User staff;
        String how;
        if (staffUserId != null && !staffUserId.isBlank()) {
            staff = staffById(staffUserId.trim());
            how = "chosen by " + actor.getName();
        } else {
            String key = (strategyKey == null || strategyKey.isBlank()) ? "category" : strategyKey.trim().toLowerCase();
            AssignmentStrategy strategy = strategies.get(key);
            if (strategy == null) {
                throw new IllegalArgumentException("Unknown strategy '" + strategyKey
                        + "'. Use 'category' or 'priority', or choose a staff member.");
            }
            String suggestion = strategy.selectStaff(c);
            staff = matchStaff(suggestion, users.findByRole("STAFF")).orElseThrow(() ->
                    new IllegalStateException("The " + key + " strategy suggested \"" + suggestion
                            + "\", which matches no staff account. Choose a staff member instead."));
            how = "by " + key + " strategy";
        }

        String oldStatus = c.getStatus();
        String assignee = staff.getName() + " (" + staff.getUserId() + ")";
        c.setAssignedStaff(assignee);
        c.setStatus("Assigned");
        recordChange(c, oldStatus,
                "Assigned to " + assignee + " " + how + ", by " + actor.getName() + ". Waiting for staff to accept.");
        return c;
    }

    public Complaint setPriority(User actor, String complaintId, String priority) {
        requireRole(actor, "ADMIN", "Only administrators can set priority.");
        Complaint c = find(actor, complaintId);
        String target = matchOption(PRIORITIES, priority, "priority");
        String old = c.getPriority();
        if (target.equals(old)) {
            throw new IllegalStateException("Priority is already " + target + ".");
        }
        c.setPriority(target);
        repo.applyChange(c, new StatusHistory(c.getComplaintId(), c.getStatus(),
                "Priority changed from " + old + " to " + target + " by " + actor.getName()));
        return c;
    }

    // ===== Staff: accept, reject, progress =====

    /** The assigned staff member accepts an Assigned task. It moves to In Progress. */
    public Complaint acceptTask(User actor, String complaintId) {
        requireRole(actor, "STAFF", "Only maintenance staff can accept tasks.");
        Complaint c = find(actor, complaintId);
        if (!isAssignedTo(actor, c)) {
            throw new SecurityException("This complaint is not assigned to you.");
        }
        if (!canAccept(c.getStatus())) {
            throw new IllegalStateException("Only Assigned complaints can be accepted. This one is " + c.getStatus() + ".");
        }
        String oldStatus = c.getStatus();
        c.setStatus("In Progress");
        recordChange(c, oldStatus, "Task accepted by " + actor.getName());
        return c;
    }

    /**
     * The assigned staff member rejects an Assigned task with a required reason. It goes back to
     * Pending and is unassigned. The admin is notified and sees the reason in the complaint window.
     */
    public Complaint rejectTask(User actor, String complaintId, String reason) {
        requireRole(actor, "STAFF", "Only maintenance staff can reject tasks.");
        Complaint c = find(actor, complaintId);
        if (!isAssignedTo(actor, c)) {
            throw new SecurityException("This complaint is not assigned to you.");
        }
        if (!canReject(c.getStatus())) {
            throw new IllegalStateException("Only Assigned complaints can be rejected. This one is " + c.getStatus() + ".");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when rejecting a task.");
        }
        validateRemarks(reason);

        String oldStatus = c.getStatus();
        String previous = c.getAssignedStaff();
        c.setStatus("Pending");
        c.setAssignedStaff("Unassigned");
        recordChange(c, oldStatus,
                "Rejected by " + actor.getName() + " (was " + previous + "): " + reason.trim());
        return c;
    }

    /** Status updates. Admins can move any complaint along the valid steps. Staff can update only their own In Progress task. */
    public Complaint updateStatus(User actor, String complaintId, String newStatus, String remarks) {
        Complaint c = find(actor, complaintId);
        if (!canUpdateStatus(actor, c)) {
            throw new SecurityException("You are not allowed to update this complaint.");
        }
        String target = matchOption(STATUSES, newStatus, "status");
        if ("Assigned".equals(target)) {
            throw new IllegalStateException("Use Assign to give this complaint to a staff member.");
        }
        validateRemarks(remarks);

        String oldStatus = c.getStatus();
        if (!isValidTransition(oldStatus, target)) {
            throw new IllegalStateException("Cannot change status from " + oldStatus + " to " + target + ".");
        }
        if ("STAFF".equals(actor.getRole()) && !"In Progress".equals(oldStatus)) {
            throw new IllegalStateException("Accept the task before updating its progress.");
        }
        c.setStatus(target);
        String note = (remarks == null || remarks.isBlank())
                ? "Status updated by " + actor.getName()
                : remarks.trim();
        recordChange(c, oldStatus, note);
        return c;
    }

    // ===== Persistence and notifications =====

    /** Saves the change with its history entry in one transaction, then notifies observers. */
    private void recordChange(Complaint c, String oldStatus, String remarks) {
        repo.applyChange(c, new StatusHistory(c.getComplaintId(), c.getStatus(), remarks));
        if (!oldStatus.equals(c.getStatus())) {
            notifyObservers(c, oldStatus);
        }
    }

    private void notifyObservers(Complaint c, String oldStatus) {
        for (ComplaintObserver o : observers) {
            try {
                o.onStatusChanged(c, oldStatus, c.getStatus());
            } catch (RuntimeException e) {
                System.err.println("Observer failed for " + c.getComplaintId() + ": " + e.getMessage());
            }
        }
    }

    /** Returns the complaint only if the user can see it. Otherwise it looks like it doesn't exist. */
    private Complaint find(User actor, String complaintId) {
        return repo.findById(complaintId)
                .filter(c -> canView(actor, c))
                .orElseThrow(() -> new NoSuchElementException("Complaint " + complaintId + " not found."));
    }

    private User staffById(String userId) {
        return users.findById(userId)
                .filter(u -> "STAFF".equals(u.getRole()))
                .orElseThrow(() -> new IllegalArgumentException("No maintenance staff member has the ID " + userId + "."));
    }

    // ===== Static rules (pure logic, unit tested without a database) =====

    /** Finds the staff member a strategy's suggestion refers to, by name. */
    public static Optional<User> matchStaff(String suggestion, List<User> staff) {
        if (suggestion == null) return Optional.empty();
        return staff.stream().filter(u -> suggestion.contains(u.getName())).findFirst();
    }

    public static void validateSubmission(String title, String category, String priority, String description) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required.");
        if (title.trim().length() > MAX_TITLE) {
            throw new IllegalArgumentException("Title must be " + MAX_TITLE + " characters or fewer.");
        }
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Description is required.");
        if (description.trim().length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("Description must be " + MAX_DESCRIPTION + " characters or fewer.");
        }
        matchOption(CATEGORIES, category, "category");
        if (priority != null && !priority.isBlank()) matchOption(PRIORITIES, priority, "priority");
    }

    public static void validateRemarks(String remarks) {
        if (remarks != null && remarks.trim().length() > MAX_REMARKS) {
            throw new IllegalArgumentException("Remarks must be " + MAX_REMARKS + " characters or fewer.");
        }
    }

    public static boolean isValidTransition(String from, String to) {
        if (from == null || to == null || from.equals(to)) return false;
        switch (from) {
            case "Pending":     return false; // only Assign moves a Pending complaint forward
            case "Assigned":    return to.equals("In Progress") || to.equals("Resolved");
            case "In Progress": return to.equals("Resolved");
            default:            return false; // Resolved is final
        }
    }

    public static boolean canAccept(String status) {
        return "Assigned".equals(status);
    }

    public static boolean canReject(String status) {
        return "Assigned".equals(status);
    }

    public static boolean canView(User actor, Complaint c) {
        switch (actor.getRole()) {
            case "ADMIN":   return true;
            case "STAFF":   return isAssignedTo(actor, c);
            case "STUDENT": return isSubmittedBy(actor, c);
            default:        return false;
        }
    }

    public static boolean canUpdateStatus(User actor, Complaint c) {
        if ("ADMIN".equals(actor.getRole())) return true;
        return "STAFF".equals(actor.getRole()) && isAssignedTo(actor, c);
    }

    private static boolean isAssignedTo(User staff, Complaint c) {
        return c.getAssignedStaff() != null && c.getAssignedStaff().contains(staff.getName());
    }

    private static boolean isSubmittedBy(User student, Complaint c) {
        return c.getSubmittedBy() != null && c.getSubmittedBy().endsWith("(" + student.getUserId() + ")");
    }

    private static void requireRole(User actor, String role, String message) {
        if (!role.equals(actor.getRole())) throw new SecurityException(message);
    }

    static String matchOption(List<String> options, String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        for (String option : options) {
            if (option.equalsIgnoreCase(value.trim())) return option;
        }
        throw new IllegalArgumentException("Invalid " + field + ": " + value + ". Allowed: " + String.join(", ", options));
    }
}