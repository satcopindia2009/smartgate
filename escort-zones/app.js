(function () {
  var $ = function (s) { return document.querySelector(s); };
  var $$ = function (s) { return Array.from(document.querySelectorAll(s)); };
  var api = window.VMS_EZ_API;
  var fx = window.VMS_EZ_FIXTURES;
  var live = false;
  var visit = Object.assign({}, fx.ravi);
  var zones = fx.zones.slice();
  var rules = fx.rules.slice();
  var escorts = (fx.escorts || []).filter(function (s) { return s && s.id === "E01"; });
  if (!escorts.length) escorts = [{ id: "E01", name: "Vikram More", roleTitle: "Gate / lobby · on duty" }];
  var selectedStaffId = "E01";
  var storyPainted = false;
  var liveStaffLoaded = false;
  var BLOCKED_STAFF_IDS = { E02: true, E03: true };
  var TYPE_ORDER = ["Vendor", "Parent", "Guest", "Official", "Alumni"];
  var PAINTED_PASS = "P-7K88";
  var BADGE_HREF = "../visitor-qr/index.html?passId=P-7K88&story=escort";

  function showState(id, push) {
    $$(".state").forEach(function (el) { el.classList.remove("active"); });
    $$(".story-btn").forEach(function (el) { el.classList.remove("active"); });
    var s = $("#state-" + id);
    if (s) s.classList.add("active");
    var btn = document.querySelector('.story-btn[data-state="' + id + '"]');
    if (btn) btn.classList.add("active");
    if (storyPainted) {
      var loadBtn = $("#btn-load-p7k88");
      if (loadBtn) loadBtn.classList.add("active");
    }
    if (push !== false) {
      var hash = "#" + id;
      if (location.hash !== hash) history.replaceState(null, "", hash);
    }
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
  window.showState = showState;

  function zoneLabel(key) {
    var z = zones.find(function (row) { return row.key === key; });
    return (z && z.label) || key;
  }

  function zoneChips(keys, size) {
    return (keys || []).map(function (key) {
      var pad = size === "sm" ? "padding:2px 8px;font-size:10px;" : "";
      return '<span class="zone-chip" style="' + pad + '">' + zoneLabel(key) + "</span>";
    }).join("");
  }

  function isBlockedStaffId(id) {
    return !!(id && BLOCKED_STAFF_IDS[id]);
  }

  function isKnownStaffId(id) {
    return !!(id && id === "E01" && escorts.some(function (s) { return s.id === id; }) && !isBlockedStaffId(id));
  }

  function preferredEscort() {
    return escorts.find(function (s) { return s.id === "E01"; })
      || { id: "E01", name: "Vikram More", roleTitle: "Gate / lobby · on duty" };
  }

  function adoptLiveStaff(rows) {
    var vikram = (rows || []).filter(function (s) {
      return s && s.id === "E01" && !isBlockedStaffId(s.id);
    }).map(function (s) {
      return { id: "E01", name: s.name || "Vikram More", roleTitle: s.roleTitle || "Escort staff" };
    });
    escorts = vikram.length ? vikram : [preferredEscort()];
    escorts = escorts.filter(function (s) { return s.id === "E01"; });
    liveStaffLoaded = true;
    selectedStaffId = "E01";
    if (visit.escortStaffId && !isKnownStaffId(visit.escortStaffId)) {
      visit.escortStaffId = null;
      if (visit.escortName && !visit.escortWaived) visit.escortName = null;
    }
  }

  function escortCleared() {
    if (visit.escortWaived) return true;
    return isKnownStaffId(visit.escortStaffId);
  }

  function renderAssign() {
    $("#ravi-name").textContent = visit.visitorName || "Ravi Deshmukh";
    $("#ravi-meta").textContent = (visit.visitorType || "Vendor") + " · " + (visit.purpose || "A/C maintenance — Block B") +
      (visit.afterHours ? " · After hours Approved" : "");
    $("#ravi-status").textContent = visit.status === "inside" ? "Inside" : (visit.status || "Approved");
    $("#ravi-escort-flag").textContent = visit.escortWaived ? "Escort waived" : (visit.escortRequired ? "Escort required" : "Escort off");
    $("#assign-zones").innerHTML = zoneChips(visit.allowedZones || ["reception", "admin"]);
    $("#ravi-id-note").textContent = "visitId " + (visit.id || "V-EZ-VENDOR") +
      (visit.passId ? " · pass " + visit.passId : "") + " · Gate assign · SH waive · B4-E5 · DEMO · not live";

    $("#staff-grid").innerHTML = escorts.length
      ? escorts.map(function (s) {
        var active = s.id === selectedStaffId || s.id === visit.escortStaffId ? " active" : "";
        return '<button type="button" class="staff-card' + active + '" data-id="' + s.id + '">' +
          "<strong>" + s.name + "</strong><span>" + s.id + " · " + (s.roleTitle || "Escort staff") + "</span></button>";
      }).join("")
      : '<p class="demo-note">No staff from GET /staff?active=true — cannot assign.</p>';

    $$("#staff-grid .staff-card").forEach(function (card) {
      card.addEventListener("click", function () { pickStaff(card.getAttribute("data-id")); });
    });

    var picked = escorts.find(function (s) {
      return s.id === selectedStaffId || s.id === visit.escortStaffId;
    }) || null;
    var sel = $("#staff-selected");
    if (sel) {
      sel.textContent = picked
        ? ("Selected · " + picked.name + " · " + picked.id + (liveStaffLoaded ? " · live staff" : " · fixtures"))
        : "Select escort staff (name + id) before Issue / Assign. Default demo: E01 Vikram More.";
    }

    var ok = escortCleared();
    $("#escort-block").style.display = ok ? "none" : "flex";
    $("#escort-ok").style.display = ok ? "flex" : "none";
    $("#btn-issue").disabled = !ok;
    if (visit.escortWaived) {
      $("#escort-ok-title").textContent = "Escort waived · ready to print pass";
      $("#escort-ok-sub").textContent = (visit.escortWaiveReason || "SH waive") + " · zones stamped";
    } else if (visit.escortName || selectedStaffId) {
      var name = visit.escortName || (escorts.find(function (s) { return s.id === selectedStaffId; }) || {}).name || "Escort";
      $("#escort-ok-title").textContent = "Escort assigned · ready to print pass";
      $("#escort-ok-sub").textContent = name + " · Gate staff · zones stamped";
    }
    renderStamps();
    renderAudit();
  }

  function escortStaff(staffId) {
    if (staffId && isKnownStaffId(staffId)) {
      return escorts.find(function (s) { return s.id === staffId; });
    }
    return preferredEscort() || { id: "E01", name: "Vikram More", roleTitle: "Gate / lobby · on duty" };
  }

  function paintEscortStory(staffId) {
    var staff = escortStaff(staffId);
    var zonesKeys = (visit.allowedZones && visit.allowedZones.length) ? visit.allowedZones : ["reception", "admin"];
    visit = Object.assign({}, fx.ravi, visit, {
      visitorName: visit.visitorName || fx.ravi.visitorName,
      visitorType: visit.visitorType || "Vendor",
      escortRequired: true,
      escortStaffId: staff.id,
      escortName: staff.name,
      escortWaived: false,
      allowedZones: zonesKeys,
    });
    selectedStaffId = staff.id;
    storyPainted = true;
    var loadBtn = $("#btn-load-p7k88");
    if (loadBtn) loadBtn.classList.add("active");
  }

  async function loadP7K88Story() {
    var staff = preferredEscort() || escortStaff("E01");
    paintEscortStory(staff.id);
    var result = $("#assign-result");
    if (result) {
      result.classList.add("visible");
      result.textContent = "DEMO story · P-7K88 · escort " + visit.escortName + " · " + staff.id +
        " · chips from fixtures (live GET /passes/P-7K88 may still be Deepak holiday)";
    }
    if (live && visit.id && !String(visit.id).startsWith("V-EZ-") && isKnownStaffId(staff.id)) {
      try {
        var updated = await api.assignEscort(visit.id, staff.id);
        visit = Object.assign({}, visit, updated);
        if (result) result.textContent = "LIVE assign-escort · " + staff.id + " " + (updated.escortName || staff.name) +
          " · P-7K88 chips (story paints fixtures if live pass lacks escort)";
      } catch (e) {
        if (result) result.textContent = (e.code ? e.code + " · " : "") + (e.message || "Assign failed") +
          " · P-7K88 chips still from fixtures";
      }
    }
    renderAssign();
    showState("pass");
  }

  function renderStamps() {
    var name = visit.escortWaived ? "— (waived)" : (visit.escortName || "Vikram More");
    $("#stamp-escort-name").textContent = name;
    $("#stamp-pass").textContent = PAINTED_PASS;
    $("#stamp-zones").innerHTML = zoneChips(visit.allowedZones || ["reception", "admin"]);
    $("#pass-id").textContent = PAINTED_PASS;
    $("#pass-escort-stamp").textContent = visit.escortWaived
      ? "Escort waived"
      : ("Escort: " + (visit.escortName || "Vikram More"));
    $("#pass-zone-stamps").innerHTML = (visit.allowedZones || ["reception", "admin"]).map(function (key) {
      return '<span class="stamp stamp-zone">' + zoneLabel(key) + "</span>";
    }).join("");
    $("#pass-soft").textContent = (visit.gateName || "Main Gate") + " · " + api.formatIst(visit.createdAt || visit.afterHoursEvaluatedAt);
    $("#pass-note").textContent = "Pass id P-7K88 · Host " + (visit.hostName || "Anita Joshi") +
      (visit.passId && visit.passId !== PAINTED_PASS ? " · live " + visit.passId : "") +
      (storyPainted ? " · story chips from fixtures" : "");
    var qr = $("#pass-qr-link");
    if (qr) qr.href = BADGE_HREF;
  }

  function renderAudit() {
    var rows = [visit, fx.suresh];
    $("#audit-body").innerHTML = rows.map(function (v, i) {
      var initials = (v.visitorName || "??").split(/\s+/).map(function (p) { return p[0]; }).join("").slice(0, 2);
      var escort = v.escortWaived ? "— (waived)" : (v.escortName || (i === 0 && visit.escortName) || "—");
      var waived = v.escortWaived ? '<span class="tag" style="background:var(--orange-dim);color:#fbbf24;">SH waive</span>' : "—";
      var status = v.status === "inside" || (i === 0 && visit.escortStaffId)
        ? '<span class="status-pill status-inside">Inside</span>'
        : '<span class="status-pill" style="background:var(--green-dim);color:#4ade80;">Checked out</span>';
      if (i === 0 && !escortCleared()) status = '<span class="status-pill status-approved">Approved</span>';
      return "<tr><td><div style=\"display:flex;gap:8px;align-items:center;\">" +
        '<div class="avatar avatar-sm avatar-' + (i === 0 ? "orange" : "teal") + '">' + initials + "</div><strong>" +
        v.visitorName + "</strong></div></td>" +
        '<td style="color:var(--cyan-bright);font-weight:600;">' + (v.passId || "—") + "</td>" +
        "<td>" + escort + "</td><td>" + zoneChips(v.allowedZones || [], "sm") + "</td>" +
        "<td>" + waived + "</td><td>" + status + "</td></tr>";
    }).join("");
  }

  function renderEditor() {
    $("#zone-fields").innerHTML = zones.map(function (z) {
      return '<div class="field"><label>' + z.key + '</label><input data-zone="' + z.key + '" value="' +
        String(z.label || "").replace(/"/g, "&quot;") + '" /></div>';
    }).join("");
    $("#rules-list").innerHTML = TYPE_ORDER.map(function (type) {
      var rule = rules.find(function (r) { return r.visitorType === type; }) || { visitorType: type, escortRequired: false, allowedZones: ["reception"] };
      var tag = "tag-" + type.toLowerCase();
      var checks = (type === "Vendor" ? ["reception", "admin", "lab"] : type === "Parent" ? ["reception", "classroom"] : ["reception"]).map(function (key) {
        var on = (rule.allowedZones || []).indexOf(key) >= 0 ? " checked" : "";
        return '<label><input type="checkbox" data-type="' + type + '" data-zone="' + key + '"' + on + " /> " + key + "</label>";
      }).join("");
      return '<div class="rule-row" data-type="' + type + '">' +
        '<span class="tag ' + tag + '">' + type + "</span>" +
        '<button type="button" class="toggle' + (rule.escortRequired ? " on" : "") + '" data-type="' + type + '" title="escort_required"></button>' +
        '<span style="font-size:12px;color:var(--text-muted);">' + (rule.escortRequired ? "Escort ON" : "Escort off") + "</span>" +
        '<div class="zone-edit">' + checks + "</div></div>";
    }).join("");
    $$("#rules-list .toggle").forEach(function (tog) {
      tog.addEventListener("click", function () {
        var type = tog.getAttribute("data-type");
        var rule = rules.find(function (r) { return r.visitorType === type; });
        if (rule) rule.escortRequired = !rule.escortRequired;
        renderEditor();
      });
    });
  }

  async function pickStaff(staffId) {
    var result = $("#assign-result");
    result.classList.add("visible");
    if (isBlockedStaffId(staffId) || !isKnownStaffId(staffId)) {
      selectedStaffId = "E01";
      result.textContent = "Unknown escortStaffId · picker is E01 Vikram More only. Never send E02/E03.";
      $("#btn-issue").disabled = !isKnownStaffId(visit.escortStaffId);
      renderAssign();
      return;
    }
    var staff = escorts.find(function (s) { return s.id === staffId; });
    if (live && visit.id && !String(visit.id).startsWith("V-EZ-")) {
      try {
        var updated = await api.assignEscort(visit.id, staffId);
        visit = Object.assign({}, visit, updated);
        selectedStaffId = staffId;
        result.textContent = "LIVE assign-escort · " + staffId + " " + (updated.escortName || (staff && staff.name) || "");
      } catch (e) {
        selectedStaffId = null;
        result.textContent = (e.code ? e.code + " · " : "") + (e.message || "Assign failed");
        renderAssign();
        return;
      }
    } else {
      selectedStaffId = staffId;
      visit.escortStaffId = staffId;
      visit.escortName = staff ? staff.name : staffId;
      visit.escortWaived = false;
      result.textContent = "FIXTURES assign · " + visit.escortName + " · " + staffId;
    }
    storyPainted = true;
    var loadBtn = $("#btn-load-p7k88");
    if (loadBtn) loadBtn.classList.add("active");
    renderAssign();
  }

  $$(".story-btn").forEach(function (btn) {
    if (btn.id === "btn-load-p7k88") return;
    btn.addEventListener("click", function () { showState(btn.dataset.state); });
  });
  var loadBtn = $("#btn-load-p7k88");
  if (loadBtn) loadBtn.addEventListener("click", function () { loadP7K88Story(); });
  window.addEventListener("hashchange", function () {
    var id = (location.hash || "#assign").slice(1);
    if (id) showState(id, false);
  });

  $("#btn-issue").addEventListener("click", function () {
    if ($("#btn-issue").disabled) return;
    showState("stamps");
  });

  $("#btn-waive").addEventListener("click", function () {
    $("#waive-box").classList.toggle("open");
  });

  $("#btn-confirm-waive").addEventListener("click", async function () {
    var reason = ($("#waive-reason").value || "").trim();
    var result = $("#waive-result");
    result.classList.add("visible");
    if (!reason) {
      result.textContent = "Reason required for SH waive.";
      return;
    }
    if (live && visit.id && !String(visit.id).startsWith("V-EZ-")) {
      try {
        var updated = await api.waiveEscort(visit.id, reason);
        visit = Object.assign({}, visit, updated);
        result.textContent = "LIVE waive-escort · escort_waived";
      } catch (e) {
        result.textContent = (e.code ? e.code + " · " : "") + (e.message || "Waive failed · SH only");
        return;
      }
    } else {
      visit.escortWaived = true;
      visit.escortWaiveReason = reason;
      visit.escortStaffId = null;
      visit.escortName = null;
      result.textContent = "FIXTURES SH waive · " + reason + " · no escortStaffId";
    }
    selectedStaffId = null;
    visit.escortStaffId = null;
    if (visit.escortWaived) visit.escortName = null;
    renderAssign();
  });

  $("#btn-save-rules").addEventListener("click", async function () {
    $$('#zone-fields input[data-zone]').forEach(function (inp) {
      var z = zones.find(function (row) { return row.key === inp.getAttribute("data-zone"); });
      if (z) z.label = inp.value;
    });
    $$("#rules-list .rule-row").forEach(function (row) {
      var type = row.getAttribute("data-type");
      var rule = rules.find(function (r) { return r.visitorType === type; });
      if (!rule) return;
      rule.escortRequired = !!row.querySelector(".toggle.on");
      rule.allowedZones = Array.from(row.querySelectorAll("input[type=checkbox]:checked")).map(function (c) {
        return c.getAttribute("data-zone");
      });
      if ((rule.allowedZones || []).indexOf("restricted") >= 0) rule.escortRequired = true;
    });
    var result = $("#editor-result");
    result.classList.add("visible");
    if (live) {
      try {
        var payload = rules.map(function (r) {
          return { visitorType: r.visitorType, escortRequired: !!r.escortRequired, allowedZones: r.allowedZones || [] };
        });
        await api.putEscortRules(payload);
        for (var i = 0; i < zones.length; i++) {
          await api.patchZone(zones[i].key, zones[i].label);
        }
        result.textContent = "LIVE rules + zone labels saved (SH).";
      } catch (e) {
        result.textContent = (e.code ? e.code + " · " : "") + (e.message || "Save failed · SH / admin only");
        return;
      }
    } else {
      result.textContent = "FIXTURES save · local only";
    }
    var vendor = rules.find(function (r) { return r.visitorType === "Vendor"; });
    if (vendor && !visit.escortStaffId) {
      visit.escortRequired = vendor.escortRequired;
      visit.allowedZones = vendor.allowedZones.slice();
    }
    renderAssign();
    renderEditor();
  });

  renderAssign();
  renderEditor();
  var bootParams = new URLSearchParams(location.search);
  var bootStory = (bootParams.get("story") || "").toLowerCase();
  var boot = (location.hash || "#assign").slice(1);
  if (bootStory === "escort" || bootStory === "p7k88" || boot === "load-p7k88") {
    loadP7K88Story();
  } else if (boot) {
    showState(boot, false);
  }

  api.warmup().then(async function (bootLive) {
    live = bootLive.live;
    var pill = $("#source-pill");
    if (live) {
      pill.textContent = "LIVE mock · " + window.VMS_CONFIG.apiBase.replace("https://", "");
      pill.classList.remove("fallback");
      try {
        var liveZones = await api.listZones();
        if (liveZones && liveZones.length) zones = liveZones;
        var liveRules = await api.listEscortRules();
        if (liveRules && liveRules.length) rules = liveRules;
        var staff = await api.listStaff();
        if (staff && staff.length) adoptLiveStaff(staff);
      } catch (e) { /* keep fixtures */ }
      try {
        var rows = await api.listVendorVisits();
        var found = (rows || []).find(function (v) {
          return v.visitorName === "Ravi Deshmukh" && v.status === "pending";
        }) || (rows || []).find(function (v) {
          return v.visitorName === "Ravi Deshmukh" && v.status === "approved";
        }) || (rows || []).find(function (v) {
          return v.escortRequired && (v.status === "pending" || v.status === "approved");
        });
        if (found && !storyPainted) {
          visit = Object.assign({}, fx.ravi, found, { hostName: found.hostName || "Anita Joshi" });
          if (found.escortStaffId && isKnownStaffId(found.escortStaffId)) {
            selectedStaffId = found.escortStaffId;
            storyPainted = true;
          } else if (found.escortStaffId && !isKnownStaffId(found.escortStaffId)) {
            visit.escortStaffId = null;
            visit.escortName = found.escortName && found.escortWaived ? found.escortName : null;
          }
        } else if (found && storyPainted) {
          visit.id = found.id || visit.id;
          if (found.passId) visit.passId = found.passId;
        }
      } catch (e) { /* keep fixtures */ }
      renderAssign();
      renderEditor();
    } else {
      pill.textContent = "FIXTURES · tunnel unreachable";
      pill.classList.add("fallback");
    }
  });
})();
