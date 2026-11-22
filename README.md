
# CampusConnect

A web-based Campus Complaint and Facility Management System. Students report campus facility problems, administrators assign them to maintenance staff, and staff accept, progress, and resolve the work. Every change is recorded in a status history, and users receive notifications.

Course: Software Construction and Development (SCD), 6th Semester

## Features

- Role-based login for students, administrators, and maintenance staff
- Complaint submission with category, location, priority, and description
- Assignment to a named staff member or by strategy (category or priority)
- Staff accept, reject with a reason, update progress, and mark resolved
- Full status history and notifications
- Dashboard statistics and search and filter

## Technologies

| Area | Technology |
|---|---|
| Backend | Java 17, JDK HTTP server, Gson |
| Database | MySQL 8.0 with JDBC |
| Frontend | HTML, CSS, JavaScript |
| Build and tests | Maven, JUnit 5 |
| Version control | Git and GitHub |

## Design Patterns

| Pattern | Where it is used |
|---|---|
| MVC | `model`, `controller`, and `frontend` |
| Repository | `repository` package (ComplaintRepository, UserRepository, NotificationRepository) |
| Factory | `factory/UserFactory.java` |
| Singleton | `singleton/DatabaseConnection.java` |
| Observer | `observer` package (ComplaintObserver, NotificationService) |
| Strategy | `strategy` package (CategoryAssignment, PriorityAssignment) |

## Project Structure

```
CampusConnect/
├── backend/
│   ├── src/          Java source code
│   ├── test/         JUnit 5 unit and integration tests
│   ├── sql/          Database schema (schema.sql)
│   └── pom.xml       Maven build file
├── frontend/
│   ├── index.html    Screens
│   ├── app.js        Screen logic and API calls
│   └── styles.css    Styling
├── .gitignore
└── README.md
```

## Setup

### Requirements

- Java JDK 17 or newer
- Apache Maven 3.9 or newer
- MySQL 8.0 running on localhost:3306
- A web browser

### Steps

1. Create the database:
   ```bat
   cd backend
   mysql -u root -p < sql\schema.sql
   ```
2. Set the environment variables:
   ```bat
   set DB_PASSWORD=your_mysql_password
   set FRONTEND_DIR=..\frontend
   ```
3. Build and start the server:
   ```bat
   mvn clean compile
   mvn exec:java -Dexec.mainClass=com.campusconnect.server.MainServer
   ```
4. Open http://localhost:8080/ in your browser.

Demo accounts use the password `password123`:

| Role | User ID |
|---|---|
| Student | STU-1, 59806, 58908, 61472 |
| Administrator | ADM-001 |
| Maintenance staff | STF-001, STF-002, STF-003 |

## Running Tests

```bat
cd backend
mvn clean test
```

The project has 36 unit tests and 1 integration test. The integration test needs the server running on port 8080; otherwise it is skipped.

## Documentation

Full documentation, UML diagrams, and test case tables are in the project report.
