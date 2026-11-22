package com.campusconnect.repository;

import com.campusconnect.exception.DatabaseException;
import com.campusconnect.singleton.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Stores notification messages so they survive a server restart. */
public class NotificationRepository {

    private static final int MAX_RESULTS = 100;

    public synchronized void save(String complaintId, String message) {
        String sql = "INSERT INTO notifications (complaint_id, message) VALUES (?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, complaintId);
            ps.setString(2, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw db("save a notification", e);
        }
    }

    /** Newest first, only for the given complaint IDs. Empty input returns an empty list. */
    public synchronized List<String> findForComplaints(Collection<String> complaintIds) {
        List<String> result = new ArrayList<>();
        if (complaintIds.isEmpty()) return result;

        String placeholders = String.join(",", java.util.Collections.nCopies(complaintIds.size(), "?"));
        String sql = "SELECT message FROM notifications WHERE complaint_id IN (" + placeholders
                   + ") ORDER BY id DESC LIMIT " + MAX_RESULTS;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            int i = 1;
            for (String id : complaintIds) ps.setString(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
            return result;
        } catch (SQLException e) {
            throw db("read notifications", e);
        }
    }

    private static Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    private static DatabaseException db(String action, SQLException e) {
        return new DatabaseException("Database error while trying to " + action + ": " + e.getMessage(), e);
    }
}
