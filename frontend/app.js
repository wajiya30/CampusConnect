// CampusConnect frontend. Every read and write goes through the Java backend at localhost:8080/api.

const API_BASE = window.location.protocol === "file:" ? "http://localhost:8080/api" : "/api";
const BUILD = "2026-11-21-b";
document.title = `CampusConnect · build ${BUILD}`;
const REQUEST_TIMEOUT_MS = 10000;
const POLL_MS = 15000;
const FAILURES_BEFORE_BANNER = 2;

const PRIORITIES = ["Low", "Medium", "High"];
const MAX_REMARKS = 250;

// Mirrors ComplaintService.isValidTransition. Pending moves only through Assign.
const NEXT_STATUSES = {
  "Pending": [],
  "Assigned": ["In Progress", "Resolved"],
  "In Progress": ["Resolved"],
  "Resolved": []
};

// Demo accounts created by DataSeeder. Every demo password is campus123.
const DEMO_ACCOUNTS = {
  student: { userId: "STU-1", password: "password123" },
  admin:   { userId: "ADM-001", password: "password123" },
  staff:   { userId: "STF-001", password: "password123" }
};
const ROLE_LABELS = { STUDENT: "Student", ADMIN: "Administrator", STAFF: "Maintenance Staff" };
const ROLE_BANNERS = {
  STUDENT: "Student Workspace",
  ADMIN: "Administrator Control Panel",
  STAFF: "Maintenance Technician Desk"
};

const state = {
  stage: "splash",
  token: null,
  user: null,          // logged-in user from /api/login
  complaints: [],      // complaints this user can see
  activity: [],        // status history, newest first
  notifications: [],   // newest first
  stats: {},
  staff: [],           // maintenance staff list (admin only)
  selectedId: null,    // complaint open in the modal
  unread: 0,
  lastNotifCount: 0,
  failures: 0,         // consecutive failed background refreshes
  pollTimer: null
};

// ---------- DOM references ----------
const $ = (id) => document.getElementById(id);

const splashScreen = $("splashScreen");
const loginScreen = $("loginScreen");
const dashboardShell = $("dashboardShell");
const loginForm = $("loginForm");
const loginId = $("loginId");
const loginPassword = $("loginPassword");
const loginError = $("loginError");
const connBanner = $("connBanner");
const connMessage = $("connMessage");
const complaintsTableBody = $("complaintsTableBody");
const searchInput = $("searchInput");
const filterCategory = $("filterCategory");
const filterStatus = $("filterStatus");
const complaintForm = $("complaintForm");
const detailModal = $("detailModal");
const modalBody = $("modalBody");
const notifDropdown = $("notifDropdown");
const toastMsg = $("toastMsg");

let toastTimer = null;
let pollInFlight = false;

// ---------- Helpers ----------

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status; // 0 means no HTTP response (network or timeout)
  }
}

