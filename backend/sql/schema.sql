CREATE DATABASE IF NOT EXISTS campusconnect_db;
USE campusconnect_db;

CREATE TABLE IF NOT EXISTS users (
    user_id       VARCHAR(20)  PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL,
    extra_info    VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS complaints (
    complaint_id   VARCHAR(20)  PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    location       VARCHAR(150),
    priority       VARCHAR(20)  NOT NULL DEFAULT 'Medium',
    status         VARCHAR(20)  NOT NULL DEFAULT 'Pending',
    description    TEXT,
    submitted_by   VARCHAR(120),
    assigned_staff VARCHAR(120)  DEFAULT 'Unassigned',
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS status_history (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    remarks      VARCHAR(255),
    changed_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notifications (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id VARCHAR(20)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE
);

