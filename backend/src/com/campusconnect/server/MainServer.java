package com.campusconnect.server;

import com.campusconnect.controller.ComplaintController;
import com.campusconnect.exception.DatabaseException;
import com.campusconnect.observer.NotificationService;
import com.campusconnect.repository.ComplaintRepository;
import com.campusconnect.repository.DataSeeder;
import com.campusconnect.repository.NotificationRepository;
import com.campusconnect.repository.UserRepository;
import com.campusconnect.service.AuthService;
import com.campusconnect.service.ComplaintService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class MainServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;

        ComplaintRepository complaintRepo = new ComplaintRepository();
        UserRepository userRepo = new UserRepository();
        NotificationRepository notificationRepo = new NotificationRepository();

        try {
            DataSeeder.seedIfEmpty(userRepo, complaintRepo);
        } catch (RuntimeException e) {
            System.err.println("Startup failed: " + e.getMessage());
            if (e instanceof DatabaseException) {
                System.err.println("Check that: 1) MySQL is running, 2) backend/sql/schema.sql has been run,");
                System.err.println("            3) DB_USER and DB_PASSWORD are correct.");
            }
            System.exit(1);
            return;
        }
         complaintRepo.exportSnapshot();
         
        NotificationService notifier = new NotificationService(notificationRepo);
        ComplaintService complaintService = new ComplaintService(complaintRepo, userRepo, notifier);
        AuthService authService = new AuthService(userRepo);

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        } catch (BindException e) {
            System.err.println("Port " + port + " is already in use. Close the other program using it and try again.");
            System.exit(1);
            return;
        }

        // Frontend folder. Set FRONTEND_DIR to point somewhere else if needed.
        Path frontendDir = Paths.get(System.getenv().getOrDefault("FRONTEND_DIR", "../frontend"));
        boolean hasUi = Files.isRegularFile(frontendDir.resolve("index.html"));

        server.createContext("/api", new ComplaintController(authService, complaintService, notifier));
        if (hasUi) {
            server.createContext("/", new StaticFileHandler(frontendDir));
        }

        // One request at a time keeps the repositories and the shared JDBC connection free of races.
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        System.out.println("CampusConnect API:  http://localhost:" + port + "/api");
        if (hasUi) {
            System.out.println("Open the app at:    http://localhost:" + port + "/");
            System.out.println("Frontend folder:    " + frontendDir.toAbsolutePath().normalize());
        } else {
            System.out.println("WARNING: no index.html found at " + frontendDir.toAbsolutePath().normalize());
            System.out.println("         Set FRONTEND_DIR to the frontend folder and restart.");
        }
    }
}