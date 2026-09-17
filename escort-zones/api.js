(function (global) {
  var cfg = global.VMS_CONFIG;
  var token = null;
  var live = false;
  var TIMEOUT_MS = 6000;

  function formatMobile(raw) {
    var d = String(raw || "").replace(/\D/g, "");
    if (d.length === 12 && d.indexOf("91") === 0) d = d.slice(2);
    if (d.length === 10) return "+91 " + d.slice(0, 5) + " " + d.slice(5);
    return raw || "—";
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
        timeZone: "Asia/Kolkata",
      }) + " IST";
    } catch (e) {
      return iso;
    }
  }

  function withTimeout() {
    var ctrl = new AbortController();
    var t = setTimeout(function () { ctrl.abort(); }, TIMEOUT_MS);
    return { signal: ctrl.signal, done: function () { clearTimeout(t); } };
  }

  async function request(path, opts, bearer) {
    var headers = Object.assign({ "Content-Type": "application/json" }, (opts && opts.headers) || {});
    var use = bearer || token;
    if (use) headers.Authorization = "Bearer " + use;
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

  async function login(user, pass) {
    var body = await request("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: user, password: pass }),
    }, null);
    return body.accessToken;
  }

  async function warmup() {
    try {
      token = await login(cfg.gateUser, cfg.gatePass);
      live = true;
      return { live: true };
    } catch (e) {
      live = false;
      token = null;
      return { live: false, error: e.message };
    }
  }

  function unwrap(body) {
    if (Array.isArray(body)) return body;
    if (body && Array.isArray(body.data)) return body.data;
    return [];
  }

  async function listZones() {
    if (!live) return null;
    return unwrap(await request("/zones"));
  }

  async function listEscortRules() {
    if (!live) return null;
    return unwrap(await request("/access-rules/escort"));
  }

  async function listStaff() {
    if (!live) return null;
    return unwrap(await request("/staff?active=true"));
  }

  async function listVendorVisits() {
    if (!live) return [];
    return unwrap(await request("/visits?visitorType=Vendor"));
  }

  async function getVisit(id) {
    if (!live) return null;
    return request("/visits/" + encodeURIComponent(id));
  }

  async function getPass(passId) {
    if (!live) return null;
    return request("/passes/" + encodeURIComponent(passId));
  }

  async function assignEscort(visitId, staffId) {
    var id = String(staffId || "").trim();
    if (!id || id === "E02" || id === "E03") {
      var err = new Error("Unknown escortStaffId · assign E01 Vikram More only (never E02/E03)");
      err.code = "VALIDATION";
      throw err;
    }
    return request("/visits/" + encodeURIComponent(visitId) + "/assign-escort", {
      method: "POST",
      body: JSON.stringify({ escortStaffId: id }),
    });
  }

  async function waiveEscort(visitId, reason) {
    var shTok = await login(cfg.shUser, cfg.shPass);
    return request("/visits/" + encodeURIComponent(visitId) + "/waive-escort", {
      method: "POST",
      body: JSON.stringify({ reason: reason }),
    }, shTok);
  }

  async function putEscortRules(rows) {
    var shTok = await login(cfg.shUser, cfg.shPass);
    return request("/access-rules/escort", {
      method: "PUT",
      body: JSON.stringify(rows),
    }, shTok);
  }

  async function patchZone(key, label) {
    var shTok = await login(cfg.shUser, cfg.shPass);
    return request("/zones/" + encodeURIComponent(key), {
      method: "PATCH",
      body: JSON.stringify({ label: label }),
    }, shTok);
  }

  global.VMS_EZ_API = {
    warmup: warmup,
    listZones: listZones,
    listEscortRules: listEscortRules,
    listStaff: listStaff,
    listVendorVisits: listVendorVisits,
    getVisit: getVisit,
    getPass: getPass,
    assignEscort: assignEscort,
    waiveEscort: waiveEscort,
    putEscortRules: putEscortRules,
    patchZone: patchZone,
    formatMobile: formatMobile,
    formatIst: formatIst,
    isLive: function () { return live; },
  };
})(window);
