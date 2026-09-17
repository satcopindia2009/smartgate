(function () {
  var $ = function (s) { return document.querySelector(s); };
  var $$ = function (s) { return Array.from(document.querySelectorAll(s)); };
  var api = window.VMS_AH_API;
  var fx = window.VMS_AH_FIXTURES;
  var live = false;
  var ravi = Object.assign({}, fx.ravi);
  var hours = fx.hours.slice();
  var holidays = fx.holidays.slice();
  var DAY = { mon: "Monday", tue: "Tuesday", wed: "Wednesday", thu: "Thursday", fri: "Friday", sat: "Saturday", sun: "Sunday" };

  function showState(id, push) {
    $$(".state").forEach(function (el) { el.classList.remove("active"); });
    $$(".story-btn").forEach(function (el) { el.classList.remove("active"); });
    var s = $("#state-" + id);
    if (s) s.classList.add("active");
    var btn = document.querySelector('.story-btn[data-state="' + id + '"]');
    if (btn) btn.classList.add("active");
    if (push !== false) {
      var hash = "#" + id;
      if (location.hash !== hash) history.replaceState(null, "", hash);
    }
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
  window.showState = showState;

  function clockLabel(iso) {
    var d = iso ? new Date(iso) : new Date();
    return d.toLocaleString("en-IN", {
      weekday: "short", day: "numeric", month: "short",
      hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "Asia/Kolkata",
    }) + " IST";
  }

  function weekdayClose() {
    var fri = hours.find(function (h) { return h.weekday === "fri" || h.weekday === "mon"; });
    return (fri && fri.closeTime) || "18:00";
  }

  function weekdayWindow() {
    var mon = hours.find(function (h) { return h.weekday === "mon"; }) || hours[0];
    if (!mon || mon.closed) return "Closed";
    return "Mon–Fri " + (mon.openTime || "08:00") + "–" + (mon.closeTime || "18:00");
  }

  function renderHours() {
    var tb = $("#hours-table tbody");
    if (!tb) return;
    tb.innerHTML = hours.map(function (row) {
      if (row.closed || !row.openTime) {
        return "<tr><td>" + (DAY[row.weekday] || row.weekday) + "</td><td colspan=\"2\"><span class=\"tag\" style=\"background:var(--red-dim);color:#f87171;\">Closed</span></td></tr>";
      }
      return "<tr><td>" + (DAY[row.weekday] || row.weekday) + "</td><td>" + row.openTime + "</td><td>" + row.closeTime + "</td></tr>";
    }).join("");
    var list = $("#holiday-list");
    if (list) {
      list.innerHTML = holidays.map(function (h) {
        return '<div class="holiday-item"><div><strong>' + h.date + "</strong><div style=\"font-size:11px;color:var(--text-dim);\">" +
          (h.label || "Holiday") + "</div></div></div>";
      }).join("") || '<div class="holiday-item"><div><strong>None</strong></div></div>';
    }
    var win = $("#sh-window");
    if (win) win.textContent = weekdayWindow();
  }

  function renderRavi() {
    $("#ravi-name").textContent = ravi.visitorName;
    $("#ravi-meta").textContent = api.formatMobile(ravi.mobile) + " · " + ravi.visitorType + " · " + ravi.purpose;
    $("#ravi-host").textContent = (ravi.hostName || "Anita Joshi") + " · Primary Coordinator (H03)";
    $("#ravi-trigger").textContent = ravi.policyTrigger || "outside_hours";
    $("#ravi-eval").textContent = api.formatIst(ravi.afterHoursEvaluatedAt || ravi.createdAt);
    $("#ravi-id").textContent = ravi.id || "V-AH-VENDOR";
    $("#ravi-status").textContent = ravi.status === "pending" ? "Pending · SH" : ravi.status;
    $("#gate-trigger").textContent = ravi.policyTrigger || "outside_hours";
    $("#gate-banner-body").innerHTML = "Campus hours ended at <strong>" + weekdayClose() +
      "</strong>. This visit is flagged <strong>after_hours</strong> (" +
      (ravi.policyTrigger || "outside_hours") + "). Host can be notified FYI, but only Security Head can Approve or Reject.";
    $("#gate-clock").textContent = clockLabel(ravi.afterHoursEvaluatedAt || ravi.createdAt);
    var shReg = $("#sh-registered");
    if (shReg) shReg.textContent = api.formatIst(ravi.createdAt);
  }

  function renderDeepak(v) {
    if (!v) return;
    if (v.passId && $("#deepak-pass")) $("#deepak-pass").textContent = v.passId;
    if (v.id && $("#deepak-id")) $("#deepak-id").textContent = v.id;
    if (v.status && $("#deepak-status")) {
      $("#deepak-status").textContent = v.status === "inside" ? "Inside" : (v.status === "approved" ? "Approved" : v.status);
    }
  }

  $$(".story-btn").forEach(function (btn) {
    btn.addEventListener("click", function () { showState(btn.dataset.state); });
  });
  function syncShButtons() {
    var ok = !!selectedReason();
    $("#btn-sh-approve").disabled = !ok;
    $("#btn-sh-reject").disabled = !ok;
  }

  $$(".reason-chips .chip").forEach(function (chip) {
    chip.addEventListener("click", function () {
      chip.parentElement.querySelectorAll(".chip").forEach(function (c) { c.classList.remove("active"); });
      chip.classList.add("active");
      var box = $("#sh-reason");
      if (box && !box.value.trim()) box.value = chip.textContent.trim();
      syncShButtons();
    });
  });
  if ($("#sh-reason")) {
    $("#sh-reason").addEventListener("input", syncShButtons);
  }
  window.addEventListener("hashchange", function () {
    var id = (location.hash || "#gate").slice(1);
    if (id) showState(id, false);
  });

  $("#btn-ack-fyi").addEventListener("click", function () {
    var el = $("#host-result");
    el.textContent = "FYI acknowledged · Security Head still must approve (AC-C4d).";
    el.classList.add("visible");
  });

  function selectedReason() {
    var chip = document.querySelector(".reason-chips .chip.active");
    var extra = ($("#sh-reason") && $("#sh-reason").value) || "";
    var chipText = chip ? chip.textContent.trim() : "";
    if (extra.trim()) return extra.trim();
    return chipText;
  }

  $("#btn-host-approve").addEventListener("click", async function () {
    var el = $("#host-result");
    el.classList.add("visible");
    if (live && ravi.id && !String(ravi.id).startsWith("V-LOCAL")) {
      try {
        await api.hostApprove(ravi.id);
        el.textContent = "Unexpected: host approve succeeded · check SH lock";
      } catch (e) {
        el.textContent = (e.code ? e.code + " · " : "") + (e.message || "Host Approve did not clear after-hours.");
      }
      return;
    }
    el.textContent = "AFTER_HOURS_SH_REQUIRED · Host Approve did not change status (AC-C4d).";
  });

  async function shClick(action) {
    var el = $("#sh-result");
    var reason = selectedReason();
    if (!reason) {
      el.textContent = "Reason required (A5/A6).";
      return;
    }
    if (live && ravi.id && !String(ravi.id).startsWith("V-LOCAL")) {
      try {
        var out = await api.shDecide(ravi.id, action, reason);
        el.textContent = "SH " + action + " · " + (out.status || "") + (out.passId ? " · " + out.passId : "");
        ravi = Object.assign({}, ravi, out);
        renderRavi();
        if (action === "approve") showState("holiday");
      } catch (e) {
        el.textContent = (e.code ? e.code + " · " : "") + (e.message || "SH decide failed");
      }
      return;
    }
    el.textContent = "FIXTURES · SH " + action + " recorded with reason (demo).";
    if (action === "approve") showState("holiday");
  }
  $("#btn-sh-approve").addEventListener("click", function () { shClick("approve"); });
  $("#btn-sh-reject").addEventListener("click", function () { shClick("reject"); });
  syncShButtons();

  renderHours();
  renderRavi();
  var boot = (location.hash || "#gate").slice(1);
  if (boot) showState(boot, false);

  api.warmup().then(async function (bootLive) {
    live = bootLive.live;
    var pill = $("#source-pill");
    if (live) {
      pill.textContent = "LIVE mock · " + window.VMS_CONFIG.apiBase.replace("https://", "");
      pill.classList.remove("fallback");
      try {
        var liveHours = await api.getHours();
        if (liveHours && liveHours.length) hours = liveHours;
        var liveHolidays = await api.listHolidays();
        if (liveHolidays && liveHolidays.length) holidays = liveHolidays;
        renderHours();
      } catch (e) { /* keep fixtures */ }
      try {
        var seeded = await api.getVisit("V-AH-VENDOR");
        var rows = await api.listPendingAfterHours();
        var found = seeded || (rows || []).find(function (v) { return v.visitorName === "Ravi Deshmukh"; });
        if (found) {
          ravi = Object.assign({}, fx.ravi, found, { hostName: "Anita Joshi" });
          renderRavi();
        }
      } catch (e) { /* keep fixtures */ }
      try {
        var hol = await api.getVisit("V-AH-HOLIDAY");
        if (hol) renderDeepak(hol);
        else {
          var pass = await api.getPass("P-7K88");
          if (pass) renderDeepak({ id: pass.visitId, passId: pass.passId, status: pass.status });
        }
      } catch (e) { /* keep fixtures */ }
    } else {
      pill.textContent = "FIXTURES · tunnel unreachable";
      pill.classList.add("fallback");
    }
  });
})();
