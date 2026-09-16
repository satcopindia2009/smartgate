import { OVERDUE_HOURS_DEFAULT, SCHOOL_TZ } from "./constants";

export function formatClock(date = new Date()): string {
  return (
    date.toLocaleString("en-IN", {
      weekday: "short",
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
      hour12: true,
      timeZone: SCHOOL_TZ,
    }) + " IST"
  );
}

export function formatTime(iso?: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleTimeString("en-IN", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: true,
      timeZone: SCHOOL_TZ,
    });
  } catch {
    return "—";
  }
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString("en-IN", {
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
      hour12: true,
      timeZone: SCHOOL_TZ,
    });
  } catch {
    return "—";
  }
}

export function formatDuration(timeInIso?: string | null, nowMs = Date.now()): string {
  if (!timeInIso) return "—";
  const start = new Date(timeInIso).getTime();
  if (Number.isNaN(start)) return "—";
  const mins = Math.max(0, Math.floor((nowMs - start) / 60000));
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (h <= 0) return `${m}m`;
  return `${h}h ${m}m`;
}

export function formatDurationMin(mins?: number | null): string {
  if (mins == null) return "—";
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  if (h <= 0) return `${m}m`;
  return `${h}h ${m}m`;
}

export function isOverdue(
  timeInIso?: string | null,
  flags: string[] = [],
  overdueHours = OVERDUE_HOURS_DEFAULT,
  nowMs = Date.now(),
): boolean {
  if (flags.includes("overdue")) return true;
  if (!timeInIso) return false;
  const start = new Date(timeInIso).getTime();
  if (Number.isNaN(start)) return false;
  return (nowMs - start) / 3600000 > overdueHours;
}

export function initials(name?: string): string {
  const parts = String(name || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (!parts.length) return "?";
  const a = parts[0][0] || "";
  const b = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (a + b).toUpperCase();
}

export function avatarClass(type?: string): string {
  const map: Record<string, string> = {
    Parent: "avatar-purple",
    Vendor: "avatar-orange",
    Guest: "avatar-blue",
    Official: "avatar-cyan",
    Alumni: "avatar-pink",
  };
  return map[type || ""] || "avatar-indigo";
}

export function typeClass(type?: string): string {
  return `tag type-${String(type || "guest").toLowerCase()}`;
}

export function formatMobile(mobile?: string | null): string {
  const digits = String(mobile || "").replace(/\D/g, "");
  if (digits.length === 10) {
    return `+91 ${digits.slice(0, 5)} ${digits.slice(5)}`;
  }
  return mobile || "—";
}

export function maskGovtId(value?: string | null): string {
  const raw = String(value || "").trim();
  if (!raw) return "—";
  if (/[*Xx]/.test(raw)) return raw;
  const compact = raw.replace(/\s+/g, "");
  const last = compact.slice(-4);
  return compact.length <= 4 ? last : `••••-${last}`;
}

export function todayIso(tz = SCHOOL_TZ): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const map: Record<string, string> = {};
  parts.forEach((p) => {
    map[p.type] = p.value;
  });
  return `${map.year}-${map.month}-${map.day}`;
}

export function addDaysIso(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() + days);
  return dt.toISOString().slice(0, 10);
}

export function schoolDate(iso?: string | null): string | null {
  if (!iso) return null;
  try {
    return new Date(iso).toLocaleDateString("en-CA", { timeZone: SCHOOL_TZ });
  } catch {
    return iso.slice(0, 10);
  }
}
