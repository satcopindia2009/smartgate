/* Satcop Smart Visitor — theme boot + toggle.
 * Persist localStorage key satcop-theme = light | dark.
 * Default: prefers-color-scheme when unset. Sets data-theme on <html>.
 */
(function (global) {
  var STORAGE_KEY = "satcop-theme";

  function readStored() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      return raw === "light" || raw === "dark" ? raw : null;
    } catch (e) {
      return null;
    }
  }

  function systemTheme() {
    if (typeof window === "undefined" || !window.matchMedia) return "light";
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    document.documentElement.classList.toggle("theme-dark", theme === "dark");
    syncToggle(theme);
  }

  function currentTheme() {
    var t = document.documentElement.getAttribute("data-theme");
    return t === "dark" || t === "light" ? t : (readStored() || systemTheme());
  }

  function setTheme(theme) {
    if (theme !== "light" && theme !== "dark") return;
    applyTheme(theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch (e) { /* ignore quota / private mode */ }
  }

  function syncToggle(theme) {
    document.querySelectorAll("[data-theme-set]").forEach(function (btn) {
      var on = btn.getAttribute("data-theme-set") === theme;
      btn.classList.toggle("active", on);
      btn.setAttribute("aria-pressed", on ? "true" : "false");
    });
    document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
      btn.setAttribute("data-theme-current", theme);
      btn.setAttribute(
        "aria-label",
        theme === "dark" ? "Switch to light theme" : "Switch to dark theme"
      );
    });
  }

  function onSystemChange(mq) {
    if (readStored()) return;
    applyTheme(mq.matches ? "dark" : "light");
  }

  function boot() {
    applyTheme(readStored() || systemTheme());
    if (!readStored() && window.matchMedia) {
      var mq = window.matchMedia("(prefers-color-scheme: dark)");
      if (mq.addEventListener) mq.addEventListener("change", onSystemChange);
      else if (mq.addListener) mq.addListener(onSystemChange);
    }
    document.addEventListener("click", function (e) {
      var setBtn = e.target.closest && e.target.closest("[data-theme-set]");
      if (setBtn) {
        setTheme(setBtn.getAttribute("data-theme-set"));
        return;
      }
      var tog = e.target.closest && e.target.closest("[data-theme-toggle]");
      if (tog) setTheme(currentTheme() === "dark" ? "light" : "dark");
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }

  global.VMS_THEME = {
    setTheme: setTheme,
    current: currentTheme,
  };
})(window);