function esc(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

// "In Progress" -> "status-In-Progress" (matches styles.css)
function statusClass(status) {
  return "status-" + status.replace(/\s+/g, "-");
}

function showToast(message, isError = false) {
  clearTimeout(toastTimer);
  toastMsg.textContent = message;
  toastMsg.classList.toggle("error", isError);
  toastMsg.classList.remove("hidden");
  toastTimer = setTimeout(() => toastMsg.classList.add("hidden"), 3200);
}

function setStage(stage) {
  state.stage = stage;
  splashScreen.classList.toggle("hidden", stage !== "splash");
  loginScreen.classList.toggle("hidden", stage !== "login");
  dashboardShell.classList.toggle("hidden", stage !== "dashboard");
}

function setLoginError(message) {
  loginError.textContent = message || "";
  loginError.classList.toggle("hidden", !message);
}

function setConnectionBanner(message) {
  connMessage.textContent = message || "";
  connBanner.classList.toggle("hidden", !message);
}

function applyDemoAccount(role) {
  document.querySelectorAll(".role-pill-card").forEach(c => {
    c.classList.toggle("active", c.dataset.role === role);
  });
  loginId.value = DEMO_ACCOUNTS[role].userId;
  loginPassword.value = DEMO_ACCOUNTS[role].password;
  setLoginError("");
}

function isAdmin() {
  return state.user?.role === "ADMIN";
}

function isStaff() {
  return state.user?.role === "STAFF";
}

function isAssignedToMe(item) {
  return isStaff() && item.staff.includes(state.user.name);
}

function switchView(viewName) {
  document.querySelectorAll(".nav-item").forEach(btn => {
    btn.classList.toggle("active", btn.dataset.view === viewName);
  });

  $("view-dashboard").classList.toggle("hidden", viewName === "submit" || viewName === "history");
  $("view-submit").classList.toggle("hidden", viewName !== "submit");
  $("view-history").classList.toggle("hidden", viewName !== "history");

  const titles = {
    dashboard: ["Dashboard Overview", "Track campus maintenance requests, assignments, and live updates."],
    complaints: ["All Campus Complaints", "Complete list of reported facility issues you can access."],
    submit: ["Submit a Complaint", "Report an electrical, classroom, laboratory, or cleanliness issue."],
    history: ["Activity & Status Log", "Chronological record of submissions, assignments, and status changes."]
  };
  if (isStaff()) {
    titles.complaints = ["My Tasks", "Complaints assigned to you. Accept or reject each new task, then update its progress."];
  }

  const [title, sub] = titles[viewName] || titles.dashboard;
  $("pageTitle").textContent = title;
  $("pageSubtitle").textContent = sub;
}

// Turns an HTTP status into a sentence, for responses that have no JSON error message.
function friendlyStatusMessage(status) {
  if (status === 404) return "That item could not be found.";
  if (status === 413) return "The data you sent is too large.";
  if (status === 502 || status === 503 || status === 504) {
    return "The server is unavailable right now. Please try again.";
  }
  return `Request failed (HTTP ${status}).`;
}

// ---------- API ----------

async function api(path, options = {}) {
  const { method = "GET", body } = options;

  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (state.token) headers["Authorization"] = "Bearer " + state.token;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  let res;
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal
    });
  } catch (err) {
    if (err.name === "AbortError") {
      throw new ApiError("The server took too long to respond. Please try again.", 0);
    }
    throw new ApiError("Cannot reach the CampusConnect server. Make sure the Java backend is running on port 8080.", 0);
  } finally {
    clearTimeout(timer);
  }

  let text;
  try {
    text = await res.text();
  } catch {
    throw new ApiError("The response from the server was interrupted. Please try again.", res.status);
  }

  const isJson = (res.headers.get("Content-Type") || "").includes("application/json");
  let data = null;
  if (isJson && text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!res.ok) {
    if (res.status === 401 && path !== "/login" && state.token) {
      handleSessionExpired();
    }
    const message = (data && data.error) || friendlyStatusMessage(res.status);
    throw new ApiError(message, res.status);
  }

  if (text && !isJson) {
    throw new ApiError("The server sent an unexpected response.", res.status);
  }
  return data;
}

function handleSessionExpired() {
  stopPolling();
  clearSession();
  setStage("login");
  setLoginError("Your session has ended. Please sign in again.");
}

function clearSession() {
  state.token = null;
  state.user = null;
  state.complaints = [];
  state.activity = [];
  state.notifications = [];
  state.stats = {};
  state.staff = [];
  state.selectedId = null;
  state.unread = 0;
  state.lastNotifCount = 0;
  state.failures = 0;
  detailModal.classList.add("hidden");
  setConnectionBanner("");
}

// ---------- Background refresh ----------

function startPolling() {
  stopPolling();
  state.pollTimer = setInterval(pollOnce, POLL_MS);
}

function stopPolling() {
  clearInterval(state.pollTimer);
  state.pollTimer = null;
}

async function pollOnce() {
  if (pollInFlight || state.stage !== "dashboard") return;
  pollInFlight = true;
  try {
    await loadDashboard();
    if (state.failures >= FAILURES_BEFORE_BANNER) showToast("Connection restored.");
    state.failures = 0;
    setConnectionBanner("");
  } catch (err) {
    if (state.stage !== "dashboard") return; // session ended. The login screen already explains why.
    state.failures += 1;
    if (state.failures >= FAILURES_BEFORE_BANNER) {
      setConnectionBanner(`Connection problem: ${err.message} Retrying automatically.`);
    }
  } finally {
    pollInFlight = false;
  }
}

// Backend complaint -> the shape the UI uses.
function toView(c) {
  return {
    id: c.complaintId,
    title: c.title,
    category: c.category,
    location: c.location || "",
    priority: c.priority,
    status: c.status,
    staff: c.assignedStaff || "Unassigned",
    description: c.description || "",
    submittedBy: c.submittedBy || ""
  };
}

