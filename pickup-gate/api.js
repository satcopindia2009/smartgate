(function (global) {
  var cfg = global.VMS_PICKUP_CONFIG;
  var fx = global.VMS_PICKUP_FIXTURES;
  var token = null;
  var shToken = null;
  var live = false;
  var currentUser = null;
  var activeTenantId = cfg.defaultTenant || "pranay";
  var TIMEOUT_MS = 6000;
  var localEvents = {};
  var localSeq = 1;

  function tenants() {
    return cfg.tenants || {};
  }

  function tenantById(id) {
    return tenants()[id] || tenants().pranay;
  }

  function currentTenant() {
    return tenantById(activeTenantId);
  }

  function inferTenantId(username, schoolId) {
    if (schoolId === "SCH-PRANAY-01") return "pranay";
    if (schoolId === "SCH-DEMO-01") return "demo";
    var u = String(username || "").toLowerCase();
    if (u.indexOf("pranay") === 0) return "pranay";
    if (u === "gate" || u === "security" || u === "admin" || u === "host") return "demo";
    return cfg.defaultTenant || "pranay";
  }

  function applyTenant(id) {
    activeTenantId = tenantById(id).id;
    var t = currentTenant();
    cfg.gateUser = t.gateUser;
    cfg.gatePass = t.gatePass;
    cfg.shUser = t.shUser;
    cfg.shPass = t.shPass;
    cfg.gateId = t.gateId;
    return t;
  }

  function uniquePasswords(primary, fallbacks) {
    var out = [];
    [primary].concat(fallbacks || []).forEach(function (p) {
      if (p && out.indexOf(p) < 0) out.push(p);
    });
    return out;
  }

  function formatMobile(raw) {
    var d = String(raw || "").replace(/\D/g, "");
    if (!d) return "—";
    if (d.length === 12 && d.indexOf("91") === 0) d = d.slice(2);
    if (d.length === 11 && d.indexOf("0") === 0) d = d.slice(1);
    if (d.length === 10) return "+91 " + d.slice(0, 5) + " " + d.slice(5);
    return raw;
  }

  function formatIst(iso) {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleString("en-IN", {
        day: "numeric",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
        hour12: true,
        timeZone: "Asia/Calcutta",
      }) + " IST";
    } catch (e) {
      return iso;
    }
  }

  function initials(name) {
    var parts = String(name || "").trim().split(/\s+/).filter(Boolean);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return (parts[0] || "??").slice(0, 2).toUpperCase();
  }

  function classLabel(s) {
    if (!s) return "";
    if (s.classLabel) return s.classLabel;
    var cls = s.class != null ? String(s.class) : "";
    var sec = s.section != null ? String(s.section) : "";
    if (cls && sec) return "Class " + cls + "-" + sec;
    if (cls) return "Class " + cls;
    return "";
  }

  function last4(value) {
    return String(value || "").replace(/\D/g, "").slice(-4);
  }

  function todayIsoDate() {
    try {
      return new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Calcutta" });
    } catch (e) {
      return new Date().toISOString().slice(0, 10);
    }
  }

  function inDate(person) {
    if (!person) return false;
    if (person.active === false) return false;
    var today = todayIsoDate();
    if (person.effectiveFrom && today < person.effectiveFrom) return false;
    if (person.effectiveTo && today > person.effectiveTo) return false;
    return true;
  }

  function relationLabel(person) {
    if (!person) return "—";
    if (person.relationLabel) return person.relationLabel;
    var known = {
      "Neha Mehta": "Mother",
      "Rohan Mehta": "Uncle",
      "Sunita Singh": "Mother",
      "Rajesh Singh": "Father",
      "Sneha Patel": "Mother",
      "Ramesh Patil": "Parent",
      "Smita Patil": "Guardian",
      "Kavita Shah": "Parent",
      "Nisha Joshi": "Guardian",
    };
    if (known[person.name]) return known[person.name];
    var map = {
      parent: "Parent",
      guardian: "Guardian",
      sibling: "Sibling",
      relative: "Relative",
      other: "Other",
    };
    return map[person.relation] || person.relation || "—";
  }

  function gateInstruction(text) {
    return String(text || "").slice(0, 280);
  }

  function withTimeout() {
    var ctrl = new AbortController();
    var t = setTimeout(function () { ctrl.abort(); }, TIMEOUT_MS);
    return {
      signal: ctrl.signal,
      done: function () { clearTimeout(t); },
    };
  }

  async function request(path, opts) {
    var headers = Object.assign({ "Content-Type": "application/json" }, (opts && opts.headers) || {});
    var useToken = (opts && opts.token) || token;
    if (useToken) headers.Authorization = "Bearer " + useToken;
    if (opts && opts.skipJson) delete headers["Content-Type"];
    var to = withTimeout();
    try {
      var res = await fetch(cfg.apiBase + path, Object.assign({}, opts, { headers: headers, signal: to.signal }));
      var text = await res.text();
      var body = text ? JSON.parse(text) : {};
      if (!res.ok) {
        var err = new Error((body.error && body.error.message) || ("HTTP " + res.status));
        err.code = body.error && body.error.code;
        err.status = res.status;
        err.body = body;
        throw err;
      }
      return body;
    } catch (e) {
      if (e && e.name === "AbortError") throw new Error("Tunnel timeout · fixtures");
      throw e;
    } finally {
      to.done();
    }
  }

  async function loginWithFallbacks(username, passwords) {
    var lastErr = null;
    for (var i = 0; i < passwords.length; i++) {
      try {
        return await request("/auth/login", {
          method: "POST",
          body: JSON.stringify({ username: username, password: passwords[i] }),
        });
      } catch (e) {
        lastErr = e;
        if (e && e.status && e.status !== 401 && e.code !== "INVALID_CREDENTIALS") throw e;
      }
    }
    throw lastErr || new Error("Login failed");
  }

  function fixtureStudents() {
    var schoolId = currentTenant().schoolId;
    return (fx.students || []).filter(function (s) { return s.schoolId === schoolId; });
  }

  function searchLocal(q) {
    var query = String(q || "").trim().toLowerCase();
    return fixtureStudents().filter(function (s) {
      if (!query) return true;
      var label = (s.name + " " + classLabel(s) + " " + (s.class || "") + "-" + (s.section || "")).toLowerCase();
      return label.indexOf(query) >= 0 || s.name.toLowerCase().indexOf(query) >= 0;
    });
  }

  async function login(username, password) {
    token = null;
    shToken = null;
    live = false;
    currentUser = null;
    var inferred = inferTenantId(username);
    applyTenant(inferred);
    var t = currentTenant();
    var passes = password
      ? uniquePasswords(password, [])
      : uniquePasswords(t.gatePass, t.gatePassFallbacks);
    try {
      var result = await loginWithFallbacks(username, passes);
      token = result.accessToken;
      live = true;
      currentUser = result.user || { username: username };
      applyTenant(inferTenantId(username, currentUser.schoolId));
      return { live: true, user: currentUser, tenant: currentTenant() };
    } catch (e) {
      live = false;
      token = null;
      currentUser = { username: username, schoolId: t.schoolId, role: "gate", fixture: true };
      return { live: false, error: e.message, user: currentUser, tenant: t };
    }
  }

  async function warmup(username, password) {
    var t = currentTenant();
    return login(username || t.gateUser, password);
  }

  async function ensureSh() {
    if (shToken) return shToken;
    var t = currentTenant();
    var attempts = [];
    uniquePasswords(t.shPass, t.shPassFallbacks).forEach(function (p) {
      attempts.push({ username: t.shUser, password: p });
    });
    if (t.shFallbackUser && t.shFallbackPass) {
      attempts.push({ username: t.shFallbackUser, password: t.shFallbackPass });
    }
    var lastErr = null;
    for (var i = 0; i < attempts.length; i++) {
      try {
        var result = await request("/auth/login", {
          method: "POST",
          body: JSON.stringify(attempts[i]),
        });
        shToken = result.accessToken;
        return shToken;
      } catch (e) {
        lastErr = e;
      }
    }
    throw lastErr || new Error("Security Head login failed");
  }

  async function searchStudents(q) {
    if (live) {
      var qs = q ? ("?q=" + encodeURIComponent(q) + "&active=true") : "?active=true";
      var out = await request("/students" + qs);
      return out.data || [];
    }
    return searchLocal(q);
  }

  async function getStudent(id) {
    if (live) {
      try {
        return await request("/students/" + encodeURIComponent(id));
      } catch (e) {
        var local = fixtureStudents().find(function (s) { return s.id === id; });
        if (local) return local;
        throw e;
      }
    }
    return fixtureStudents().find(function (s) { return s.id === id; }) || null;
  }

  async function listAuthorized(studentId) {
    if (live) {
      try {
        var out = await request("/students/" + encodeURIComponent(studentId) + "/authorized-pickup");
        return out.data || [];
      } catch (e) {
        if (fx.authorized[studentId]) return fx.authorized[studentId].slice();
        throw e;
      }
    }
    return (fx.authorized[studentId] || []).slice();
  }

  async function getCustody(studentId) {
    if (live) {
      try {
        return await request("/students/" + encodeURIComponent(studentId) + "/custody-flag");
      } catch (e) {
        if (fx.custody[studentId]) return fx.custody[studentId];
        throw e;
      }
    }
    return fx.custody[studentId] || { studentId: studentId, flag: "none", gateInstruction: "" };
  }

  function localPickup(partial) {
    var now = new Date().toISOString();
    var id = "PK-LOCAL-" + String(localSeq++).padStart(3, "0");
    var ev = Object.assign({
      id: id,
      schoolId: currentTenant().schoolId,
      gateId: cfg.gateId,
      status: "Matching",
      override: false,
      attemptedAt: now,
      createdAt: now,
      updatedAt: now,
      gateUserId: currentUser && currentUser.id || "U-GATE",
      meta: { watermark: "DEMO", fixture: true },
    }, partial);
    localEvents[id] = ev;
    return ev;
  }

  function isFixtureOnlyStudent(studentId) {
    return String(studentId || "").indexOf("-FX-") >= 0;
  }

  async function createPickup(body) {
    if (live && !isFixtureOnlyStudent(body.studentId)) {
      return request("/pickups", { method: "POST", body: JSON.stringify(body) });
    }
    return localPickup({
      studentId: body.studentId,
      gateId: body.gateId || cfg.gateId,
      collectorPickupPersonId: body.collectorPickupPersonId || null,
      collectorName: body.collectorName || "",
      collectorMobile: body.collectorMobile || "",
      pickupReason: body.pickupReason,
      matchMethod: body.matchMethod || (body.collectorPickupPersonId ? "manual_list_select" : "none"),
      custodyFlagSnapshot: body.custodyFlagSnapshot || "none",
      status: "Matching",
    });
  }

  async function recordConsent(pickupId, version) {
    var payload = { pickupConsentVersion: version || cfg.consentVersion };
    if (live && !localEvents[pickupId]) {
      return request("/pickups/" + encodeURIComponent(pickupId) + "/consent", {
        method: "POST",
        body: JSON.stringify(payload),
      });
    }
    var ev = localEvents[pickupId];
    if (ev) {
      ev.pickupConsentVersion = payload.pickupConsentVersion;
      ev.pickupConsentAt = new Date().toISOString();
      ev.updatedAt = ev.pickupConsentAt;
    }
    return ev;
  }

  async function uploadLivePhoto(blob) {
    if (!live) return { key: "media/live_photo/fixture-stub" };
    var fd = new FormData();
    fd.append("file", blob, "collector-live.png");
    fd.append("kind", "live_photo");
    var to = withTimeout();
    try {
      var res = await fetch(cfg.apiBase + "/media/upload", {
        method: "POST",
        headers: { Authorization: "Bearer " + token },
        body: fd,
        signal: to.signal,
      });
      var text = await res.text();
      var body = text ? JSON.parse(text) : {};
      if (!res.ok) throw new Error((body.error && body.error.message) || ("HTTP " + res.status));
      return body;
    } finally {
      to.done();
    }
  }

  async function releasePickup(pickupId, photoRef, linkVisit) {
    var payload = { collectorLivePhotoRef: photoRef };
    if (linkVisit) payload.linkVisit = true;
    if (live && !localEvents[pickupId]) {
      return request("/pickups/" + encodeURIComponent(pickupId) + "/release", {
        method: "POST",
        body: JSON.stringify(payload),
      });
    }
    var ev = localEvents[pickupId];
    if (ev) {
      ev.collectorLivePhotoRef = photoRef;
      ev.updatedAt = new Date().toISOString();
    }
    return ev;
  }

  async function requestOverride(pickupId) {
    if (live && !localEvents[pickupId]) {
      return request("/pickups/" + encodeURIComponent(pickupId) + "/request-override", { method: "POST" });
    }
    var ev = localEvents[pickupId];
    if (ev) {
      ev.meta = Object.assign({}, ev.meta, { overrideRequested: true });
      ev.updatedAt = new Date().toISOString();
    }
    return ev;
  }

  async function overridePickup(pickupId, reason) {
    if (live && !localEvents[pickupId]) {
      await ensureSh();
      return request("/pickups/" + encodeURIComponent(pickupId) + "/override", {
        method: "POST",
        token: shToken,
        body: JSON.stringify({ reason: reason }),
      });
    }
    var ev = localEvents[pickupId];
    if (ev) {
      ev.status = "ReleasedWithOverride";
      ev.override = true;
      ev.overrideByUserId = "U-SH";
      ev.overrideReason = reason;
      ev.releasedAt = new Date().toISOString();
      ev.updatedAt = ev.releasedAt;
    }
    return ev;
  }

  async function getPickup(pickupId) {
    if (localEvents[pickupId]) return localEvents[pickupId];
    if (live) return request("/pickups/" + encodeURIComponent(pickupId));
    return localEvents[pickupId] || null;
  }

  function stubPngBlob() {
    var bin = atob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    var arr = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
    return new Blob([arr], { type: "image/png" });
  }

  function canvasPngBlob() {
    return new Promise(function (resolve) {
      try {
        var c = document.createElement("canvas");
        c.width = 240;
        c.height = 240;
        var ctx = c.getContext("2d");
        ctx.fillStyle = "#1a1d24";
        ctx.fillRect(0, 0, 240, 240);
        ctx.fillStyle = "#8b5cf6";
        ctx.beginPath();
        ctx.arc(120, 120, 70, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = "#fff";
        ctx.font = "bold 42px Inter, sans-serif";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText("LIVE", 120, 120);
        c.toBlob(function (blob) { resolve(blob || stubPngBlob()); }, "image/png");
      } catch (e) {
        resolve(stubPngBlob());
      }
    });
  }

  global.VMS_PICKUP_API = {
    warmup: warmup,
    login: login,
    searchStudents: searchStudents,
    getStudent: getStudent,
    listAuthorized: listAuthorized,
    getCustody: getCustody,
    createPickup: createPickup,
    recordConsent: recordConsent,
    uploadLivePhoto: uploadLivePhoto,
    releasePickup: releasePickup,
    requestOverride: requestOverride,
    overridePickup: overridePickup,
    getPickup: getPickup,
    canvasPngBlob: canvasPngBlob,
    stubPngBlob: stubPngBlob,
    formatMobile: formatMobile,
    formatIst: formatIst,
    initials: initials,
    classLabel: classLabel,
    last4: last4,
    inDate: inDate,
    relationLabel: relationLabel,
    gateInstruction: gateInstruction,
    fixtureStudents: fixtureStudents,
    isLive: function () { return live; },
    currentTenant: currentTenant,
    currentUser: function () { return currentUser; },
    applyLocalStatus: function (pickupId, status, extra) {
      var ev = localEvents[pickupId];
      if (!ev) return null;
      ev.status = status;
      ev.updatedAt = new Date().toISOString();
      if (extra) Object.assign(ev, extra);
      return ev;
    },
  };
})(window);
