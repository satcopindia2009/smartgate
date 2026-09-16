(function () {
  var $ = function (s) { return document.querySelector(s); };
  var cfg = window.VMS_CONFIG;
  var fixture = window.VMS_PASS_FIXTURE;
  var params = new URLSearchParams(window.location.search);
  var passId = params.get("passId") || cfg.defaultPassId;
  var live = false;
  var cycle = ["pending", "approved", "inside", "completed"];
  var TIMEOUT_MS = 6000;

  function toast(msg, kind) {
    var box = $("#toast-box");
    if (!box) return;
    var el = document.createElement("div");
    el.className = "toast " + (kind || "info");
    el.textContent = msg;
    box.appendChild(el);
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 2800);
  }

  function formatMobile(raw) {
    var d = String(raw || "").replace(/\D/g, "");
    if (d.length === 12 && d.indexOf("91") === 0) d = d.slice(2);
    if (d.length === 10) return "+91 " + d.slice(0, 5) + " " + d.slice(5);
    return raw || "";
  }

  function initials(name) {
    var parts = String(name || "").trim().split(/\s+/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return (parts[0] || "VS").slice(0, 2).toUpperCase();
  }

  function bannerFor(status) {
    if (status === "pending") return { cls: "waiting", text: "Waiting · host not yet approved" };
    if (status === "approved") return { cls: "approved", text: "Approved · show at gate" };
    if (status === "inside") return { cls: "inside", text: "Inside campus · present QR on exit" };
    if (status === "completed") return { cls: "completed", text: "Checked out · visit complete" };
    if (status === "rejected") return { cls: "rejected", text: "Rejected · see gate" };
    return { cls: "waiting", text: status || "Unknown" };
  }

  function drawQr(token) {
    var el = $("#qr-canvas");
    el.innerHTML = "";
    if (window.QRCode) {
      new QRCode(el, {
        text: token,
        width: 220,
        height: 220,
        colorDark: "#0f1115",
        colorLight: "#ffffff",
        correctLevel: QRCode.CorrectLevel.M,
      });
    } else {
      el.innerHTML = '<div class="qr-box" style="margin:0;border-width:0;box-shadow:none;width:220px;height:220px;"><div class="qr-inner"></div></div>';
      el.querySelector(".qr-inner").textContent = String(token || "").slice(0, 18);
    }
  }

  function render(pass, isLive) {
    live = !!isLive;
    var pill = $("#source-pill");
    pill.textContent = isLive ? ("LIVE mock · " + passId) : ("FIXTURES · " + passId);
    pill.classList.toggle("fallback", !isLive);
    $("#qr-name").textContent = pass.visitorName;
    $("#qr-sub").textContent = (pass.visitorType || "Parent") + " · " + formatMobile(pass.mobile || "");
    $("#qr-host").textContent = pass.hostName || "Anita Joshi";
    $("#qr-gate").textContent = pass.gateName || "Main Gate";
    $("#qr-purpose").textContent = pass.purpose || "—";
    $("#qr-visit").textContent = pass.visitId || "—";
    $("#pass-code").textContent = "PASS · " + pass.passId;
    $("#qr-avatar").textContent = initials(pass.visitorName);
    var ban = bannerFor(pass.status);
    var banner = $("#status-banner");
    banner.className = "status-banner " + ban.cls + (isLive ? "" : " cycle");
    banner.textContent = ban.text;
    banner.title = isLive ? "" : "Tap to preview status banners (fixtures)";
    drawQr(pass.qrToken || pass.passId);
  }

  function withTimeout() {
    var ctrl = new AbortController();
    var t = setTimeout(function () { ctrl.abort(); }, TIMEOUT_MS);
    return { signal: ctrl.signal, done: function () { clearTimeout(t); } };
  }

  async function fetchLive() {
    var to = withTimeout();
    try {
      var loginRes = await fetch(cfg.apiBase + "/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: cfg.gateUser, password: cfg.gatePass }),
        signal: to.signal,
      });
      if (!loginRes.ok) throw new Error("login " + loginRes.status);
      var login = await loginRes.json();
      var passRes = await fetch(cfg.apiBase + "/passes/" + encodeURIComponent(passId), {
        headers: { Authorization: "Bearer " + login.accessToken },
        signal: to.signal,
      });
      if (!passRes.ok) throw new Error("pass " + passRes.status);
      var pass = await passRes.json();
      if (!pass.purpose) pass.purpose = fixture.purpose;
      if (!pass.mobile) pass.mobile = fixture.mobile;
      if (!pass.visitorType) pass.visitorType = fixture.visitorType;
      return pass;
    } finally {
      to.done();
    }
  }

  $("#status-banner").addEventListener("click", function () {
    if (live) return;
    var cur = fixture.status || "inside";
    var next = cycle[(cycle.indexOf(cur) + 1) % cycle.length];
    fixture.status = next;
    render(fixture, false);
    toast("Fixtures status · " + next, "info");
  });

  fetchLive()
    .then(function (pass) {
      render(pass, true);
      toast("Live badge · " + (pass.passId || passId), "success");
    })
    .catch(function () {
      render(fixture, false);
      toast("FIXTURES · tunnel unreachable", "warning");
    });
})();
