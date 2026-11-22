package com.campusconnect.repository;

import com.campusconnect.exception.DatabaseException;
import com.campusconnect.model.Complaint;
import com.campusconnect.model.StatusHistory;
import com.campusconnect.singleton.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC access for complaints and their history. Multi-step writes run in a transaction. */
public class ComplaintRepository {

    private static final String SELECT_COMPLAINTS =
            "SELECT complaint_id, title, category, location, priority, status, description, submitted_by, assigned_staff "
          + "FROM complaints";

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection conn) throws SQLException;
    }

    public synchronized String generateNextId() {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(complaint_id, 5) AS UNSIGNED)), 1000) FROM complaints";
        try (Statement st = conn().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return "CMP-" + (rs.getLong(1) + 1);
        } catch (SQLException e) {
            throw db("generate a complaint ID", e);
        }
    }

    /** Inserts the complaint and its "submitted" history row, both or neither. */
    public synchronized void save(Complaint c) {
        inTransaction("save a complaint", conn -> {
            insertComplaint(conn, c);
            insertHistory(conn, new StatusHistory(c.getComplaintId(), c.getStatus(), "Complaint submitted"));
        });
        exportSnapshot();
    }
    /** Updates the complaint and records the change in history, both or neither. */
    public synchronized void applyChange(Complaint c, StatusHistory entry) {
        inTransaction("update a complaint", conn -> {
            updateComplaint(conn, c);
            insertHistory(conn, entry);
        });
        exportSnapshot();
    }

    /** Plain update, used by the seeder. */
    public synchronized void update(Complaint c) {
        run("update a complaint", conn -> updateComplaint(conn, c));
    }

    /** Plain history insert, used by the seeder. */
    public synchronized void addHistory(StatusHistory h) {
        run("record history", conn -> insertHistory(conn, h));
    }

    public synchronized List<Complaint> findAll() {
        String sql = SELECT_COMPLAINTS + " ORDER BY created_at DESC, complaint_id DESC";
        List<Complaint> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            throw db("list complaints", e);
        }
    }

    public synchronized Optional<Complaint> findById(String id) {
        String sql = SELECT_COMPLAINTS + " WHERE complaint_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw db("look up a complaint", e);
        }
    }

    public synchronized long count() {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM complaints")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw db("count complaints", e);
        }
    }

/** Newest first. */
    public synchronized List<StatusHistory> historyFor(String complaintId) {
        String sql = "SELECT complaint_id, status, remarks, DATE_FORMAT(changed_at, '%Y-%m-%d %H:%i') AS ts "
                   + "FROM status_history WHERE complaint_id = ? ORDER BY changed_at DESC, id DESC";
        List<StatusHistory> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new StatusHistory(
                            rs.getString("complaint_id"),
                            rs.getString("status"),
                            rs.getString("remarks")));
                }
            }
            return list;
        } catch (SQLException e) {
            throw db("read complaint history", e);
        }
    }

    // ----- SQL helpers -----

    private static void insertComplaint(Connection conn, Complaint c) throws SQLException {
        String sql = "INSERT INTO complaints (complaint_id, title, category, location, priority, status, "
                   + "description, submitted_by, assigned_staff) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getComplaintId());
            ps.setString(2, c.getTitle());
            ps.setString(3, c.getCategory());
            ps.setString(4, c.getLocation());
            ps.setString(5, c.getPriority());
            ps.setString(6, c.getStatus());
            ps.setString(7, c.getDescription());
            ps.setString(8, c.getSubmittedBy());
            ps.setString(9, c.getAssignedStaff());
            ps.executeUpdate();
        }
    }

    private static void updateComplaint(Connection conn, Complaint c) throws SQLException {
        String sql = "UPDATE complaints SET priority = ?, status = ?, assigned_staff = ? WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getPriority());
            ps.setString(2, c.getStatus());
            ps.setString(3, c.getAssignedStaff());
            ps.setString(4, c.getComplaintId());
            ps.executeUpdate();
        }
    }

    private static void insertHistory(Connection conn, StatusHistory h) throws SQLException {
        String sql = "INSERT INTO status_history (complaint_id, status, remarks) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, h.getComplaintId());
            ps.setString(2, h.getStatus());
            ps.setString(3, h.getRemarks());
            ps.executeUpdate();
        }
    }

    /** Runs one statement on the shared connection. */
    private static void run(String action, SqlWork work) {
        try {
            work.run(conn());
        } catch (SQLException e) {
            throw db(action, e);
        }
    }

    /** Runs several statements atomically. If any fails, everything is rolled back. */
    private static void inTransaction(String action, SqlWork work) {
        Connection conn = conn();
        try {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
            } catch (SQLException e) {
                rollbackQuietly(conn);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw db(action, e);
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            System.err.println("Rollback failed: " + e.getMessage());
        }
    }

    private static Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    private static Complaint map(ResultSet rs) throws SQLException {
        Complaint c = new Complaint(
                rs.getString("complaint_id"),
                rs.getString("title"),
                rs.getString("category"),
                rs.getString("location"),
                rs.getString("priority"),
                rs.getString("description"),
                rs.getString("submitted_by"));
        c.setStatus(rs.getString("status"));
        c.setAssignedStaff(rs.getString("assigned_staff"));
        return c;
    }

    private static DatabaseException db(String action, SQLException e) {
        return new DatabaseException("Database error while trying to " + action + ": " + e.getMessage(), e);
    }
 public synchronized void exportSnapshot() {
        try {
            ComplaintSnapshotExporter.exportSafely(conn());
        } catch (RuntimeException e) {
            System.err.println("Snapshot skipped: " + e.getMessage());
        }
    }
}