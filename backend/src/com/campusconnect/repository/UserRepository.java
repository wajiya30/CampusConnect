package com.campusconnect.repository;

import com.campusconnect.exception.DatabaseException;
import com.campusconnect.factory.UserFactory;
import com.campusconnect.model.Admin;
import com.campusconnect.model.Staff;
import com.campusconnect.model.Student;
import com.campusconnect.model.User;
import com.campusconnect.singleton.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserRepository {

    public synchronized void save(User u, String passwordHash) {
        String sql = "INSERT INTO users (user_id, name, email, role, extra_info, password_hash) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, u.getUserId());
            ps.setString(2, u.getName());
            ps.setString(3, u.getEmail());
            ps.setString(4, u.getRole());
            ps.setString(5, extraInfoOf(u));
            ps.setString(6, passwordHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw db("save a user", e);
        }
    }

    public synchronized Optional<User> findById(String userId) {
        String sql = "SELECT user_id, name, email, role, extra_info FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapUser(rs));
            }
        } catch (SQLException e) {
            throw db("look up a user", e);
        }
    }

    /** All users with the given role, sorted by name. Used to list maintenance staff. */
    public synchronized List<User> findByRole(String role) {
        String sql = "SELECT user_id, name, email, role, extra_info FROM users WHERE role = ? ORDER BY name";
        List<User> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapUser(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw db("list users by role", e);
        }
    }

    public synchronized Optional<String> findPasswordHash(String userId) {
        String sql = "SELECT password_hash FROM users WHERE user_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw db("check a password", e);
        }
    }

    public synchronized boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw db("check an email", e);
        }
    }

    public synchronized long count() {
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw db("count users", e);
        }
    }

    /** Builds the right User subclass from one row. A bad role in the table is a data problem. */
    private static User mapUser(ResultSet rs) throws SQLException {
        String userId = rs.getString("user_id");
        try {
            return UserFactory.createUser(
                    rs.getString("role"),
                    userId,
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("extra_info"));
        } catch (IllegalArgumentException corrupt) {
            throw new DatabaseException("Corrupt user record for " + userId + ": " + corrupt.getMessage(), corrupt);
        }
    }

    /** Role-specific detail: registration number, department, or specialization. */
    static String extraInfoOf(User u) {
        if (u instanceof Student s) return s.getRegistrationNumber();
        if (u instanceof Admin a) return a.getDepartment();
        if (u instanceof Staff st) return st.getSpecialization();
        return null;
    }

    private static Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    private static DatabaseException db(String action, SQLException e) {
        return new DatabaseException("Database error while trying to " + action + ": " + e.getMessage(), e);
    }
}