async function loadDashboard() {
  const [complaints, stats, activity, notifications, staff] = await Promise.all([
    api("/complaints"),
    api("/stats"),
    api("/activity"),
    api("/notifications"),
    isAdmin() ? api("/staff") : Promise.resolve([])
  ]);

  state.complaints = complaints.map(toView);
  state.stats = stats;
  state.activity = activity;
  state.staff = staff;

  // New alerts since the last refresh increase the unread badge.
  const newCount = notifications.length - state.lastNotifCount;
  if (newCount > 0) state.unread += newCount;
  state.lastNotifCount = notifications.length;
  state.notifications = notifications;

  renderAll();
}

// ---------- Rendering ----------

function renderAll() {
  renderProfile();
  renderStats();
  renderComplaints();
  renderActivity();
  renderNotifications();
}

function renderProfile() {
  const u = state.user;
  if (!u) return;

  const initials = u.name.split(" ").filter(Boolean).slice(0, 2).map(w => w[0]).join("").toUpperCase();
  $("userName").textContent = u.name;
  $("userAvatar").textContent = initials;
  $("userMeta").textContent = `${u.userId} • ${ROLE_LABELS[u.role] || u.role}`;
  $("welcomeRoleBanner").textContent = ROLE_BANNERS[u.role] || "CampusConnect";

  const navComplaints = document.querySelector('.nav-item[data-view="complaints"]');
  navComplaints.innerHTML = `<span class="nav-dot"></span> ${isStaff() ? "My Tasks" : "All Complaints"}`;

  $("navSubmitBtn").classList.toggle("hidden", u.role !== "STUDENT");
  $("quickNewBtn").classList.toggle("hidden", u.role !== "STUDENT");
}

function renderStats() {
  const s = state.stats;
  $("statTotal").textContent = s.total ?? 0;
  $("statPending").textContent = (s.Pending ?? 0) + (s.Assigned ?? 0);
  $("statProgress").textContent = s["In Progress"] ?? 0;
  $("statResolved").textContent = s.Resolved ?? 0;
}

function renderComplaints() {
  if (!state.user) return;

  const cat = filterCategory.value;
  const st = filterStatus.value;
  const q = searchInput.value.trim().toLowerCase();
  const isStudent = state.user.role === "STUDENT";

  const filtered = state.complaints.filter(c => {
    const matchCat = cat === "ALL" || c.category === cat;
    const matchStatus = st === "ALL" || c.status === st;
    const matchQuery =
      !q ||
      c.id.toLowerCase().includes(q) ||
      c.title.toLowerCase().includes(q) ||
      c.location.toLowerCase().includes(q);
    return matchCat && matchStatus && matchQuery;
  });

  if (filtered.length === 0) {
    const empty = isStaff()
      ? "No tasks are assigned to you right now."
      : "No complaints found matching your search or filter.";
    complaintsTableBody.innerHTML = `
      <tr><td colspan="7" style="text-align:center; color: var(--text-muted); padding: 28px;">${empty}</td></tr>`;
    return;
  }

  complaintsTableBody.innerHTML = filtered.map(c => `
    <tr>
      <td><span class="mono-id">${esc(c.id)}</span></td>
      <td>
        <div class="issue-title">${esc(c.title)}</div>
        <div class="issue-loc">${esc(c.location)}</div>
      </td>
      <td>${esc(c.category)}</td>
      <td><span class="pill priority-${esc(c.priority)}">${esc(c.priority)}</span></td>
      <td><span class="pill ${statusClass(c.status)}">${esc(c.status)}</span></td>
      <td>${esc(c.staff)}</td>
      <td>
        <button class="btn btn-secondary btn-sm" data-open="${esc(c.id)}">
          ${isStudent ? "View Details" : (isStaff() ? "Open Task" : "Manage")}
        </button>
      </td>
    </tr>
  `).join("");
}

function renderActivity() {
  const list = $("historyTimeline");
  if (state.activity.length === 0) {
    list.innerHTML = `<li class="timeline-item"><span>No activity yet.</span></li>`;
    return;
  }
  list.innerHTML = state.activity.map(h => `
    <li class="timeline-item">
      <span><span class="mono-id">${esc(h.complaintId)}</span> ${esc(h.remarks)}</span>
      <span class="issue-loc">${esc(h.timestamp)} · ${esc(h.status)}</span>
    </li>
  `).join("");
}

