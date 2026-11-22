package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ErrorMapper;
import com.campusconnect.model.Complaint;
import com.campusconnect.model.Staff;
import com.campusconnect.model.User;
import com.campusconnect.observer.NotificationService;
import com.campusconnect.service.AuthService;
import com.campusconnect.service.ComplaintService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HTTP layer. Routes requests to services and turns any exception into a JSON error response.
 * Every error response has the shape {"error": "..."} so the frontend can always show it.
 */
public class ComplaintController implements HttpHandler {

    private static final Gson GSON = new Gson();
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final AuthService auth;
    private final ComplaintService complaints;
    private final NotificationService notifier;

    public ComplaintController(AuthService auth, ComplaintService complaints, NotificationService notifier) {
        this.auth = auth;
        this.complaints = complaints;
        this.notifier = notifier;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        try {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 204, null);
                return;
            }
            route(ex);
        } catch (Exception e) {
            String reference = ErrorMapper.newReferenceId();
            int status = ErrorMapper.statusFor(e);
            logError(reference, status, e);
            // If a response was already sent (for example, the client disconnected), don't send another.
            if (ex.getResponseCode() == -1) {
                send(ex, status, error(ErrorMapper.messageFor(e, reference)));
            }
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        String path = ex.getRequestURI().getPath().replaceFirst("^/api", "");
        String[] p = Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
        if (p.length == 0) throw new ApiException(404, "Unknown endpoint.");

        // Public endpoints
        if (p.length == 1 && method.equals("GET") && p[0].equals("health")) {
            complaints.healthCheck();
            send(ex, 200, GSON.toJson(Map.of("status", "ok", "database", "ok")));
            return;
        }
        if (p.length == 1 && method.equals("POST") && p[0].equals("login")) { login(ex); return; }
        if (p.length == 1 && method.equals("POST") && p[0].equals("register")) { register(ex); return; }

        // Everything below needs a valid session
        User user = currentUser(ex);

        if (p.length == 1) {
            switch (p[0] + " " + method) {
                case "logout POST":
                    auth.logout(token(ex));
                    send(ex, 200, GSON.toJson(Map.of("message", "Logged out.")));
                    return;
                case "me GET":
                    send(ex, 200, GSON.toJson(userMap(user)));
                    return;
                case "stats GET":
                    send(ex, 200, GSON.toJson(complaints.statsFor(user)));
                    return;
                case "activity GET":
                    send(ex, 200, GSON.toJson(complaints.activityFor(user)));
                    return;
                case "notifications GET":
                    send(ex, 200, GSON.toJson(notifier.getNotificationsFor(visibleIds(user))));
                    return;
                case "staff GET":
                    send(ex, 200, GSON.toJson(staffList(user)));
                    return;
                case "complaints GET":
                    send(ex, 200, GSON.toJson(complaints.listFor(user)));
                    return;
                case "complaints POST":
                    createComplaint(ex, user);
                    return;
                default:
                    break;
            }
        }

        if (p.length == 2 && p[0].equals("complaints") && method.equals("GET")) {
            send(ex, 200, GSON.toJson(complaints.getFor(user, p[1])));
            return;
        }

        if (p.length == 3 && p[0].equals("complaints")) {
            routeComplaintAction(ex, method, p[1], p[2], user);
            return;
        }

        throw new ApiException(404, "Unknown endpoint.");
    }

    private void routeComplaintAction(HttpExchange ex, String method, String id, String action, User user)
            throws IOException {
        switch (action + " " + method) {
            case "history GET":
                send(ex, 200, GSON.toJson(complaints.historyFor(user, id)));
                return;
            case "assign POST": {
                JsonObject b = body(ex);
                send(ex, 200, GSON.toJson(
                        complaints.assign(user, id, str(b, "strategy"), str(b, "staffUserId"))));
                return;
            }
            case "accept POST":
                send(ex, 200, GSON.toJson(complaints.acceptTask(user, id)));
                return;
            case "reject POST": {
                JsonObject b = body(ex);
                send(ex, 200, GSON.toJson(complaints.rejectTask(user, id, str(b, "reason"))));
                return;
            }
            case "status PUT": {
                JsonObject b = body(ex);
                send(ex, 200, GSON.toJson(complaints.updateStatus(user, id, str(b, "status"), str(b, "remarks"))));
                return;
            }
            case "priority PUT": {
                JsonObject b = body(ex);
                send(ex, 200, GSON.toJson(complaints.setPriority(user, id, str(b, "priority"))));
                return;
            }
            default:
                throw new ApiException(404, "Unknown endpoint.");
        }
    }

    private void createComplaint(HttpExchange ex, User user) throws IOException {
        JsonObject b = body(ex);
        Complaint c = complaints.submit(user,
                str(b, "title"), str(b, "category"), str(b, "location"),
                str(b, "priority"), str(b, "description"));
        send(ex, 201, GSON.toJson(c));
    }

    private void login(HttpExchange ex) throws IOException {
        JsonObject b = body(ex);
        String token = auth.login(str(b, "userId"), str(b, "password"))
                .orElseThrow(() -> new ApiException(401, "Invalid user ID or password."));
        User user = auth.userForToken(token)
                .orElseThrow(() -> new ApiException(401, "Could not create a session. Please try again."));
        Map<String, Object> res = userMap(user);
        res.put("token", token);
        send(ex, 200, GSON.toJson(res));
    }

    private void register(HttpExchange ex) throws IOException {
        JsonObject b = body(ex);
        User u = auth.register(str(b, "role"), str(b, "userId"), str(b, "name"),
                str(b, "email"), str(b, "password"), str(b, "extraInfo"));
        send(ex, 201, GSON.toJson(userMap(u)));
    }

    /** Maintenance staff for the admin's assignment dropdown. */
    private List<Map<String, Object>> staffList(User admin) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : complaints.listStaff(admin)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", u.getUserId());
            m.put("name", u.getName());
            m.put("specialization", u instanceof Staff s ? s.getSpecialization() : "");
            out.add(m);
        }
        return out;
    }

    /** Complaint IDs this user can see. Used to filter notifications. */
    private Set<String> visibleIds(User user) {
        return complaints.listFor(user).stream()
                .map(Complaint::getComplaintId)
                .collect(Collectors.toSet());
    }

    private User currentUser(HttpExchange ex) {
        String token = token(ex);
        if (token == null) throw new ApiException(401, "Please log in to continue.");
        return auth.userForToken(token)
                .orElseThrow(() -> new ApiException(401, "Session expired. Please log in again."));
    }

    private static String token(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7).trim();
    }

    private static Map<String, Object> userMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("permissions", u.getDashboardPermissions());
        return m;
    }

    /** Reads and validates a JSON object body. Rejects oversized, malformed, or non-object bodies. */
    private static JsonObject body(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "The request is too large.");
        }
        String raw = new String(bytes, StandardCharsets.UTF_8);
        if (raw.isBlank()) return new JsonObject();

        JsonElement el;
        try {
            el = JsonParser.parseString(raw);
        } catch (JsonParseException e) {
            throw new ApiException(400, "The request body is not valid JSON.");
        }
        if (!el.isJsonObject()) throw new ApiException(400, "The request body must be a JSON object.");
        return el.getAsJsonObject();
    }

    /** Returns a text field, or null if it's missing. Non-text values are rejected. */
    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        if (!e.isJsonPrimitive()) throw new ApiException(400, "Field '" + key + "' must be text.");
        return e.getAsString();
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            if (bytes.length > 0) os.write(bytes);
        } finally {
            ex.close();
        }
    }

    private static String error(String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", message == null ? "Request failed." : message);
        return GSON.toJson(m);
    }

    private static void logError(String reference, int status, Exception e) {
        if (status == 500) {
            System.err.println("[" + reference + "] Unexpected server error:");
            e.printStackTrace();
        } else if (status >= 500) {
            System.err.println("[" + reference + "] Service error " + status + ": " + e.getMessage());
        } else {
            System.out.println("[" + reference + "] " + status + " " + e.getMessage());
        }
    }
}