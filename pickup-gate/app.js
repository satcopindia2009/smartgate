(function () {
  var $ = function (sel, el) { return (el || document).querySelector(sel); };
  var $$ = function (sel, el) { return Array.prototype.slice.call((el || document).querySelectorAll(sel)); };
  var api = window.VMS_PICKUP_API;
  var cfg = window.VMS_PICKUP_CONFIG;

  var state = {
    panel: 1,
    live: false,
    student: null,
    authorized: [],
    custody: null,
    collector: null,
    reason: "sick",
    photo: false,
    photoBlob: null,
    consent: false,
    linkVisit: false,
    matchMethod: "manual_list_select",
    resultMode: null,
    pickup: null,
    story: "purpose",
    busy: false,
  };

  var KNOWN_STORIES = ["purpose", "lookup", "list", "happy", "notauth", "custody", "override"];

  function applyChrome() {
    var t = api.currentTenant();
    var user = api.currentUser() || {};
    var schoolEl = $("#school-chrome");
    if (schoolEl) {
      schoolEl.textContent = t.name + " · " + t.schoolId + " · " + t.schoolCode + " · Student pickup";
    }
    var gatePill = $("#gate-pill");
    if (gatePill) gatePill.textContent = t.gateName || "Main Gate";
    var account = $("#account-label");
    if (account) account.textContent = user.username || t.gateUser;
    var sh = $("#sh-hint");
    if (sh) {
      var fallback = t.shFallbackUser
        ? " (fallback <code>" + t.shFallbackUser + "</code> / <code>" + t.shFallbackPass + "</code>)"
        : "";
      sh.innerHTML = "Gate cannot self-override. Security Head only (reason required) · login <code>" +
        t.shUser + "</code> / <code>" + t.shPass + "</code>" + fallback + ":";
    }
    var userInput = $("#login-user");
    var passInput = $("#login-pass");
    if (userInput && !userInput.value) userInput.value = t.gateUser;
    if (passInput && !passInput.value) passInput.placeholder = t.gatePass;
  }

  function toast(msg, kind) {
    var box = $("#toast-box");
    if (!box) return;
    var el = document.createElement("div");
    el.className = "toast " + (kind || "info");
    el.textContent = msg;
    box.appendChild(el);
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 3200);
  }

  function setBusy(on) {
    state.busy = !!on;
    document.body.classList.toggle("is-busy", on);
    ["btn-complete", "btn-to-release", "btn-claim-notlist", "btn-match", "btn-request-override", "btn-sh-approve"].forEach(function (id) {
      var el = $("#" + id);
      if (!el) return;
      if (on) {
        el.setAttribute("data-busy", "1");
        el.disabled = true;
      } else if (el.getAttribute("data-busy") === "1") {
        el.removeAttribute("data-busy");
        if (id === "btn-complete") updateReleaseReady();
        else if (id === "btn-to-release") el.disabled = !state.collector;
        else el.disabled = false;
      }
    });
  }

  function tick() {
    var now = new Date();
    $("#gate-clock").textContent = now.toLocaleTimeString("en-IN", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true,
      timeZone: "Asia/Calcutta",
    }) + " IST";
  }
  tick();
  setInterval(tick, 1000);

  function showPanel(n) {
    state.panel = n;
    $$(".step-panel").forEach(function (p) {
      p.classList.toggle("active", Number(p.dataset.panel) === n);
    });
    $$(".step-dot").forEach(function (d) {
      var s = Number(d.dataset.step);
      d.classList.toggle("active", s === n);
      d.classList.toggle("done", s < n);
    });
  }

  function setStoryActive(name) {
    state.story = name;
    $$(".story-btn").forEach(function (b) {
      b.classList.toggle("active", b.dataset.story === name);
    });
    if (history.replaceState) history.replaceState(null, "", "#" + name);
  }

  function studentAvatarClass(s) {
    if (!s) return "avatar-cyan";
    if (/Kabir|Dev Joshi/i.test(s.name)) return "avatar-orange";
    if (/Patel|Shah/i.test(s.name)) return "avatar-blue";
    return "avatar-cyan";
  }

  function isCustodyFlag(flag) {
    return flag === "court_order" || flag === "restricted";
  }

  function decide(student, custody, collector) {
    if (!collector || collector.notOnList) return "blocked_auth";
    if (!api.inDate(collector) && !collector.demoBlock) return "blocked_auth";
    if (collector.blockedByCustody || collector.blocked || collector.demoBlock) return "blocked_custody";
    var flag = (custody && custody.flag) || "none";
    var instr = api.gateInstruction(custody && custody.gateInstruction);
    if (flag === "court_order" && !instr.trim()) return "blocked_custody";
    if (flag === "restricted" || flag === "court_order") {
      var allowed = custody && custody.allowedPersonIds;
      if (Array.isArray(allowed) && allowed.length && allowed.indexOf(collector.id) < 0) {
        return "blocked_custody";
      }
      var blockedIds = (custody && custody.blockedPersonIds) || [];
      if (blockedIds.indexOf(collector.id) >= 0) return "blocked_custody";
    }
    return "ok";
  }

  function matchPerson(list, mobileRaw, idRaw) {
    var mob = api.last4(mobileRaw);
    var id4 = String(idRaw || "").replace(/\D/g, "").slice(-4);
    var hit = null;
    var method = "none";
    if (mob) {
      hit = list.find(function (p) {
        return api.last4(p.mobile) === mob || api.last4(p.idLast4) === mob;
      });
      if (hit) method = "mobile";
    }
    if (!hit && id4) {
      hit = list.find(function (p) { return String(p.idLast4 || "") === id4; });
      if (hit) method = "id";
    }
    return { hit: hit, method: method };
  }

  function renderLookup(q) {
    var box = $("#lookup-results");
    var note = $("#disambig-note");
    var query = (q || "").trim().toLowerCase();
    var hits = (state._lookupCache || []).slice();
    if (!state.live && !hits.length) {
      hits = api.fixtureStudents();
    }
    if (query) {
      hits = hits.filter(function (s) {
        var label = (s.name + " " + api.classLabel(s) + " " + (s.class || "") + (s.section || "")).toLowerCase();
        return label.indexOf(query) >= 0;
      });
    }
    var aaravHits = hits.filter(function (s) { return /^aarav/i.test(s.name); });
    if (
      api.currentTenant().id === "demo" &&
      query.indexOf("aarav") >= 0 &&
      aaravHits.length >= 2 &&
      !/\d/.test(query) &&
      query.indexOf("-") < 0 &&
      query.indexOf("mehta") < 0 &&
      query.indexOf("patel") < 0
    ) {
      note.classList.add("visible");
      note.textContent = "Multiple Aaravs — pick class/section to continue (e.g. Aarav Mehta 5-B vs Aarav Patel 3-A).";
    } else {
      note.classList.remove("visible");
      note.textContent = "";
    }
    if (!hits.length) {
      box.innerHTML = '<div style="padding:16px;color:var(--text-muted);font-size:13px;">No student found. Ask Admin to add — no free-text release.</div>';
    } else {
      box.innerHTML = hits.map(function (s) {
        var custodyMark = s._custodyFlag === "court_order" ? " · COURT ORDER"
          : s._custodyFlag === "restricted" ? " · RESTRICTED" : "";
        return (
          '<button type="button" class="lookup-item" data-id="' + s.id + '" role="option">' +
            '<div class="avatar avatar-lg ' + studentAvatarClass(s) + '">' + api.initials(s.name) + "</div>" +
            "<div><strong>" + s.name + "</strong><span>" + api.classLabel(s) + custodyMark + "</span></div>" +
          "</button>"
        );
      }).join("");
    }
    box.classList.add("open");
    $$(".lookup-item", box).forEach(function (btn) {
      btn.addEventListener("click", function () { selectStudent(btn.dataset.id); });
    });
    if (state.student) {
      $$(".lookup-item", box).forEach(function (el) {
        var on = el.dataset.id === state.student.id;
        el.style.outline = on ? "1px solid var(--cyan)" : "";
        el.style.background = on ? "var(--cyan-dim)" : "";
      });
    }
  }

  async function refreshLookup(q) {
    try {
      var rows = await api.searchStudents(q);
      state._lookupCache = rows;
      await Promise.all(rows.slice(0, 12).map(function (s) {
        return api.getCustody(s.id).then(function (c) { s._custodyFlag = c && c.flag; }).catch(function () {});
      }));
    } catch (e) {
      toast(e.message || "Student search failed", "warning");
      if (!state._lookupCache) state._lookupCache = api.fixtureStudents();
    }
    renderLookup(q);
  }

  async function selectStudent(id) {
    var fromCache = (state._lookupCache || []).find(function (s) { return s.id === id; });
    try {
      state.student = fromCache || await api.getStudent(id);
      var pack = await Promise.all([
        api.listAuthorized(id),
        api.getCustody(id),
      ]);
      state.authorized = pack[0] || [];
      state.custody = pack[1];
    } catch (e) {
      toast(e.message || "Could not load student", "error");
      return;
    }
    state.collector = null;
    state.matchMethod = "manual_list_select";
    state.pickup = null;
    $("#student-search").value = state.student.name + " · " + api.classLabel(state.student).replace(/^Class\s+/, "");
    $("#btn-to-list").disabled = false;
    renderLookup($("#student-search").value.split("·")[0].trim());
    renderAuthList();
  }

  function renderAuthList() {
    var s = state.student;
    if (!s) return;
    $("#student-avatar").textContent = api.initials(s.name);
    $("#student-avatar").className = "avatar avatar-xl " + studentAvatarClass(s);
    $("#student-name").textContent = s.name;
    $("#student-class").textContent = api.classLabel(s);

    var banner = $("#custody-banner");
    var flag = (state.custody && state.custody.flag) || "none";
    var instr = api.gateInstruction(state.custody && state.custody.gateInstruction);
    banner.className = "custody-banner visible";
    if (flag === "court_order") {
      banner.classList.add("court");
      $("#custody-badge").textContent = "COURT ORDER";
      $("#custody-instr").textContent = instr || "Court order on file. Follow gate instruction. No documents in product.";
    } else if (flag === "restricted") {
      banner.classList.add("restricted");
      $("#custody-badge").textContent = "RESTRICTED";
      $("#custody-instr").textContent = instr || "Restricted pickup — follow gate instruction.";
    } else {
      banner.classList.add("quiet");
      $("#custody-badge").textContent = "NONE";
      $("#custody-instr").textContent = "No custody restriction on file.";
    }

    var list = $("#auth-list");
    var rows = state.authorized || [];
    if (!rows.length) {
      list.innerHTML = '<div style="padding:12px;color:var(--text-muted);font-size:13px;">No authorized collectors. Ask Admin — no free-text release.</div>';
    } else {
      list.innerHTML = rows.map(function (p) {
        var blocked = p.blockedByCustody || p.blocked || p.demoBlock;
        var active = state.collector && state.collector.id === p.id;
        var expired = !api.inDate(p) && !p.demoBlock;
        return (
          '<button type="button" class="auth-card' +
            (blocked || expired ? " blocked-hint" : "") +
            (active ? " active" : "") +
          '" data-id="' + p.id + '">' +
            '<div class="avatar avatar-lg ' + (blocked ? "avatar-orange" : "avatar-purple") + '">' +
              api.initials(p.name) +
            "</div>" +
            '<div style="flex:1;">' +
              '<strong style="display:block;font-size:15px;">' + p.name + "</strong>" +
              '<div style="display:flex;gap:8px;align-items:center;margin-top:4px;flex-wrap:wrap;">' +
                '<span class="rel">' + api.relationLabel(p) + "</span>" +
                '<span class="mobile">' + api.formatMobile(p.mobile) + "</span>" +
                (p.idLast4 ? '<span class="mobile">ID ····' + p.idLast4 + "</span>" : "") +
                (expired ? '<span class="status-pill status-rejected">Expired / inactive</span>' : "") +
                (blocked ? '<span class="status-pill status-rejected">Blocked by custody</span>' : "") +
              "</div></div></button>"
        );
      }).join("");
    }
    $$(".auth-card", list).forEach(function (card) {
      card.addEventListener("click", function () {
        var person = rows.find(function (p) { return p.id === card.dataset.id; });
        state.collector = person;
        state.matchMethod = "manual_list_select";
        $$(".auth-card", list).forEach(function (c) { c.classList.remove("active"); });
        card.classList.add("active");
        $("#claimed-name").value = "";
        $("#btn-to-release").disabled = false;
      });
    });
    $("#btn-to-release").disabled = !state.collector;
  }

  function updateReleaseReady() {
    var ok = state.photo && state.consent && state.collector && decide(state.student, state.custody, state.collector) === "ok";
    $("#btn-complete").disabled = !ok || state.busy;
  }

  function setMetaMatch(method) {
    var el = $("#meta-match");
    if (el) el.textContent = method || state.matchMethod || "—";
  }

  function proofFields(pickup) {
    var ev = pickup || state.pickup || {};
    var s = state.student || {};
    var c = state.collector || {};
    return {
      student: s.name || ev.studentId || "—",
      collector: ev.collectorName || c.name || "—",
      relation: ev.collectorRelation || api.relationLabel(c),
      mobile: api.formatMobile(ev.collectorMobile || c.mobile),
      match: ev.matchMethod || state.matchMethod || "—",
      gate: ev.gateId || cfg.gateId,
      status: ev.status || state.resultMode || "—",
      photo: ev.collectorLivePhotoRef || "—",
      attempted: api.formatIst(ev.attemptedAt),
      released: api.formatIst(ev.releasedAt),
      id: ev.id || "—",
    };
  }

  function renderProof(pickup) {
    var p = proofFields(pickup);
    $("#proof-student").textContent = p.student;
    $("#proof-collector").textContent = p.collector + " (" + p.relation + ")";
    $("#proof-mobile").textContent = p.mobile;
    $("#proof-event").textContent = p.id;
    $("#proof-photo").textContent = p.photo;
    $("#proof-times").textContent = p.released !== "—" ? p.released : p.attempted;
    setMetaMatch(p.match);
  }

  function showResult(mode, pickup) {
    state.resultMode = mode;
    if (pickup) state.pickup = pickup;
    var s = state.student || { name: "Student" };
    var c = state.collector || { name: "Unknown collector", relationLabel: "—" };
    var icon = $("#result-icon");
    var chip = $("#result-chip");
    var title = $("#result-title");
    var prompt = $("#result-prompt");
    var overrideBtn = $("#btn-request-override");
    var demoBar = $("#override-demo-bar");
    overrideBtn.style.display = "none";
    demoBar.classList.remove("visible");

    icon.className = "result-icon";
    chip.className = "status-chip";

    var apiPrompt = pickup && pickup.meta && pickup.meta.gatePrompt;
    var instr = api.gateInstruction(state.custody && state.custody.gateInstruction);

    if (mode === "released") {
      icon.classList.add("ok");
      icon.innerHTML = '<svg class="icon icon-xl" viewBox="0 0 24 24"><path d="M20 6L9 17l-5-5"/></svg>';
      chip.classList.add("released");
      chip.textContent = "Released";
      title.textContent = "Release logged";
      prompt.textContent = apiPrompt || ("Release logged for " + s.name + " → " + c.name + " (" + api.relationLabel(c) + ").");
    } else if (mode === "blocked_auth") {
      icon.classList.add("block-auth");
      icon.innerHTML = '<svg class="icon icon-xl" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M15 9l-6 6M9 9l6 6"/></svg>';
      chip.classList.add("blocked-auth");
      chip.textContent = "BlockedNotAuthorized";
      title.textContent = "Do not release";
      prompt.textContent = apiPrompt || "This person is not on the authorized pickup list. Do not release. Ask Security Head.";
      overrideBtn.style.display = "inline-flex";
    } else if (mode === "blocked_custody") {
      icon.classList.add("block-custody");
      icon.innerHTML = '<svg class="icon icon-xl" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M12 8v4M12 16h.01"/></svg>';
      chip.classList.add("blocked-custody");
      chip.textContent = "BlockedCustody";
      title.textContent = "Custody restriction";
      prompt.textContent = apiPrompt || ("Custody restriction: " + (instr || "named allow-list") + " Release blocked. Security Head alerted.");
      overrideBtn.style.display = "inline-flex";
    } else if (mode === "override_wait") {
      icon.classList.add("override");
      icon.innerHTML = '<svg class="icon icon-xl" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>';
      chip.classList.add("override-wait");
      chip.innerHTML = '<span class="pulse-dot"></span> Override pending';
      title.textContent = "Override requested";
      prompt.textContent = apiPrompt || "Waiting for Security Head override…";
      demoBar.classList.add("visible");
    } else if (mode === "override_done") {
      icon.classList.add("override");
      icon.innerHTML = '<svg class="icon icon-xl" viewBox="0 0 24 24"><path d="M20 6L9 17l-5-5"/></svg>';
      chip.classList.add("override-done");
      chip.textContent = "ReleasedWithOverride";
      title.textContent = "Released with Security Head override";
      var reason = (pickup && pickup.overrideReason) || ($("#sh-override-reason").value || "").trim() || "SH confirmed";
      prompt.textContent = apiPrompt || ("Release logged for " + s.name + " → " + c.name + " (override by Security Head · " + reason + ").");
    }

    renderProof(pickup);
    showPanel(5);
  }

  function modeFromStatus(status) {
    if (status === "Released") return "released";
    if (status === "BlockedNotAuthorized") return "blocked_auth";
    if (status === "BlockedCustody") return "blocked_custody";
    if (status === "ReleasedWithOverride") return "override_done";
    return null;
  }

  async function persistAttempt(expectedMode) {
    var s = state.student;
    var c = state.collector || { name: "Unknown collector", mobile: "", notOnList: true };
    var body = {
      studentId: s.id,
      gateId: cfg.gateId,
      pickupReason: state.reason || "early",
      collectorName: c.name,
      collectorMobile: String(c.mobile || "").replace(/\D/g, "") || "9999999999",
    };
    if (c.id && !c.notOnList) body.collectorPickupPersonId = c.id;
    var pickup = await api.createPickup(body);
    await api.recordConsent(pickup.id, cfg.consentVersion);
    var blob = state.photoBlob || api.stubPngBlob();
    var uploaded = await api.uploadLivePhoto(blob);
    var released = await api.releasePickup(pickup.id, uploaded.key, state.linkVisit);
    if (!api.isLive() || (released && String(released.id || "").indexOf("PK-LOCAL-") === 0)) {
      var status = expectedMode === "blocked_auth" ? "BlockedNotAuthorized"
        : expectedMode === "blocked_custody" ? "BlockedCustody"
        : expectedMode === "released" ? "Released" : "Matching";
      released = api.applyLocalStatus(pickup.id, status, {
        collectorLivePhotoRef: uploaded.key,
        matchMethod: state.matchMethod,
        collectorRelation: api.relationLabel(c),
        custodyFlagSnapshot: (state.custody && state.custody.flag) || "none",
        releasedAt: status === "Released" ? new Date().toISOString() : null,
      });
    }
    state.pickup = released;
    return released;
  }

  async function handleBlock(mode, storyName) {
    setStoryActive(storyName);
    showResult(mode, state.pickup);
    try {
      setBusy(true);
      var pickup = await persistAttempt(mode);
      showResult(mode, pickup);
      toast("PickupEvent " + pickup.id + " · " + pickup.status, mode === "blocked_custody" ? "error" : "warning");
    } catch (e) {
      toast((e && e.message) || "Could not persist attempt — still blocked at gate", "warning");
    } finally {
      setBusy(false);
    }
  }

  function resetFlow() {
    state.student = null;
    state.authorized = [];
    state.custody = null;
    state.collector = null;
    state.reason = "sick";
    state.photo = false;
    state.photoBlob = null;
    state.consent = false;
    state.linkVisit = false;
    state.matchMethod = "manual_list_select";
    state.resultMode = null;
    state.pickup = null;
    $("#student-search").value = "";
    $("#claimed-name").value = "";
    var mm = $("#match-mobile"); if (mm) mm.value = "";
    var mi = $("#match-id"); if (mi) mi.value = "";
    $("#photo-box").classList.remove("filled");
    $("#photo-avatar").textContent = "?";
    $("#consent-check").checked = false;
    $("#campus-enter").checked = false;
    $("#btn-to-list").disabled = true;
    $("#btn-to-release").disabled = true;
    $("#btn-complete").disabled = true;
    $("#override-demo-bar").classList.remove("visible");
    $$(".reason-chip").forEach(function (c) {
      c.classList.toggle("active", c.dataset.reason === "sick");
    });
    renderLookup("");
    showPanel(1);
  }

  function findByName(list, name) {
    return (list || []).find(function (p) { return p.name === name; });
  }

  function storyCast(students) {
    var pranay = api.currentTenant().id === "pranay";
    var happyNames = pranay ? ["Asha Patil"] : ["Aarav Mehta"];
    var custodyNames = pranay ? ["Dev Joshi", "Kabir Singh"] : ["Kabir Singh"];
    var collectorNames = pranay
      ? ["Ramesh Patil", "Smita Patil", "Kavita Shah"]
      : ["Rohan Mehta", "Neha Mehta"];
    var happy = null;
    happyNames.forEach(function (n) {
      if (!happy) happy = students.find(function (s) { return s.name === n; });
    });
    if (!happy) happy = students[0];
    var custody = null;
    students.forEach(function (s) {
      if (custody) return;
      if (isCustodyFlag(s._custodyFlag) || custodyNames.indexOf(s.name) >= 0) custody = s;
    });
    if (!custody && pranay) {
      custody = (api.fixtureStudents() || []).find(function (s) { return s.name === "Dev Joshi"; }) || null;
    }
    return { happy: happy, custody: custody, collectorNames: collectorNames, lookupQ: pranay ? "Asha" : "Aarav" };
  }

  async function runStory(name) {
    setStoryActive(name);
    resetFlow();
    if (name === "purpose") {
      showPanel(1);
      return;
    }
    if (name === "lookup") {
      showPanel(2);
      var q = api.currentTenant().id === "pranay" ? "Asha" : "Aarav";
      $("#student-search").value = q;
      await refreshLookup(q);
      return;
    }

    try {
      setBusy(true);
      await refreshLookup("");
      var students = state._lookupCache || [];
      var cast = storyCast(students);

      if (name === "list" || name === "happy" || name === "notauth" || name === "override") {
        if (!cast.happy) throw new Error((api.currentTenant().id === "pranay" ? "Asha Patil" : "Aarav Mehta") + " not in directory");
        await selectStudent(cast.happy.id);
      }
      if (name === "custody") {
        if (!cast.custody) {
          throw new Error("No restricted / court_order student on this tenant. Demo Kabir is SCH-DEMO-01 only (login gate / gate123).");
        }
        await selectStudent(cast.custody.id);
      }

      if (name === "list") {
        showPanel(3);
        renderAuthList();
      } else if (name === "happy") {
        var collector = null;
        cast.collectorNames.forEach(function (n) {
          if (!collector) collector = findByName(state.authorized, n);
        });
        if (!collector) throw new Error("Happy-path collector missing");
        state.collector = collector;
        state.matchMethod = "manual_list_select";
        renderAuthList();
        showPanel(4);
        state.photo = true;
        state.consent = true;
        $("#photo-box").classList.add("filled");
        $("#photo-avatar").textContent = api.initials(collector.name);
        $("#consent-check").checked = true;
        $("#release-sub").textContent = state.student.name + " → " + collector.name + " (" + api.relationLabel(collector) + ")";
        updateReleaseReady();
        showResult("released");
      } else if (name === "notauth") {
        $("#claimed-name").value = "Unknown pickup person";
        state.collector = { id: null, name: "Unknown pickup person", relation: "none", relationLabel: "—", notOnList: true, mobile: "" };
        state.matchMethod = "none";
        showResult("blocked_auth");
      } else if (name === "custody") {
        var blocked = (state.authorized || []).find(function (p) {
          return p.blockedByCustody || p.blocked || p.demoBlock;
        });
        state.collector = blocked || { id: null, name: "Claimed relative", relation: "other", notOnList: !blocked, mobile: "" };
        state.matchMethod = blocked ? "manual_list_select" : "none";
        renderAuthList();
        showResult("blocked_custody");
      } else if (name === "override") {
        state.collector = { id: null, name: "Unknown collector", relation: "none", relationLabel: "—", notOnList: true, mobile: "" };
        state.matchMethod = "none";
        showResult("override_wait");
      }
    } catch (e) {
      toast(e.message || "Story jump failed", "error");
    } finally {
      setBusy(false);
    }
  }

  $("#btn-to-lookup").addEventListener("click", function () {
    refreshLookup("");
    showPanel(2);
    setStoryActive("lookup");
  });

  var searchTimer = null;
  $("#student-search").addEventListener("input", function (e) {
    clearTimeout(searchTimer);
    var q = e.target.value;
    searchTimer = setTimeout(function () { refreshLookup(q); }, 180);
  });
  $("#student-search").addEventListener("focus", function () {
    refreshLookup($("#student-search").value);
  });

  $("#btn-to-list").addEventListener("click", function () {
    if (!state.student) return;
    renderAuthList();
    showPanel(3);
    setStoryActive("list");
  });

  $("#btn-claim-notlist").addEventListener("click", function () {
    var name = $("#claimed-name").value.trim();
    if (!name) return;
    state.collector = { id: null, name: name, relation: "none", relationLabel: "—", notOnList: true, mobile: "" };
    state.matchMethod = "none";
    handleBlock("blocked_auth", "notauth");
  });

  $("#btn-match").addEventListener("click", function () {
    if (!state.student) return;
    var found = matchPerson(state.authorized, $("#match-mobile").value, $("#match-id").value);
    if (!found.hit) {
      state.collector = { id: null, name: "Claimed (no match)", relation: "none", relationLabel: "—", notOnList: true, mobile: $("#match-mobile").value };
      state.matchMethod = "none";
      handleBlock("blocked_auth", "notauth");
      return;
    }
    state.collector = found.hit;
    state.matchMethod = found.method;
    renderAuthList();
    var verdict = decide(state.student, state.custody, found.hit);
    if (verdict === "blocked_custody") {
      handleBlock("blocked_custody", "custody");
      return;
    }
    if (verdict === "blocked_auth") {
      handleBlock("blocked_auth", "notauth");
      return;
    }
    $("#btn-to-release").disabled = false;
    toast("Matched " + found.hit.name + " via " + found.method, "success");
  });

  $("#btn-to-release").addEventListener("click", function () {
    if (!state.collector) return;
    var verdict = decide(state.student, state.custody, state.collector);
    if (verdict === "blocked_custody") {
      handleBlock("blocked_custody", "custody");
      return;
    }
    if (verdict === "blocked_auth") {
      handleBlock("blocked_auth", "notauth");
      return;
    }
    $("#release-sub").textContent = state.student.name + " → " + state.collector.name + " (" + api.relationLabel(state.collector) + ")";
    $("#photo-avatar").textContent = api.initials(state.collector.name);
    showPanel(4);
    updateReleaseReady();
  });

  $$(".reason-chip").forEach(function (chip) {
    chip.addEventListener("click", function () {
      state.reason = chip.dataset.reason;
      $$(".reason-chip").forEach(function (c) { c.classList.toggle("active", c === chip); });
    });
  });

  $("#photo-box").addEventListener("click", async function () {
    state.photo = true;
    $("#photo-box").classList.add("filled");
    updateReleaseReady();
    try {
      state.photoBlob = await api.canvasPngBlob();
    } catch (e) {
      state.photoBlob = api.stubPngBlob();
    }
  });

  $("#consent-check").addEventListener("change", function (e) {
    state.consent = e.target.checked;
    updateReleaseReady();
  });

  $("#campus-enter").addEventListener("change", function (e) {
    state.linkVisit = e.target.checked;
  });

  $("#btn-complete").addEventListener("click", async function () {
    if (decide(state.student, state.custody, state.collector) !== "ok") return;
    setStoryActive("happy");
    try {
      setBusy(true);
      var pickup = await persistAttempt("released");
      var mode = modeFromStatus(pickup && pickup.status) || "released";
      if (mode !== "released") {
        showResult(mode, pickup);
        toast(pickup.status + " · not released", "warning");
        return;
      }
      showResult("released", pickup);
      toast("Released · " + pickup.id, "success");
    } catch (e) {
      toast((e && e.message) || "Release failed", "error");
    } finally {
      setBusy(false);
    }
  });

  $("#btn-restart").addEventListener("click", function () {
    resetFlow();
    setStoryActive("purpose");
  });

  $("#btn-request-override").addEventListener("click", async function () {
    setStoryActive("override");
    showResult("override_wait", state.pickup);
    if (!state.pickup || !state.pickup.id) {
      try {
        setBusy(true);
        var created = await persistAttempt(state.resultMode === "blocked_custody" ? "blocked_custody" : "blocked_auth");
        state.pickup = created;
      } catch (e) {
        toast((e && e.message) || "Could not persist block before override", "warning");
      } finally {
        setBusy(false);
      }
    }
    if (state.pickup && state.pickup.id) {
      try {
        var ev = await api.requestOverride(state.pickup.id);
        showResult("override_wait", ev);
        toast("Override requested · Security Head", "info");
      } catch (e) {
        toast((e && e.message) || "Override request failed — still waiting", "warning");
      }
    }
  });

  $("#btn-sh-approve").addEventListener("click", async function () {
    var reason = ($("#sh-override-reason").value || "").trim();
    if (!reason) {
      $("#sh-override-reason").focus();
      $("#sh-override-reason").style.borderColor = "var(--orange)";
      return;
    }
    $("#sh-override-reason").style.borderColor = "";
    if (!state.pickup || !state.pickup.id) {
      toast("No PickupEvent to override", "error");
      return;
    }
    try {
      setBusy(true);
      var ev = await api.overridePickup(state.pickup.id, reason);
      showResult("override_done", ev);
      toast("ReleasedWithOverride · SH reason stored", "success");
    } catch (e) {
      toast((e && e.message) || "SH override failed", "error");
    } finally {
      setBusy(false);
    }
  });

  $("#btn-admin-stub").addEventListener("click", function () {
    toast("Admin CRUD / history is the Admin desk — stub only on this Gate slice.", "info");
  });

  $$("[data-back]").forEach(function (btn) {
    btn.addEventListener("click", function () { showPanel(Number(btn.dataset.back)); });
  });
  $$(".story-btn").forEach(function (btn) {
    btn.addEventListener("click", function () { runStory(btn.dataset.story); });
  });

  function toggleLoginPanel(force) {
    var panel = $("#login-panel");
    if (!panel) return;
    var on = typeof force === "boolean" ? force : !panel.classList.contains("visible");
    panel.classList.toggle("visible", on);
    if (on) {
      applyChrome();
      $("#login-user").focus();
    }
  }

  async function signInAs(username, password) {
    try {
      setBusy(true);
      var boot = await api.login(username, password);
      state.live = boot.live;
      applyChrome();
      var pill = $("#source-pill");
      if (boot.live) {
        pill.textContent = "LIVE mock · " + cfg.apiBase.replace("https://", "");
        pill.classList.remove("fallback");
        toast("LIVE · " + boot.tenant.name, "success");
      } else {
        pill.textContent = "FIXTURES · tunnel unreachable";
        pill.classList.add("fallback");
        toast("FIXTURES · " + boot.tenant.name, "warning");
      }
      toggleLoginPanel(false);
      state._lookupCache = [];
      await refreshLookup("");
      resetFlow();
      setStoryActive("purpose");
    } catch (e) {
      toast((e && e.message) || "Sign-in failed", "error");
    } finally {
      setBusy(false);
    }
  }

  $("#btn-switch-account").addEventListener("click", function () {
    toggleLoginPanel();
  });
  $("#btn-login").addEventListener("click", function () {
    var user = ($("#login-user").value || "").trim() || api.currentTenant().gateUser;
    var pass = $("#login-pass").value;
    signInAs(user, pass);
  });
  $("#btn-login-demo").addEventListener("click", function () {
    $("#login-user").value = "gate";
    $("#login-pass").value = "gate123";
    signInAs("gate", "gate123");
  });
  $("#login-pass").addEventListener("keydown", function (e) {
    if (e.key === "Enter") $("#btn-login").click();
  });

  function bootFromHash() {
    var hash = (location.hash || "#purpose").replace(/^#/, "");
    runStory(KNOWN_STORIES.indexOf(hash) >= 0 ? hash : "purpose");
  }
  window.addEventListener("hashchange", function () {
    var hash = (location.hash || "").replace(/^#/, "");
    if (KNOWN_STORIES.indexOf(hash) >= 0 && hash !== state.story) runStory(hash);
  });

  api.warmup().then(function (boot) {
    state.live = boot.live;
    applyChrome();
    var pill = $("#source-pill");
    var tenantName = (boot.tenant && boot.tenant.name) || api.currentTenant().name;
    if (boot.live) {
      pill.textContent = "LIVE mock · " + cfg.apiBase.replace("https://", "");
      pill.classList.remove("fallback");
      toast("LIVE · " + tenantName, "success");
    } else {
      pill.textContent = "FIXTURES · tunnel unreachable";
      pill.classList.add("fallback");
      toast("FIXTURES · " + tenantName + " · Asha / Rohan Shah", "warning");
    }
    return refreshLookup("");
  }).then(function () {
    bootFromHash();
  });
})();