function renderNotifications() {
  const countEl = $("notifCount");
  countEl.textContent = state.unread;
  countEl.style.display = state.unread > 0 ? "inline-block" : "none";

  const listEl = $("notifList");
  listEl.innerHTML = state.notifications.length
    ? state.notifications.map((text, i) => `
        <li class="notif-item ${i < state.unread ? "unread" : ""}">${esc(text)}</li>
      `).join("")
    : `<li class="notif-item">No notifications yet.</li>`;
}

// ---------- Complaint window (modal) ----------

async function openComplaintModal(id) {
  const item = state.complaints.find(c => c.id === id);
  if (!item) return;
  state.selectedId = id;

  const admin = isAdmin();
  const mine = isAssignedToMe(item);
  const isResolved = item.status === "Resolved";
  const nextStatuses = [item.status, ...(NEXT_STATUSES[item.status] || [])];
  const noNextStep = nextStatuses.length < 2;

  $("modalComplaintId").textContent = item.id;
  $("modalComplaintTitle").textContent = item.title;

  // Admin: assign to a specific person, or let a strategy pick one
  const staffOptions = state.staff.map(s =>
    `<option value="staff:${esc(s.userId)}">${esc(s.name)} (${esc(s.specialization)})</option>`
  ).join("");
  const adminAssignBlock = admin ? `
    <div class="modal-section">
      <div class="form-group">
        <label for="modalAssignee">Assign to</label>
        <div class="inline-row">
          <select id="modalAssignee" ${isResolved ? "disabled" : ""}>
            <optgroup label="Automatic (strategy)">
              <option value="auto:category">By category</option>
              <option value="auto:priority">By priority</option>
            </optgroup>
            <optgroup label="Maintenance staff">
              ${staffOptions || `<option disabled>No staff accounts found</option>`}
            </optgroup>
          </select>
          <button type="button" class="btn btn-secondary btn-sm" data-action="assign" ${isResolved ? "disabled" : ""}>Assign</button>
        </div>
        <p class="issue-loc">Assigned staff must accept the task before work starts.</p>
      </div>
    </div>` : "";

  const adminPriorityBlock = admin ? `
    <div class="modal-section">
      <div class="form-group">
        <label for="modalPriority">Priority</label>
        <div class="inline-row">
          <select id="modalPriority">
            ${PRIORITIES.map(p => `<option value="${p}" ${p === item.priority ? "selected" : ""}>${p}</option>`).join("")}
          </select>
          <button type="button" class="btn btn-secondary btn-sm" data-action="priority">Save Priority</button>
        </div>
      </div>
    </div>` : "";

  // Staff: accept or reject a task that was just assigned to them
  const staffTaskBlock = (mine && item.status === "Assigned") ? `
    <div class="modal-section">
      <div class="form-group">
        <label>New task assigned to you</label>
        <p class="issue-loc">Accept to start the work, or reject it with a reason so the admin can reassign it.</p>
        <div class="inline-row">
          <button type="button" class="btn btn-accent btn-sm" data-action="accept">Accept Task</button>
          <button type="button" class="btn btn-secondary btn-sm" data-action="reject">Reject Task</button>
        </div>
      </div>
      <div class="form-group">
        <label for="modalReason">Reason for rejecting (required to reject)</label>
        <input type="text" id="modalReason" maxlength="${MAX_REMARKS}" placeholder="e.g., Needs an electrical specialist" />
      </div>
    </div>` : "";

  // Admin: any valid step after assignment. Staff: progress on their own In Progress task.
  const showStatusBlock =
    (admin && item.status !== "Pending") ||
    (mine && item.status === "In Progress");
  const statusBlock = showStatusBlock ? `
    <div class="modal-section">
      <div class="form-group">
        <label for="modalStatus">Update progress</label>
        <div class="inline-row">
          <select id="modalStatus" ${noNextStep ? "disabled" : ""}>
            ${nextStatuses.map(s => `<option value="${s}" ${s === item.status ? "selected" : ""}>${s}</option>`).join("")}
          </select>
          <button type="button" class="btn btn-accent btn-sm" data-action="status" ${noNextStep ? "disabled" : ""}>Update</button>
        </div>
      </div>
      <div class="form-group">
        <label for="modalRemarks">Remarks (optional)</label>
        <input type="text" id="modalRemarks" maxlength="${MAX_REMARKS}" placeholder="e.g., Part ordered, technician on site" />
      </div>
    </div>` : "";

  const hasControls = adminAssignBlock || adminPriorityBlock || staffTaskBlock || statusBlock;
  let readOnlyNote = "";
  if (!hasControls) {
    readOnlyNote = isResolved
      ? `<p class="issue-loc">This complaint is resolved and closed.</p>`
      : (state.user.role === "STUDENT"
          ? `<p class="issue-loc">Progress is updated by administrators and the assigned maintenance staff.</p>`
          : `<p class="issue-loc">This complaint is not assigned to you.</p>`);
  }

  modalBody.innerHTML = `
    <div id="modalReturnNote"></div>
    <p><strong>Location:</strong> ${esc(item.location)}</p>
    <p><strong>Category:</strong> ${esc(item.category)} &nbsp;|&nbsp; <strong>Priority:</strong> ${esc(item.priority)}</p>
    <p><strong>Current Status:</strong> ${esc(item.status)} &nbsp;|&nbsp; <strong>Staff:</strong> ${esc(item.staff)}</p>
    <p><strong>Submitted By:</strong> ${esc(item.submittedBy)}</p>
    <p><strong>Description:</strong> ${esc(item.description)}</p>
    ${adminAssignBlock}
    ${adminPriorityBlock}
    ${staffTaskBlock}
    ${statusBlock}
    ${readOnlyNote}
    <div class="modal-section">
      <strong>Activity on this complaint</strong>
      <ul class="timeline" id="modalHistory">
        <li class="timeline-item"><span>Loading...</span></li>
      </ul>
    </div>
  `;

  detailModal.classList.remove("hidden");

  try {
    const history = await api(`/complaints/${encodeURIComponent(id)}/history`);
    if (state.selectedId !== id) return; // the window was closed or moved to another complaint
    renderModalHistory(history);
  } catch (err) {
    if (state.selectedId !== id) return;
    $("modalHistory").innerHTML = `<li class="timeline-item"><span>Could not load activity: ${esc(err.message)}</span></li>`;
  }
}

