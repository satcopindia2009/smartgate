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

  async function request(path, opts) {
    var headers = Object.assign({ "Content-Type": "application/json" }, (opts && opts.headers) || {});
    if (token) headers.Authorization = "Bearer " + token;
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

  async function warmup() {
    try {
      var login = await request("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: cfg.gateUser, password: cfg.gatePass }),
      });
      token = login.accessToken;
      live = true;
      return { live: true, user: login.user };
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

  async function getHours() {
    if (!live) return null;
    return unwrap(await request("/access-rules/hours"));
  }

  async function listHolidays() {
    if (!live) return null;
    return unwrap(await request("/access-rules/holidays"));
  }

  async function getVisit(id) {
    if (!live) return null;
    return request("/visits/" + encodeURIComponent(id));
  }

  async function getPass(passId) {
    if (!live) return null;
    return request("/passes/" + encodeURIComponent(passId));
  }

  async function listPendingAfterHours() {
    if (!live) return [];
    var out = await request("/visits?status=pending&afterHours=true");
    return unwrap(out);
  }

  async function hostApprove(id) {
    var login = await fetch(cfg.apiBase + "/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: cfg.hostUser, password: cfg.hostPass }),
    });
    var body = await login.json();
    if (!login.ok) {
      var err = new Error((body.error && body.error.message) || "Host login failed");
      err.code = body.error && body.error.code;
      throw err;
    }
    var res = await fetch(cfg.apiBase + "/visits/" + encodeURIComponent(id) + "/approve", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + body.accessToken },
    });
    var out = await res.json();
    if (!res.ok) {
      var e = new Error((out.error && out.error.message) || "Host approve failed");
      e.code = out.error && out.error.code;
      e.body = out;
      throw e;
    }
    return out;
  }

  async function shDecide(id, action, reason) {
    if (!reason || !String(reason).trim()) {
      var missing = new Error("Reason required for Security Head after-hours decide");
      missing.code = "VALIDATION";
      throw missing;
    }
    var login = await fetch(cfg.apiBase + "/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: cfg.shUser, password: cfg.shPass }),
    });
    var body = await login.json();
    if (!login.ok) {
      var err = new Error((body.error && body.error.message) || "SH login failed");
      err.code = body.error && body.error.code;
      throw err;
    }
    var path = action === "reject" ? "/reject" : "/approve";
    var res = await fetch(cfg.apiBase + "/visits/" + encodeURIComponent(id) + path, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + body.accessToken },
      body: JSON.stringify({ reason: String(reason).trim() }),
    });
    var out = await res.json();
    if (!res.ok) {
      var e = new Error((out.error && out.error.message) || "SH decide failed");
      e.code = out.error && out.error.code;
      e.body = out;
      throw e;
    }
    return out;
  }

  global.VMS_AH_API = {
    warmup: warmup,
    getHours: getHours,
    listHolidays: listHolidays,
    getVisit: getVisit,
    getPass: getPass,
    listPendingAfterHours: listPendingAfterHours,
    hostApprove: hostApprove,
    shDecide: shDecide,
    formatMobile: formatMobile,
    formatIst: formatIst,
    isLive: function () { return live; },
  };
})(window);
