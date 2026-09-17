(function () {
  var $ = function (s) { return document.querySelector(s); };
  var $$ = function (s) { return Array.from(document.querySelectorAll(s)); };
  var api = window.VMS_API;
  var fx = window.VMS_FIXTURES;
  var visit = null;
  var reason = null;
  var live = false;
  var GATES = { "G-MAIN": "Main Gate", "G-PED": "Pedestrian Gate", "G-STAFF": "Staff Gate", "G-BUS": "Bus Bay" };

  function toast(msg, kind) {
    var box = $("#toast-box");
    if (!box) {
      console.log("[host-web]", msg);
      return;
    }
    var el = document.createElement("div");
    el.className = "toast " + (kind || "info");
    el.textContent = msg;
    box.appendChild(el);
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 2800);
  }

  function gateName(v) {
    return v.gateName || GATES[v.gateId] || v.gateId || "Main Gate";
  }

  function render(v) {
    visit = v;
    $("#visitor-name").textContent = v.visitorName;
    $("#visitor-purpose").textContent = v.purpose || "—";
    $("#visitor-gate").textContent = gateName(v);
    $("#visitor-mobile").textContent = api.formatMobile(v.mobile);
    $("#visit-id").textContent = v.id;
    $("#req-time").textContent = api.formatIst(v.createdAt || v.timeIn);
    $("#visitor-type").textContent = v.visitorType || "Parent";
    $("#visitor-type").className = "tag type-" + String(v.visitorType || "parent").toLowerCase();
    $("#visitor-avatar").textContent = api.initials(v.visitorName);
    var pending = v.status === "pending";
    var afterHours = !!v.afterHours;
    $("#action-row").classList.toggle("hidden", !pending);
    $("#btn-meeting").classList.toggle("hidden", v.status !== "inside");
    $("#fyi-badge").classList.toggle("hidden", !afterHours);
    $("#ah-hint").classList.toggle("hidden", !afterHours);
    $("#btn-ack-fyi").classList.toggle("hidden", !afterHours);
    $("#row-trigger").classList.toggle("hidden", !afterHours);
    $("#row-eval").classList.toggle("hidden", !afterHours);
    $("#row-decision").classList.toggle("hidden", !afterHours);
    $("#visitor-trigger").textContent = v.policyTrigger || "outside_hours";
    $("#visitor-eval").textContent = api.formatIst(v.afterHoursEvaluatedAt || v.createdAt);
    $("#btn-reject").disabled = afterHours;
    if (afterHours) {
      $("#ah-hint").textContent = "After hours / holiday visits need Security Head. You may Ack FYI — Host Approve does not move to Approved (AC-C4d).";
    }
    $("#status-badge").textContent = afterHours && pending
      ? "FYI · After hours · you cannot Approve"
      : pending
        ? "Awaiting your decision"
        : (v.status === "inside" ? "Inside campus" : (v.status === "approved" ? "Approved · on the way" : v.status));
    $("#confirm-view").classList.remove("visible");
    $("#request-view").classList.remove("hidden");
    $("#reject-panel").classList.remove("open");
    reason = null;
    $$("#reason-chips .chip").forEach(function (c) { c.classList.remove("active"); });
    $("#btn-confirm-reject").disabled = true;
  }

  function showConfirm(ok, title, sub, onway) {
    $("#request-view").classList.add("hidden");
    $("#confirm-view").classList.add("visible");
    $("#confirm-icon").className = "confirm-icon " + (ok ? "ok" : "no");
    $("#confirm-icon").innerHTML = ok
      ? '<svg class="icon icon-xl" viewBox="0 0 24 24"><path d="M20 6L9 17l-5-5"/></svg>'
      : '<svg class="icon icon-xl" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12"/></svg>';
    $("#confirm-title").textContent = title;
    $("#confirm-sub").textContent = sub;
    $("#onway-banner").style.display = onway ? "inline-flex" : "none";
  }

  async function loadPending() {
    if (live) {
      try {
        var rows = await api.listPending();
        var pick = (rows || []).find(function (v) { return !v.afterHours; }) || rows[0];
        if (pick) {
          render(pick);
          toast("Live pending · " + pick.visitorName, "info");
          return;
        }
      } catch (e) {
        toast(e.message || "Pending fetch failed", "warning");
      }
    }
    render(fx.pending);
    toast(live ? "No live pending · fixture Priya" : "FIXTURES · Priya awaiting host", "warning");
  }

  async function loadPriya() {
    if (live) {
      try {
        var v = await api.getVisit("V-20260916-014");
        if (v) {
          render(v);
          toast("Priya " + v.status + " · " + (v.passId || "P-4F21"), "info");
          return;
        }
      } catch (e) { toast(e.message, "warning"); }
    }
    render(fx.priya);
    toast(live ? "Priya seed not on this tenant · fixture" : "FIXTURES · Priya inside P-4F21", "warning");
  }

  async function loadAfterHours() {
    if (live) {
      try {
        var seeded = await api.getVisit("V-AH-VENDOR");
        if (seeded) {
          seeded.afterHours = seeded.afterHours !== false;
          seeded.policyTrigger = seeded.policyTrigger || fx.ravi.policyTrigger;
          seeded.afterHoursEvaluatedAt = seeded.afterHoursEvaluatedAt || fx.ravi.afterHoursEvaluatedAt;
          render(seeded);
          toast("After-hours · " + seeded.visitorName + " · " + (seeded.policyTrigger || "SH"), "warning");
          return;
        }
        var rows = await api.listPending();
        var found = (rows || []).find(function (v) { return v.afterHours; });
        if (found) {
          render(found);
          toast("After-hours pending · " + found.visitorName, "warning");
          return;
        }
      } catch (e) {
        toast(e.message || "After-hours fetch failed", "warning");
      }
    }
    render(fx.ravi);
    toast(live ? "No live after-hours visit · fixture Ravi" : "FIXTURES · Ravi after-hours (FYI)", "warning");
  }

  function storyFromUrl() {
    var q = new URLSearchParams(location.search);
    var tab = (q.get("tab") || q.get("story") || "").toLowerCase();
    var visitId = (q.get("visitId") || "").toUpperCase();
    var hash = (location.hash || "").replace("#", "").toLowerCase();
    if (visitId === "V-AH-VENDOR" || tab === "afterhours" || tab === "after-hours" || hash === "afterhours") {
      return "afterhours";
    }
    if (visitId === "V-20260916-014" || tab === "priya" || hash === "priya") return "priya";
    if (tab === "pending" || hash === "pending") return "pending";
    return "pending";
  }

  function setTab(id, push) {
    ["tab-pending", "tab-afterhours", "tab-priya"].forEach(function (t) {
      var el = $("#" + t);
      if (el) {
        var on = t === id;
        el.classList.toggle("active", on);
        el.setAttribute("aria-selected", on ? "true" : "false");
      }
    });
    if (push !== false) {
      var hash = id === "tab-afterhours" ? "#afterhours" : (id === "tab-priya" ? "#priya" : "#pending");
      if (location.hash !== hash) history.replaceState(null, "", hash);
    }
  }

  function bootStory(name) {
    if (name === "priya") {
      setTab("tab-priya", false);
      return loadPriya();
    }
    if (name === "afterhours") {
      setTab("tab-afterhours", false);
      return loadAfterHours();
    }
    setTab("tab-pending", false);
    return loadPending();
  }

  $("#tab-pending").addEventListener("click", function () {
    setTab("tab-pending");
    loadPending();
  });
  $("#tab-afterhours").addEventListener("click", function () {
    setTab("tab-afterhours");
    loadAfterHours();
  });
  $("#tab-priya").addEventListener("click", function () {
    setTab("tab-priya");
    loadPriya();
  });

  function stayPendingShRequired(detail) {
    if (visit) visit.status = "pending";
    $("#confirm-view").classList.remove("visible");
    $("#request-view").classList.remove("hidden");
    $("#action-row").classList.remove("hidden");
    $("#ah-hint").classList.remove("hidden");
    $("#ah-hint").textContent = (detail || "AFTER_HOURS_SH_REQUIRED") + " · still pending. Security Head must approve (AC-C4d).";
    $("#status-badge").textContent = "FYI · After hours · you cannot Approve";
    toast("AFTER_HOURS_SH_REQUIRED · still pending", "warning");
  }

  $("#btn-approve").addEventListener("click", async function () {
    try {
      if (visit && visit.afterHours && (!live || String(visit.id).startsWith("V-LOCAL") || visit.id === "V-AH-VENDOR")) {
        stayPendingShRequired("AFTER_HOURS_SH_REQUIRED");
        return;
      }
      if (live && visit && visit.id && !String(visit.id).startsWith("V-LOCAL")) {
        visit = await api.approve(visit.id);
        if (visit.afterHours || visit.status === "pending") {
          stayPendingShRequired((visit.policyTrigger || "AFTER_HOURS_SH_REQUIRED") + " · Host Approve did not clear");
          return;
        }
      } else {
        visit.status = "approved";
        visit.passId = visit.passId || "P-DEMO";
      }
      if (visit && visit.afterHours) {
        stayPendingShRequired("AFTER_HOURS_SH_REQUIRED");
        return;
      }
      showConfirm(true, "Approved", visit.visitorName + " can enter via " + gateName(visit) + ".", true);
      toast("Visitor approved · gate notified", "success");
    } catch (e) {
      if (e.code === "AFTER_HOURS_SH_REQUIRED" || /after hours|security head/i.test(e.message || "")) {
        stayPendingShRequired(e.code || "AFTER_HOURS_SH_REQUIRED");
        return;
      }
      showConfirm(false, "Could not approve", e.message, false);
      toast(e.message, "error");
    }
  });

  $("#btn-reject").addEventListener("click", function () {
    $("#reject-panel").classList.toggle("open");
  });

  $$("#reason-chips .chip").forEach(function (chip) {
    chip.addEventListener("click", function () {
      $$("#reason-chips .chip").forEach(function (c) { c.classList.remove("active"); });
      chip.classList.add("active");
      reason = chip.getAttribute("data-reason");
      $("#btn-confirm-reject").disabled = false;
    });
  });

  $("#btn-confirm-reject").addEventListener("click", async function () {
    if (!reason) return;
    try {
      if (live && visit && visit.id && !String(visit.id).startsWith("V-LOCAL")) {
        visit = await api.reject(visit.id, reason);
      } else {
        visit.status = "rejected";
        visit.rejectReason = reason;
      }
      showConfirm(false, "Rejected", "Reason: " + reason + ". Gate will inform the visitor.", false);
      toast("Rejection sent to gate", "warning");
    } catch (e) {
      showConfirm(false, "Could not reject", e.message, false);
      toast(e.message, "error");
    }
  });

  $("#btn-meeting").addEventListener("click", async function () {
    try {
      if (live && visit && visit.id && !String(visit.id).startsWith("V-LOCAL")) {
        visit = await api.meetingDone(visit.id);
      } else {
        visit.meetingDoneAt = new Date().toISOString();
      }
      showConfirm(true, "Meeting done", visit.visitorName + " — host marked meeting complete.", false);
      toast("Meeting done · gate can check out", "success");
    } catch (e) {
      showConfirm(false, "Could not mark meeting done", e.message, false);
      toast(e.message, "error");
    }
  });

  $("#btn-ack-fyi").addEventListener("click", function () {
    toast("FYI acknowledged · Security Head still must approve", "info");
  });

  $("#btn-reset").addEventListener("click", function () {
    if ($("#tab-priya").classList.contains("active")) loadPriya();
    else if ($("#tab-afterhours").classList.contains("active")) loadAfterHours();
    else loadPending();
  });

  function applyUser(user) {
    var name = (user && user.displayName) || "Host";
    var school = (user && user.schoolId) || "";
    $("#host-label").textContent = name + (school ? " · " + school : "");
    var pill = $("#source-pill");
    if (pill) {
      pill.textContent = "LIVE · " + (window.VMS_CONFIG.apiBase || "").replace("https://", "");
      pill.classList.remove("fallback");
    }
    var lp = $("#login-pill");
    if (lp) lp.textContent = "LIVE · " + name;
  }

  function showApp() {
    var login = $("#login-screen");
    var main = $("#app-main");
    if (login) login.classList.add("hidden");
    if (main) main.classList.remove("hidden");
    if (logoutBtn) logoutBtn.classList.remove("hidden");
  }

  function showLogin(err) {
    var login = $("#login-screen");
    var main = $("#app-main");
    if (main) main.classList.add("hidden");
    if (logoutBtn) logoutBtn.classList.add("hidden");
    if (login) login.classList.remove("hidden");
    if ($("#login-error")) $("#login-error").textContent = err || "";
    $("#host-label").textContent = "Sign in to continue";
    var lp = $("#login-pill");
    if (lp) {
      lp.textContent = "Typed sign-in · no demo auto-login";
      lp.classList.remove("fallback");
    }
    live = false;
    visit = null;
  }

  function enterApp(user) {
    live = true;
    applyUser(user);
    showApp();
    return bootStory(storyFromUrl());
  }

  var logoutBtn = $("#btn-logout");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", function () {
      if (api.logout) api.logout();
      showLogin("");
    });
  }

  var loginForm = $("#login-form");
  if (loginForm) {
    loginForm.addEventListener("submit", async function (ev) {
      ev.preventDefault();
      var u = ($("#login-user") && $("#login-user").value || "").trim();
      var p = ($("#login-pass") && $("#login-pass").value || "");
      var btn = $("#btn-login");
      if (btn) btn.disabled = true;
      if ($("#login-error")) $("#login-error").textContent = "";
      try {
        var boot = await api.login(u, p);
        if ($("#login-pass")) $("#login-pass").value = "";
        await enterApp(boot.user);
      } catch (e) {
        var msg = (e && e.message) || "Sign-in failed";
        if (/failed to fetch|networkerror|tunnel timeout/i.test(msg)) {
          msg = "Could not reach API. Check the tunnel, then try again.";
        }
        showLogin(msg);
      } finally {
        if (btn) btn.disabled = false;
      }
    });
  }

  // Restore only a previous typed session — never auto-login demo host/host123.
  if (api.restoreSession && api.restoreSession()) {
    enterApp(api.currentUser && api.currentUser());
  } else {
    showLogin("");
  }
})();
