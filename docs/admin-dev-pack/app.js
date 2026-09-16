/* Satcop Smart Visitor — shared demo helpers + fixture loader */
(function () {
  'use strict';

  function $(sel, root) {
    return (root || document).querySelector(sel);
  }
  function $$(sel, root) {
    return Array.from((root || document).querySelectorAll(sel));
  }

  function ensureToastContainer() {
    var el = $('.toast-container');
    if (!el) {
      el = document.createElement('div');
      el.className = 'toast-container';
      document.body.appendChild(el);
    }
    return el;
  }

  window.showToast = function (message, type) {
    var container = ensureToastContainer();
    var toast = document.createElement('div');
    toast.className = 'toast ' + (type || 'info');
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(function () {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.25s';
      setTimeout(function () { toast.remove(); }, 250);
    }, 2800);
  };

  window.vmsNow = function () {
    var d = new Date();
    return d.toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
      timeZone: 'Asia/Calcutta'
    });
  };

  window.vmsToday = function () {
    return new Date().toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'Asia/Calcutta'
    });
  };

  window.vmsFormatTime = function (iso) {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleTimeString('en-IN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
        timeZone: 'Asia/Calcutta'
      });
    } catch (e) {
      return '—';
    }
  };

  window.vmsFormatDateTime = function (iso) {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleString('en-IN', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
        timeZone: 'Asia/Calcutta'
      });
    } catch (e) {
      return '—';
    }
  };

  window.vmsDuration = function (timeInIso, nowMs) {
    if (!timeInIso) return '—';
    var start = new Date(timeInIso).getTime();
    var end = nowMs != null ? nowMs : Date.now();
    var mins = Math.max(0, Math.floor((end - start) / 60000));
    var h = Math.floor(mins / 60);
    var m = mins % 60;
    if (h <= 0) return m + 'm';
    return h + 'h ' + m + 'm';
  };

  window.vmsInitials = function (name) {
    var parts = String(name || '').trim().split(/\s+/).filter(Boolean);
    if (!parts.length) return '?';
    var a = parts[0][0] || '';
    var b = parts.length > 1 ? parts[parts.length - 1][0] : '';
    return (a + b).toUpperCase();
  };

  window.vmsAvatarClass = function (type) {
    var map = {
      Parent: 'avatar-purple',
      Vendor: 'avatar-orange',
      Guest: 'avatar-blue',
      Official: 'avatar-cyan',
      Alumni: 'avatar-pink'
    };
    return map[type] || 'avatar-indigo';
  };

  window.vmsTypeClass = function (type) {
    return 'tag type-' + String(type || 'guest').toLowerCase();
  };

  /** Shared demo story: Priya Sharma @ Main Gate → Anita Joshi */
  window.vmsDemoStory = function (fixtures) {
    var inside = (fixtures && fixtures.inside) || [];
    var priya = inside.find(function (v) { return v.name === 'Priya Sharma'; }) || inside[0];
    var staff = (fixtures && fixtures.staff) || [];
    var host = staff.find(function (s) { return s.id === (priya && priya.hostId); })
      || staff.find(function (s) { return s.name === 'Anita Joshi'; })
      || { name: 'Anita Joshi', role: 'Primary Coordinator' };
    return {
      visitor: priya || {
        name: 'Priya Sharma',
        type: 'Parent',
        mobile: '9822011122',
        purpose: 'PTM follow-up, Class 4B',
        gate: 'Main Gate',
        passId: 'P-4F21',
        host: 'Anita Joshi · Primary Coordinator',
        hostId: 'H03',
        timeIn: '2026-09-16T14:10:00+05:30'
      },
      host: host,
      gate: (priya && priya.gate) || 'Main Gate'
    };
  };

  window.vmsFormatMobile = function (m) {
    var digits = String(m || '').replace(/\D/g, '');
    if (digits.length === 10) {
      return '+91 ' + digits.slice(0, 5) + ' ' + digits.slice(5);
    }
    return m || '—';
  };

  /**
   * Load admin MVP fixtures: fetch demos/data/… then fall back to
   * window.VMS_FIXTURES_FALLBACK (file:// / CORS).
   */
  window.loadVmsFixtures = function () {
    if (window.__vmsFixturesPromise) return window.__vmsFixturesPromise;
    window.__vmsFixturesPromise = fetch('data/admin-mvp-fixtures.json', { cache: 'no-store' })
      .then(function (res) {
        if (!res.ok) throw new Error('fixtures HTTP ' + res.status);
        return res.json();
      })
      .catch(function () {
        if (window.VMS_FIXTURES_FALLBACK) return window.VMS_FIXTURES_FALLBACK;
        throw new Error('No fixtures available');
      })
      .then(function (data) {
        window.VMS_FIXTURES = data;
        return data;
      });
    return window.__vmsFixturesPromise;
  };

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      $$('.modal-overlay.open').forEach(function (el) {
        el.classList.remove('open');
      });
    }
  });

  document.addEventListener('click', function (e) {
    var overlay = e.target.closest('.modal-overlay');
    if (overlay && e.target === overlay) {
      overlay.classList.remove('open');
    }
  });
})();
