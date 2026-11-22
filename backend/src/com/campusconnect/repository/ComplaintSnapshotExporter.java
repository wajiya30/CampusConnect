package com.campusconnect.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes every complaint and its status history to backend/data/complaints_data.json.
 * Read-only: it never changes the database. Any failure is logged and ignored.
 */
public final class ComplaintSnapshotExporter {

    private static final Path OUTPUT = Paths.get("data", "complaints_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private static final String COMPLAINTS_SQL =
            "SELECT complaint_id, title, category, location, priority, status, description, "
          + "submitted_by, assigned_staff, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_at "
          + "FROM complaints ORDER BY complaint_id";

    private static final String HISTORY_SQL =
            "SELECT status, remarks, DATE_FORMAT(changed_at, '%Y-%m-%d %H:%i') AS changed_at "
          + "FROM status_history WHERE complaint_id = ? ORDER BY changed_at, id";

    private ComplaintSnapshotExporter() {}

    /** Writes the snapshot. Never throws. */
    public static void exportSafely(Connection conn) {
        try {
            List<Map<String, Object>> complaints = readComplaints(conn);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("database", "campusconnect_db");
            payload.put("totalComplaints", complaints.size());
            payload.put("complaints", complaints);

            writeAtomically(GSON.toJson(payload));
        } catch (SQLException | IOException | RuntimeException e) {
            System.err.println("Snapshot skipped: " + e.getMessage());
        }
    }

    private static List<Map<String, Object>> readComplaints(Connection conn) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(COMPLAINTS_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("complaint_id", rs.getString("complaint_id"));
                c.put("title", rs.getString("title"));
                c.put("category", rs.getString("category"));
                c.put("location", rs.getString("location"));
                c.put("priority", rs.getString("priority"));
                c.put("status", rs.getString("status"));
                c.put("description", rs.getString("description"));
                c.put("submitted_by", rs.getString("submitted_by"));
                c.put("assigned_staff", rs.getString("assigned_staff"));
                c.put("created_at", rs.getString("created_at"));
                out.add(c);
            }
        }
        // Read history after the complaint rows are closed, one complaint at a time.
        for (Map<String, Object> c : out) {
            c.put("history", readHistory(conn, (String) c.get("complaint_id")));
        }
        return out;
    }

    private static List<Map<String, Object>> readHistory(Connection conn, String complaintId) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(HISTORY_SQL)) {
            ps.setString(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> h = new LinkedHashMap<>();
                    h.put("changed_at", rs.getString("changed_at"));
                    h.put("status", rs.getString("status"));
                    h.put("remarks", rs.getString("remarks"));
                    out.add(h);
                }
            }
        }
        return out;
    }

    /** Writes to a temp file first, then replaces the old one, so a half-written file is never left behind. */
    private static void writeAtomically(String json) throws IOException {
        Files.createDirectories(OUTPUT.getParent());
        Path tmp = OUTPUT.resolveSibling("complaints_data.json.tmp");
        Files.write(tmp, (json + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, OUTPUT, StandardCopyOption.REPLACE_EXISTING);
    }
}