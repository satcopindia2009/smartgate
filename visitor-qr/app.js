(function () {
  var $ = function (s) { return document.querySelector(s); };
  var cfg = window.VMS_CONFIG;
  var fixture = window.VMS_PASS_FIXTURE;
  var params = new URLSearchParams(window.location.search);
  var passId = params.get("passId") || cfg.defaultPassId;

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
    return { cls: "waiting", text: status || "Unknown" };
  }

  function drawQr(token) {
    var el = $("#qr-canvas");
    el.innerHTML = "";
    if (window.QRCode) {
      new QRCode(el, {
        text: token,
        width: 180,
        height: 180,
        colorDark: "#0f1115",
        colorLight: "#ffffff",
        correctLevel: QRCode.CorrectLevel.M,
      });
    } else {
      el.innerHTML = '<div class="qr-box" style="margin:0;border-width:0;box-shadow:none;width:180px;height:180px;"><div class="qr-inner"></div></div>';
      el.querySelector(".qr-inner").textContent = token.slice(0, 18);
    }
  }

  function render(pass, live) {
    var pill = $("#source-pill");
    pill.textContent = live ? ("LIVE mock · " + passId) : "FIXTURES fallback · " + passId;
    pill.classList.toggle("fallback", !live);
    $("#qr-name").textContent = pass.visitorName;
    $("#qr-sub").textContent = (pass.visitorType || "Parent") + " · " + formatMobile(pass.mobile || "");
    $("#qr-host").textContent = pass.hostName || "Anita Joshi";
    $("#qr-gate").textContent = pass.gateName || "Main Gate";
    $("#qr-purpose").textContent = pass.purpose || "—";
    $("#qr-visit").textContent = pass.visitId || "—";
    $("#pass-code").textContent = "PASS · " + pass.passId;
    $("#qr-avatar").textContent = initials(pass.visitorName);
    var ban = bannerFor(pass.status);
    $("#status-banner").className = "status-banner " + ban.cls;
    $("#status-banner").textContent = ban.text;
    drawQr(pass.qrToken || pass.passId);
  }

  async function fetchLive() {
    var loginRes = await fetch(cfg.apiBase + "/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: cfg.gateUser, password: cfg.gatePass }),
    });
    if (!loginRes.ok) throw new Error("login " + loginRes.status);
    var login = await loginRes.json();
    var passRes = await fetch(cfg.apiBase + "/passes/" + encodeURIComponent(passId), {
      headers: { Authorization: "Bearer " + login.accessToken },
    });
    if (!passRes.ok) throw new Error("pass " + passRes.status);
    var pass = await passRes.json();
    if (!pass.purpose) pass.purpose = fixture.purpose;
    if (!pass.mobile) pass.mobile = fixture.mobile;
    if (!pass.visitorType) pass.visitorType = fixture.visitorType;
    return pass;
  }

  fetchLive()
    .then(function (pass) { render(pass, true); })
    .catch(function () { render(fixture, false); });
})();
