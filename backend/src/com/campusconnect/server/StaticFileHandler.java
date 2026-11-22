package com.campusconnect.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the frontend folder (index.html, app.js, styles.css) read-only. Requests outside the folder are refused. */
public class StaticFileHandler implements HttpHandler {

    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, "text/plain; charset=utf-8", bytes("Method not allowed."));
            return;
        }

        String requested = ex.getRequestURI().getPath();
        if (requested == null || requested.isEmpty() || requested.equals("/")) {
            requested = "/index.html";
        }

        Path file = root.resolve(requested.substring(1)).normalize();
        // Block anything that resolves outside the frontend folder, such as ../../secrets
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            respond(ex, 404, "text/plain; charset=utf-8", bytes("Not found."));
            return;
        }
        respond(ex, 200, contentType(file), Files.readAllBytes(file));
    }

    private static void respond(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        // no-cache makes the browser check for changed files, so an old app.js is not reused
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        } finally {
            ex.close();
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}