package com.campusconnect.singleton;

import com.campusconnect.exception.DatabaseException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Singleton that owns the JDBC connection. Retries with backoff and reconnects after a dropped link. */
public class DatabaseConnection {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;

    private static volatile DatabaseConnection instance;

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    private DatabaseConnection() {
        this.url = env("DB_URL",
                "jdbc:mysql://localhost:3306/campusconnect_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        this.user = env("DB_USER", "root");
        this.password = env("DB_PASSWORD", "");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("MySQL JDBC driver is missing from the classpath.", e);
        }
    }

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    /** Returns a live connection. Reconnects if the old one was dropped. Throws DatabaseException if MySQL is down. */
    public synchronized Connection getConnection() {
        try {
            if (connection != null && !connection.isClosed() && connection.isValid(2)) {
                return connection;
            }
        } catch (SQLException e) {
            // The old connection is unusable. Fall through and reconnect.
        }
        return connectWithRetry();
    }

    private Connection connectWithRetry() {
        SQLException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                connection = DriverManager.getConnection(url, user, password);
                return connection;
            } catch (SQLException e) {
                last = e;
                System.err.println("MySQL connection attempt " + attempt + " of " + MAX_ATTEMPTS + " failed: " + e.getMessage());
                if (attempt < MAX_ATTEMPTS) sleep(RETRY_DELAY_MS * attempt);
            }
        }
        connection = null;
        throw new DatabaseException("Could not connect to MySQL after " + MAX_ATTEMPTS + " attempts.", last);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
