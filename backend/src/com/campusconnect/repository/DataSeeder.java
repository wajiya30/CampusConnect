package com.campusconnect.repository;

import com.campusconnect.factory.UserFactory;
import com.campusconnect.model.Complaint;
import com.campusconnect.model.StatusHistory;
import com.campusconnect.model.User;
import com.campusconnect.util.PasswordUtil;

public final class DataSeeder {

    /** One password for every demo account. */
    static final String DEMO_PASSWORD = "password123";

    private DataSeeder() {}

    public static void seedIfEmpty(UserRepository users, ComplaintRepository complaints) {
        if (users.count() == 0) {
            // Students. The registration number is the same as the user ID.
            seedUser(users, "STUDENT", "STU-1", "Alex Student", "alex.student@campus.edu", "STU-1");
            seedUser(users, "STUDENT", "STU-2", "Sara Student", "sara.student@campus.edu", "STU-2");
            seedUser(users, "STUDENT", "STU-3", "Omar Student", "omar.student@campus.edu", "STU-3");
            seedUser(users, "STUDENT", "59806", "Tayyaba Iqbal", "tayyaba.iqbal@campus.edu", "59806");
            seedUser(users, "STUDENT", "58908", "Wajeeha Aqdas", "wajeeha.aqdas@campus.edu", "58908");
            seedUser(users, "STUDENT", "61472", "Nayyab Razzaq", "nayyab.razzaq@campus.edu", "61472");

            // Admin and maintenance staff
            seedUser(users, "ADMIN", "ADM-001", "Campus Admin", "admin@campus.edu", "Administration");
            seedUser(users, "STAFF", "STF-001", "Bilal Ahmed", "bilal.ahmed@campus.edu", "HVAC");
            seedUser(users, "STAFF", "STF-002", "Kamran Raza", "kamran.raza@campus.edu", "Laboratory");
            seedUser(users, "STAFF", "STF-003", "Tariq Mehmood", "tariq.mehmood@campus.edu", "Electrical");

            System.out.println("Seeded demo users. Password for all of them: " + DEMO_PASSWORD);
        }

        if (complaints.count() == 0) {
            seedComplaint(complaints, "CMP-1001", "AC unit leaking in Lecture Hall B2", "Electrical",
                    "Main Academic Block - Hall B2", "High",
                    "Water dripping near the front desk area during morning classes.",
                    "Tayyaba Iqbal (59806)", "In Progress", "Bilal Ahmed (HVAC)",
                    "Assigned to Bilal Ahmed (HVAC)");
            seedComplaint(complaints, "CMP-1002", "Broken lab stools and faulty power socket", "Laboratory",
                    "Computing Block - Software Lab 1", "Medium",
                    "Two stools have loose legs and workstation #14 socket lacks power.",
                    "Wajeeha Aqdas (58908)", "Assigned", "Kamran Raza (Laboratory)",
                    "Assigned to Kamran Raza (Laboratory)");
            seedComplaint(complaints, "CMP-1003", "Corridor waste bin overflow on 2nd floor", "Cleanliness",
                    "East Wing - 2nd Floor Corridor", "Low",
                    "Bin needs timely emptying and floor sanitization.",
                    "Nayyab Razzaq (61472)", "Resolved", "Sanitation Team A",
                    "Completed by Sanitation Team A");
            System.out.println("Seeded sample complaints.");
        }
    }

    private static void seedUser(UserRepository users, String role, String id, String name,
                                 String email, String extraInfo) {
        User u = UserFactory.createUser(role, id, name, email, extraInfo);
        users.save(u, PasswordUtil.hash(DEMO_PASSWORD));
    }

    private static void seedComplaint(ComplaintRepository repo, String id, String title, String category,
                                      String location, String priority, String description,
                                      String submittedBy, String status, String staff, String remarks) {
        Complaint c = new Complaint(id, title, category, location, priority, description, submittedBy);
        repo.save(c); // also writes a "Complaint submitted" history row
        c.setStatus(status);
        c.setAssignedStaff(staff);
        repo.update(c);
        repo.addHistory(new StatusHistory(id, status, remarks));
    }
}