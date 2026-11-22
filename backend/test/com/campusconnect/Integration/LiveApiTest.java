package com.campusconnect.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real workflow against a server that is already running on localhost:8080.
 * Start the server first (mvn exec:java -Dexec.mainClass=com.campusconnect.server.MainServer).
 * If the server isn't running, this test is skipped instead of failing.
 * Each run creates its own complaints, so it can be repeated safely.
 */
class LiveApiTest {

    private static final String BASE = "http://localhost:8080/api";
    private static final String DEMO_PASSWORD = "password123";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private record Resp(int status, JsonElement body) {}

    @BeforeAll
    static void serverMustBeRunning() {
        boolean up;
        try {
            up = call("GET", "/health", null, null).status() == 200;
        } catch (Exception e) {
            up = false;
        }
        Assumptions.assumeTrue(up, "Server not running on localhost:8080. Start it and rerun. Test skipped.");
    }

    @Test
    void studentAdminAndStaffWorkflowEndToEnd() throws Exception {
        String student = login("STU-1");
        String admin = login("ADM-001");
        String staff = login("STF-002"); // Kamran Raza, laboratory

        // 1. Student submits a complaint
        String id = submit(student, "Smoke test lab " + System.currentTimeMillis());

        // 2. Admin assigns it to a named staff member
        Resp assign = call("POST", "/complaints/" + id + "/assign", admin, json("staffUserId", "STF-002"));
        assertEquals(200, assign.status(), "admin assign: " + errorOf(assign));
        assertEquals("Assigned", field(id, admin, "status"));

        // 3. Staff can see the task
        assertEquals(200, call("GET", "/complaints/" + id, staff, null).status(), "staff sees assigned task");

        // 4. Staff cannot resolve before accepting
        Resp early = call("PUT", "/complaints/" + id + "/status", staff, json("status", "Resolved"));
        assertEquals(409, early.status(), "resolve before accept must be refused");

        // 5. Rejecting without a reason is refused
        Resp noReason = call("POST", "/complaints/" + id + "/reject", staff, "{}");
        assertEquals(400, noReason.status(), "reject without reason must be refused");

        // 6. Staff accepts the task
        Resp accept = call("POST", "/complaints/" + id + "/accept", staff, null);
        assertEquals(200, accept.status(), "accept: " + errorOf(accept));
        assertEquals("In Progress", field(id, staff, "status"));

        // 7. Student cannot change progress
        Resp studentTry = call("PUT", "/complaints/" + id + "/status", student, json("status", "Resolved"));
        assertEquals(403, studentTry.status(), "student must not update status");

        // 8. Staff marks it resolved with remarks
        JsonObject resolveBody = new JsonObject();
        resolveBody.addProperty("status", "Resolved");
        resolveBody.addProperty("remarks", "Replaced fitting");
        Resp resolve = call("PUT", "/complaints/" + id + "/status", staff, resolveBody.toString());
        assertEquals(200, resolve.status(), "resolve: " + errorOf(resolve));
        assertEquals("Resolved", field(id, student, "status"));

        // 9. The student sees the history and a notification
        assertTrue(historyContains(id, student, "Task accepted by"), "history records acceptance");
        assertTrue(notificationsContain(student, id), "student is notified about the complaint");

        // 10. Rejection path: staff returns a task, and the admin sees the reason
        String id2 = submit(student, "Smoke test reject " + System.currentTimeMillis());
        Resp assign2 = call("POST", "/complaints/" + id2 + "/assign", admin, json("staffUserId", "STF-002"));
        assertEquals(200, assign2.status(), "second assign: " + errorOf(assign2));

        Resp reject = call("POST", "/complaints/" + id2 + "/reject", staff, json("reason", "Needs an electrician"));
        assertEquals(200, reject.status(), "reject: " + errorOf(reject));
        assertEquals("Pending", field(id2, admin, "status"));
        assertEquals("Unassigned", field(id2, admin, "assignedStaff"));
        assertTrue(historyContains(id2, admin, "Rejected by"), "admin sees the rejection in history");
    }

    // ----- helpers -----

    private static String login(String userId) throws Exception {
        Resp r = call("POST", "/login", null, json2("userId", userId, "password", DEMO_PASSWORD));
        assertEquals(200, r.status(), "login " + userId + ": " + errorOf(r));
        return r.body().getAsJsonObject().get("token").getAsString();
    }

    private static String submit(String token, String title) throws Exception {
        JsonObject b = new JsonObject();
        b.addProperty("title", title);
        b.addProperty("category", "Laboratory");
        b.addProperty("location", "Smoke Lab");
        b.addProperty("priority", "High");
        b.addProperty("description", "Automated end-to-end test");
        Resp r = call("POST", "/complaints", token, b.toString());
        assertEquals(201, r.status(), "submit: " + errorOf(r));
        return r.body().getAsJsonObject().get("complaintId").getAsString();
    }

    private static String field(String id, String token, String key) throws Exception {
        Resp r = call("GET", "/complaints/" + id, token, null);
        assertEquals(200, r.status(), "read " + id);
        return r.body().getAsJsonObject().get(key).getAsString();
    }

    private static boolean historyContains(String id, String token, String text) throws Exception {
        Resp r = call("GET", "/complaints/" + id + "/history", token, null);
        assertEquals(200, r.status(), "history " + id);
        for (JsonElement el : r.body().getAsJsonArray()) {
            JsonObject h = el.getAsJsonObject();
            JsonElement remarks = h.get("remarks");
            if (remarks != null && !remarks.isJsonNull() && remarks.getAsString().contains(text)) return true;
        }
        return false;
    }

    private static boolean notificationsContain(String token, String id) throws Exception {
        Resp r = call("GET", "/notifications", token, null);
        assertEquals(200, r.status(), "notifications");
        JsonArray list = r.body().getAsJsonArray();
        for (JsonElement el : list) {
            if (el.getAsString().contains(id)) return true;
        }
        return false;
    }

    private static String errorOf(Resp r) {
        if (r.body() != null && r.body().isJsonObject() && r.body().getAsJsonObject().has("error")) {
            return r.body().getAsJsonObject().get("error").getAsString();
        }
        return "HTTP " + r.status();
    }

    private static String json(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o.toString();
    }

    private static String json2(String k1, String v1, String k2, String v2) {
        JsonObject o = new JsonObject();
        o.addProperty(k1, v1);
        o.addProperty(k2, v2);
        return o.toString();
    }

    private static Resp call(String method, String path, String token, String json) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(10));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (json != null) b.header("Content-Type", "application/json");
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        b.method(method, body);

        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonElement parsed = r.body().isBlank() ? null : JsonParser.parseString(r.body());
        return new Resp(r.statusCode(), parsed);
    }
}