function renderModalHistory(history) {
  const list = $("modalHistory");
  if (!list) return;

  list.innerHTML = history.length
    ? history.map(h => `
        <li class="timeline-item">
          <span>${esc(h.remarks)}</span>
          <span class="issue-loc">${esc(h.timestamp)} · ${esc(h.status)}</span>
        </li>`).join("")
    : `<li class="timeline-item"><span>No activity yet.</span></li>`;

  // If the latest change was a staff rejection, the admin needs to see the reason at the top.
  const latest = history[0];
  const note = $("modalReturnNote");
  if (note && isAdmin() && latest && latest.remarks && latest.remarks.startsWith("Rejected by")) {
    note.innerHTML = `
      <p class="alert-note">
        Returned by staff: ${esc(latest.remarks)}<br />
        Choose another staff member or a strategy below to reassign it.
      </p>`;
  }
}

function closeModal() {
  detailModal.classList.add("hidden");
  state.selectedId = null;
}

// ---------- Event wiring ----------

// Splash and login navigation
$("continueToLoginBtn").addEventListener("click", () => setStage("login"));
$("backToLogoBtn").addEventListener("click", () => setStage("splash"));

document.querySelectorAll(".role-pill-card").forEach(card => {
  card.addEventListener("click", () => applyDemoAccount(card.dataset.role));
});

loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const submitBtn = loginForm.querySelector('button[type="submit"]');
  submitBtn.disabled = true;
  setLoginError("");

  try {
    const user = await api("/login", {
      method: "POST",
      body: { userId: loginId.value.trim(), password: loginPassword.value }
    });
    state.user = user;
    state.token = user.token;

    await loadDashboard();
    state.unread = 0; // alerts that already exist at sign-in are not new
    renderNotifications();

    setStage("dashboard");
    switchView("dashboard");
    state.failures = 0;
    startPolling();
    showToast(`Welcome back, ${user.name}!`);
  } catch (err) {
    clearSession();
    setLoginError(err.message);
  } finally {
    submitBtn.disabled = false;
  }
});

$("logoutBtn").addEventListener("click", async () => {
  try {
    await api("/logout", { method: "POST" });
  } catch {
    // The session may already be gone on the server. That's fine.
  }
  stopPolling();
  clearSession();
  notifDropdown.classList.add("hidden");
  setStage("login");
  setLoginError("");
});

$("retryNowBtn").addEventListener("click", () => pollOnce());

