(function (global) {
  var cfg = global.VMS_CONFIG;
  var token = null;
  var live = false;
  var currentUser = null;
  var TIMEOUT_MS = 6000;
  var SESSION_TOKEN = "satcop-host-token";
  var SESSION_USER = "satcop-host-user";

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
        timeZone: "Asia/Calcutta",
      }) + " IST";
    } catch (e) {
      return iso;
    }
  }

  function initials(name) {
    var parts = String(name || "").trim().split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return (parts[0] || "VS").slice(0, 2).toUpperCase();
  }

  function decodeJwt(tok) {
    try {
      var payload = String(tok || "").split(".")[1];
      if (!payload) return null;
      payload = payload.replace(/-/g, "+").replace(/_/g, "/");
      while (payload.length % 4) payload += "=";
      var json = atob(payload);
      try {
        json = decodeURIComponent(Array.prototype.map.call(json, function (c) {
          return "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(""));
      } catch (e) { /* keep latin-1 json */ }
      return JSON.parse(json);
    } catch (e) {
      return null;
    }
  }

  function userFromToken(tok, fallback) {
    var claims = decodeJwt(tok) || {};
    var base = fallback && typeof fallback === "object" ? fallback : {};
    return {
      id: claims.sub || claims.userId || base.id || null,
      schoolId: claims.schoolId || base.schoolId || "",
      role: claims.role || base.role || "host",
      staffId: claims.staffId || base.staffId || null,
      displayName: claims.displayName || base.displayName || "",
    };
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
        throw err;
      }
      return body;
    } catch (e) {
      if (e && e.name === "AbortError") {
        throw new Error("Tunnel timeout · fixtures");
      }
      throw e;
    } finally {
      to.done();
    }
  }

  function persist() {
    try {
      if (token) sessionStorage.setItem(SESSION_TOKEN, token);
      if (currentUser) sessionStorage.setItem(SESSION_USER, JSON.stringify(currentUser));
    } catch (e) { /* private mode */ }
  }

  function logout() {
    token = null;
    live = false;
    currentUser = null;
    try {
      sessionStorage.removeItem(SESSION_TOKEN);
      sessionStorage.removeItem(SESSION_USER);
    } catch (e) { /* ignore */ }
  }

  async function login(username, password) {
    var prev = token;
    token = null;
    try {
      var out = await request("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: username, password: password }),
      });
      token = out.accessToken;
      currentUser = userFromToken(token, out.user || null);
      live = true;
      persist();
      return { live: true, user: currentUser };
    } catch (e) {
      token = prev;
      live = !!prev;
      throw e;
    }
  }

  // Restore only a previous typed session. Never POSTs /auth/login and never
  // uses baked-in demo host/host123 credentials.
  function restoreSession() {
    try {
      var t = sessionStorage.getItem(SESSION_TOKEN);
      var raw = sessionStorage.getItem(SESSION_USER);
      if (!t) return false;
      var stored = null;
      try { stored = raw ? JSON.parse(raw) : null; } catch (e) { stored = null; }
      token = t;
      currentUser = userFromToken(t, stored);
      live = true;
      persist();
      return true;
    } catch (e) {
      return false;
    }
  }

  async function getVisit(id) {
    if (!live) return null;
    return request("/visits/" + encodeURIComponent(id));
  }

  async function listPending() {
    if (!live) return [];
    var out = await request("/visits?status=pending");
    return out.data || [];
  }

  async function approve(id) {
    return request("/visits/" + encodeURIComponent(id) + "/approve", { method: "POST" });
  }

  async function reject(id, reason) {
    return request("/visits/" + encodeURIComponent(id) + "/reject", {
      method: "POST",
      body: JSON.stringify({ reason: reason }),
    });
  }

  async function meetingDone(id) {
    return request("/visits/" + encodeURIComponent(id) + "/meeting-done", { method: "POST" });
  }

  global.VMS_API = {
    login: login,
    logout: logout,
    restoreSession: restoreSession,
    currentUser: function () { return currentUser; },
    getVisit: getVisit,
    listPending: listPending,
    approve: approve,
    reject: reject,
    meetingDone: meetingDone,
    formatMobile: formatMobile,
    formatIst: formatIst,
    initials: initials,
    isLive: function () { return live; },
  };
})(window);
