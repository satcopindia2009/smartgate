(function (global) {
  var cfg = global.VMS_CONFIG;
  var token = null;
  var live = false;

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

  async function request(path, opts) {
    var headers = Object.assign({ "Content-Type": "application/json" }, (opts && opts.headers) || {});
    if (token) headers.Authorization = "Bearer " + token;
    var res = await fetch(cfg.apiBase + path, Object.assign({}, opts, { headers }));
    var text = await res.text();
    var body = text ? JSON.parse(text) : {};
    if (!res.ok) {
      var err = new Error((body.error && body.error.message) || ("HTTP " + res.status));
      err.code = body.error && body.error.code;
      err.status = res.status;
      throw err;
    }
    return body;
  }

  async function warmup() {
    try {
      var login = await request("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: cfg.hostUser, password: cfg.hostPass }),
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
    warmup: warmup,
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