// Dashboard navigation
document.querySelectorAll(".nav-item").forEach(btn => {
  btn.addEventListener("click", () => switchView(btn.dataset.view));
});

$("quickNewBtn").addEventListener("click", () => switchView("submit"));
$("cancelFormBtn").addEventListener("click", () => switchView("dashboard"));

// Filters
searchInput.addEventListener("input", renderComplaints);
filterCategory.addEventListener("change", renderComplaints);
filterStatus.addEventListener("change", renderComplaints);

// Open a complaint from the table (delegated, so re-rendering is safe)
complaintsTableBody.addEventListener("click", (e) => {
  const btn = e.target.closest("[data-open]");
  if (btn) openComplaintModal(btn.dataset.open);
});

// Complaint window actions: assign, priority, accept, reject, progress
modalBody.addEventListener("click", async (e) => {
  const btn = e.target.closest("[data-action]");
  if (!btn || btn.disabled || !state.selectedId) return;

  const id = state.selectedId;
  const action = btn.dataset.action;
  btn.disabled = true;

  try {
    if (action === "assign") {
      const [mode, value] = $("modalAssignee").value.split(":");
      const body = mode === "staff" ? { staffUserId: value } : { strategy: value };
      const updated = await api(`/complaints/${id}/assign`, { method: "POST", body });
      showToast(`${id} assigned to ${updated.assignedStaff}. Waiting for them to accept.`);

    } else if (action === "priority") {
      await api(`/complaints/${id}/priority`, {
        method: "PUT",
        body: { priority: $("modalPriority").value }
      });
      showToast(`Priority of ${id} updated.`);

    } else if (action === "accept") {
      await api(`/complaints/${id}/accept`, { method: "POST" });
      showToast(`${id} accepted. Work has started.`);

    } else if (action === "reject") {
      const reason = $("modalReason").value.trim();
      if (!reason) {
        showToast("Enter a reason before rejecting.", true);
        return;
      }
      if (!window.confirm(`Reject ${id}? It will go back to the admin for reassignment.`)) {
        return;
      }
      await api(`/complaints/${id}/reject`, { method: "POST", body: { reason } });
      showToast(`${id} rejected and returned to the admin.`);

    } else if (action === "status") {
      const current = state.complaints.find(c => c.id === id);
      const newStatus = $("modalStatus").value;
      if (current && newStatus === current.status) {
        showToast("Choose a different status first.", true);
        return;
      }
      await api(`/complaints/${id}/status`, {
        method: "PUT",
        body: { status: newStatus, remarks: $("modalRemarks").value.trim() }
      });
      showToast(`${id} is now ${newStatus}.`);
    }

    await loadDashboard();
    // Reopen if the complaint is still visible to this user. Close it if it moved out of their view.
    if (state.complaints.some(c => c.id === id)) {
      await openComplaintModal(id);
    } else {
      closeModal();
    }
  } catch (err) {
    showToast(err.message, true);
    if (err.status === 404) {
      closeModal();
      loadDashboard().catch(() => {});
    }
  } finally {
    btn.disabled = false;
  }
});

$("closeModalBtn").addEventListener("click", closeModal);
detailModal.addEventListener("click", (e) => {
  if (e.target === detailModal) closeModal();
});

// Submit complaint (students only)
complaintForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const submitBtn = complaintForm.querySelector('button[type="submit"]');
  submitBtn.disabled = true;

  try {
    const created = await api("/complaints", {
      method: "POST",
      body: {
        title: $("cTitle").value.trim(),
        category: $("cCategory").value,
        location: $("cLocation").value.trim(),
        priority: $("cPriority").value,
        description: $("cDescription").value.trim()
      }
    });
    complaintForm.reset();
    switchView("dashboard");
    await loadDashboard();
    showToast(`Complaint ${created.complaintId} submitted successfully!`);
  } catch (err) {
    showToast(err.message, true);
  } finally {
    submitBtn.disabled = false;
  }
});

// Notifications dropdown
$("notifToggleBtn").addEventListener("click", (e) => {
  e.stopPropagation();
  notifDropdown.classList.toggle("hidden");
});
notifDropdown.addEventListener("click", (e) => e.stopPropagation());
$("markAllReadBtn").addEventListener("click", () => {
  state.unread = 0;
  renderNotifications();
});
document.addEventListener("click", () => notifDropdown.classList.add("hidden"));

// ---------- Start ----------
applyDemoAccount("student");
setStage("splash");