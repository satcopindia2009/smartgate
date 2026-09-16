(function () {
  var $ = function (s) { return document.querySelector(s); };
  var $$ = function (s) { return Array.from(document.querySelectorAll(s)); };
  var api = window.VMS_API;
  var fx = window.VMS_FIXTURES;
  var visit = null;
  var reason = null;
  var live = false;
  var GATES = { "G-MAIN": "Main Gate", "G-PED": "Pedestrian Gate", "G-STAFF": "Staff Gate", "G-BUS": "Bus Bay" };

  function toast(msg) {
    console.log("[host-web]", msg);
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
    var inside = v.status === "inside" || v.status === "approved";
    $("#action-row").classList.toggle("hidden", !pending);
    $("#btn-meeting").classList.toggle("hidden", v.status !== "inside");
    $("#status-badge").textContent = pending
      ? "Awaiting your decision"
      : (v.status === "inside" ? "Inside campus" : v.status);
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
      var rows = await api.listPending();
      if (rows.length) {
        render(rows[0]);
        return;
      }
    }
    render(fx.pending);
  }

  async function loadPriya() {
    if (live) {
      try {
        var v = await api.getVisit("V-20260916-014");
        if (v) {
          render(v);
          return;
        }
      } catch (e) { toast(e.message); }
    }
    render(fx.priya);
  }

  $("#tab-pending").addEventListener("click", function () {
    $("#tab-pending").classList.add("active");
    $("#tab-priya").classList.remove("active");
    loadPending();
  });
  $("#tab-priya").addEventListener("click", function () {
    $("#tab-priya").classList.add("active");
    $("#tab-pending").classList.remove("active");
    loadPriya();
  });

  $("#btn-approve").addEventListener("click", async function () {
    try {
      if (live && visit && visit.id && !String(visit.id).startsWith("V-LOCAL")) {
        visit = await api.approve(visit.id);
      } else {
        visit.status = "approved";
      }
      showConfirm(true, "Approved", visit.visitorName + " can enter via " + gateName(visit) + ".", true);
    } catch (e) {
      showConfirm(false, "Could not approve", e.message, false);
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
    } catch (e) {
      showConfirm(false, "Could not reject", e.message, false);
    }
  });

  $("#btn-meeting").addEventListener("click", async function () {
    try {
      if (live && visit && visit.id) {
        visit = await api.meetingDone(visit.id);
      } else {
        visit.meetingDoneAt = new Date().toISOString();
      }
      showConfirm(true, "Meeting done", visit.visitorName + " — host marked meeting complete.", false);
    } catch (e) {
      showConfirm(false, "Could not mark meeting done", e.message, false);
    }
  });

  $("#btn-reset").addEventListener("click", function () {
    if ($("#tab-priya").classList.contains("active")) loadPriya();
    else loadPending();
  });

  api.warmup().then(function (boot) {
    live = boot.live;
    var pill = $("#source-pill");
    if (live) {
      pill.textContent = "LIVE mock · " + window.VMS_CONFIG.apiBase;
      pill.classList.remove("fallback");
      if (boot.user && boot.user.displayName) {
        $("#host-label").textContent = boot.user.displayName + " · host";
      }
    } else {
      pill.textContent = "FIXTURES fallback · tunnel unreachable";
      pill.classList.add("fallback");
    }
    return loadPending();
  });
})();
