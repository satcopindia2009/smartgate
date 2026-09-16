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
    $("#action-row").classList.toggle("hidden", !pending);
    $("#btn-meeting").classList.toggle("hidden", v.status !== "inside");
    $("#status-badge").textContent = pending
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
        if (rows.length) {
          render(rows[0]);
          toast("Live pending · " + rows[0].visitorName, "info");
          return;
        }
      } catch (e) {
        toast(e.message || "Pending fetch failed", "warning");
      }
    }
    render(fx.pending);
    if (!live) toast("FIXTURES · Priya awaiting Anita", "warning");
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
    if (!live) toast("FIXTURES · Priya inside P-4F21", "warning");
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
        visit.passId = visit.passId || "P-DEMO";
      }
      showConfirm(true, "Approved", visit.visitorName + " can enter via " + gateName(visit) + ".", true);
      toast("Visitor approved · gate notified", "success");
    } catch (e) {
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

  $("#btn-reset").addEventListener("click", function () {
    if ($("#tab-priya").classList.contains("active")) loadPriya();
    else loadPending();
  });

  api.warmup().then(function (boot) {
    live = boot.live;
    var pill = $("#source-pill");
    $("#host-label").textContent = "Anita Joshi · Primary Coordinator";
    if (live) {
      pill.textContent = "LIVE mock · " + window.VMS_CONFIG.apiBase.replace("https://", "");
      pill.classList.remove("fallback");
    } else {
      pill.textContent = "FIXTURES · tunnel unreachable";
      pill.classList.add("fallback");
    }
    return loadPending();
  });
})();
