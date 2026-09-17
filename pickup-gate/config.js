/**
 * Pickup-gate living API + tenant defaults.
 * pensions-usb is DEAD — do not point anything here at it.
 *
 * Living seed login is pranay.gate / PranayGate@2026 (not pranay123).
 * Override apiBase with ?api=https://host/v1 or window.VMS_PICKUP_API_BASE.
 */
(function (global) {
  var params = new URLSearchParams(location.search);
  var envBase =
    (global.VMS_PICKUP_API_BASE) ||
    params.get("api") ||
    "https://replacing-spyware-yes-due.trycloudflare.com/v1";

  var tenants = {
    pranay: {
      id: "pranay",
      schoolId: "SCH-PRANAY-01",
      schoolCode: "PRANAY",
      name: "Pranay School Pune",
      gateUser: "pranay.gate",
      gatePass: "PranayGate@2026",
      gatePassFallbacks: [],
      shUser: "pranay.sh",
      shPass: "PranaySH@2026",
      shPassFallbacks: [],
      shFallbackUser: "security",
      shFallbackPass: "sh123",
      gateId: "PS-G-MAIN",
      gateName: "Main Gate",
    },
    demo: {
      id: "demo",
      schoolId: "SCH-DEMO-01",
      schoolCode: "DEMO",
      name: "Demo International School",
      gateUser: "gate",
      gatePass: "gate123",
      gatePassFallbacks: [],
      shUser: "security",
      shPass: "sh123",
      shPassFallbacks: [],
      shFallbackUser: null,
      shFallbackPass: null,
      gateId: "G-MAIN",
      gateName: "Main Gate",
    },
  };

  var requested = String(params.get("tenant") || "pranay").toLowerCase();
  var defaultTenant = tenants[requested] ? requested : "pranay";

  global.VMS_PICKUP_CONFIG = {
    apiBase: String(envBase).replace(/\/$/, ""),
    defaultTenant: defaultTenant,
    tenants: tenants,
    gateUser: tenants[defaultTenant].gateUser,
    gatePass: tenants[defaultTenant].gatePass,
    shUser: tenants[defaultTenant].shUser,
    shPass: tenants[defaultTenant].shPass,
    gateId: tenants[defaultTenant].gateId,
    consentVersion: "pickup_notice_en_v1",
    watermark: "DEMO · P2 PICKUP",
  };
})(window);
