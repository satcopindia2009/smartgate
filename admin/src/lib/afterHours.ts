import { AFTER_HOURS_FIXTURE_STATE_KEY, CAMPUS_TZ } from "./constants";
import type { CampusHoursRow, HolidayEntry, PolicyTrigger, Weekday } from "./types";

export const WEEKDAYS: Weekday[] = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];

export const WEEKDAY_LABELS: Record<Weekday, string> = {
  mon: "Monday",
  tue: "Tuesday",
  wed: "Wednesday",
  thu: "Thursday",
  fri: "Friday",
  sat: "Saturday",
  sun: "Sunday",
};

export function policyTriggerLabel(trigger?: string | null): string {
  if (trigger === "holiday") return "holiday";
  if (trigger === "both") return "both";
  if (trigger === "outside_hours") return "outside_hours";
  return "After hours";
}

export function isHolidayTrigger(trigger?: string | null): boolean {
  return trigger === "holiday" || trigger === "both";
}

export function seedCampusHours(): CampusHoursRow[] {
  return WEEKDAYS.map((weekday) => {
    if (weekday === "sun") {
      return {
        schoolId: "SCH-DEMO-01",
        timezone: CAMPUS_TZ,
        weekday,
        openTime: null,
        closeTime: null,
        closed: true,
        overnight: false,
        updatedByUserId: "U-ADMIN",
        updatedAt: "2026-09-01T09:00:00+05:30",
      };
    }
    return {
      schoolId: "SCH-DEMO-01",
      timezone: CAMPUS_TZ,
      weekday,
      openTime: "08:00",
      closeTime: weekday === "sat" ? "13:00" : "18:00",
      closed: false,
      overnight: false,
      updatedByUserId: "U-ADMIN",
      updatedAt: "2026-09-01T09:00:00+05:30",
    };
  });
}

export function seedHolidays(): HolidayEntry[] {
  return [
    {
      id: "HOL-DIWALI",
      schoolId: "SCH-DEMO-01",
      date: "2026-10-20",
      label: "Diwali",
      createdByUserId: "U-ADMIN",
      updatedByUserId: "U-ADMIN",
      createdAt: "2026-09-01T09:00:00+05:30",
      updatedAt: "2026-09-01T09:00:00+05:30",
    },
  ];
}

export interface AfterHoursFixtureSession {
  hours: CampusHoursRow[];
  holidays: HolidayEntry[];
}

let session: AfterHoursFixtureSession | null = null;

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T;
}

export function getAfterHoursFixtureSession(
  hours?: CampusHoursRow[],
  holidays?: HolidayEntry[],
): AfterHoursFixtureSession {
  if (session) return session;
  try {
    const raw = sessionStorage.getItem(AFTER_HOURS_FIXTURE_STATE_KEY);
    if (raw) {
      session = JSON.parse(raw) as AfterHoursFixtureSession;
      return session;
    }
  } catch {
    /* ignore */
  }
  session = {
    hours: clone(hours && hours.length ? hours : seedCampusHours()),
    holidays: clone(holidays && holidays.length ? holidays : seedHolidays()),
  };
  return session;
}

export function persistAfterHoursFixtures(): void {
  if (!session) return;
  try {
    sessionStorage.setItem(AFTER_HOURS_FIXTURE_STATE_KEY, JSON.stringify(session));
  } catch {
    /* ignore */
  }
}

export function saveHoursLocal(rows: CampusHoursRow[]): CampusHoursRow[] {
  const sess = getAfterHoursFixtureSession();
  const now = new Date().toISOString();
  sess.hours = rows.map((row) => ({
    ...row,
    timezone: row.timezone || CAMPUS_TZ,
    updatedByUserId: "U-ADMIN",
    updatedAt: now,
  }));
  persistAfterHoursFixtures();
  return clone(sess.hours);
}

export function addHolidayLocal(date: string, label?: string | null): HolidayEntry {
  const sess = getAfterHoursFixtureSession();
  const now = new Date().toISOString();
  const entry: HolidayEntry = {
    id: `HOL-${date.replace(/-/g, "")}`,
    schoolId: "SCH-DEMO-01",
    date,
    label: label || null,
    createdByUserId: "U-ADMIN",
    updatedByUserId: "U-ADMIN",
    createdAt: now,
    updatedAt: now,
  };
  sess.holidays = [...sess.holidays, entry].sort((a, b) => a.date.localeCompare(b.date));
  persistAfterHoursFixtures();
  return entry;
}

export function removeHolidayLocal(id: string): void {
  const sess = getAfterHoursFixtureSession();
  sess.holidays = sess.holidays.filter((h) => h.id !== id);
  persistAfterHoursFixtures();
}

export function hoursPayload(rows: CampusHoursRow[]): CampusHoursRow[] {
  return rows.map((row) => ({
    weekday: row.weekday,
    timezone: row.timezone || CAMPUS_TZ,
    openTime: row.closed ? null : row.openTime || null,
    closeTime: row.closed ? null : row.closeTime || null,
    closed: Boolean(row.closed),
    overnight: Boolean(row.overnight),
  }));
}

export function overnightNeedsConfirm(row: CampusHoursRow): boolean {
  if (row.closed || !row.openTime || !row.closeTime) return false;
  return row.closeTime < row.openTime && !row.overnight;
}

export function formatHolidayDate(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  if (!y || !m || !d) return isoDate;
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}

export function matchesAfterHoursFlag(
  afterHours: boolean | undefined,
  trigger: string | null | undefined,
  flag: string,
  decision?: string,
): boolean {
  if (!flag) return true;
  if (flag === "afterhours") return Boolean(afterHours);
  if (flag === "holiday") return Boolean(afterHours) && isHolidayTrigger(trigger);
  if (flag === "pending_sh") {
    return Boolean(afterHours) && String(decision || "").toLowerCase() === "pending";
  }
  return true;
}

export type { PolicyTrigger };
