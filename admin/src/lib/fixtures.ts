import { DEMO_LOGINS, FIXTURE_STATE_KEY, GATE_ENUMS } from "./constants";
import type {
  AuthUser,
  BlacklistEntry,
  FixturesFile,
  Gate,
  GateReport,
  HistoryVisit,
  LiveVisitor,
  Staff,
} from "./types";
import { gateIdFromName } from "./mapVisit";
import { seedCampusHours, seedHolidays } from "./afterHours";
import { seedEscortRules, seedZones } from "./escort";

let cached: FixturesFile | null = null;
let session: FixtureSession | null = null;

export interface FixtureSession {
  inside: LiveVisitor[];
  history: HistoryVisit[];
  blacklist: BlacklistEntry[];
}

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T;
}

export async function loadFixtures(): Promise<FixturesFile> {
  if (cached) return cached;
  try {
    const res = await fetch("/data/admin-mvp-fixtures.json", { cache: "no-store" });
    if (!res.ok) throw new Error(`fixtures HTTP ${res.status}`);
    cached = (await res.json()) as FixturesFile;
  } catch {
    cached = fallbackFixtures();
  }
  return cached;
}

export async function getFixtureSession(): Promise<FixtureSession> {
  if (session) return session;
  const fx = await loadFixtures();
  try {
    const raw = sessionStorage.getItem(FIXTURE_STATE_KEY);
    if (raw) {
      session = JSON.parse(raw) as FixtureSession;
      return session;
    }
  } catch {
    /* ignore */
  }
  session = {
    inside: clone(fx.inside),
    history: clone(fx.history),
    blacklist: clone(fx.blacklist),
  };
  return session;
}

export function persistFixtureSession(): void {
  if (!session) return;
  try {
    sessionStorage.setItem(FIXTURE_STATE_KEY, JSON.stringify(session));
  } catch {
    /* ignore */
  }
}

export function fixtureGates(): Gate[] {
  return GATE_ENUMS.map((name) => ({
    id: gateIdFromName(name) || name,
    name,
    active: true,
  }));
}

export function fixtureStaff(fx: FixturesFile): Staff[] {
  return fx.staff.map((s) => ({
    id: s.id,
    name: s.name,
    roleTitle: s.role,
    role: s.role,
    active: true,
  }));
}

export function fixtureLogin(username: string, password: string): AuthUser | null {
  const row = DEMO_LOGINS[username as keyof typeof DEMO_LOGINS];
  if (!row || row.password !== password) return null;
  return {
    id: row.id,
    schoolId: row.schoolId,
    schoolCode: row.schoolCode,
    role: row.role,
    staffId: row.staffId,
    displayName: row.displayName,
    email: `${username}@${row.schoolId === "SCH-PRANAY-01" ? "pranay.school" : "demo.school"}`,
  };
}

export function applyForceCheckoutLocal(visitId: string, reason: string): LiveVisitor | null {
  if (!session) return null;
  const idx = session.inside.findIndex((v) => v.visitId === visitId);
  if (idx === -1) return null;
  const [row] = session.inside.splice(idx, 1);
  const now = new Date().toISOString();
  const hist = session.history.find((h) => h.visitId === visitId);
  if (hist) {
    hist.checkoutType = "Force";
    hist.timeOut = now;
    hist.gateOut = row.gate;
    hist.notes = `Force checkout: ${reason}`;
    if (hist.timeIn) {
      hist.durationMin = Math.max(
        0,
        Math.round((new Date(now).getTime() - new Date(hist.timeIn).getTime()) / 60000),
      );
    }
  } else {
    session.history.unshift({
      visitId: row.visitId,
      name: row.name,
      mobile: row.mobile,
      type: row.type,
      purpose: row.purpose,
      host: row.host.split("·")[0].trim(),
      decision: "Approved",
      decisionReason: null,
      gateIn: row.gate,
      timeIn: row.timeIn,
      gateOut: row.gate,
      timeOut: now,
      checkoutType: "Force",
      blacklistHit: row.blacklistHit,
      notes: `Force checkout: ${reason}`,
      afterHours: row.afterHours,
      policyTrigger: row.policyTrigger,
      afterHoursEvaluatedAt: row.afterHoursEvaluatedAt,
    });
  }
  persistFixtureSession();
  return row;
}

export function applyHostPendingDecisionLocal(
  visitId: string,
  action: "approve" | "reject",
  reason?: string,
): HistoryVisit | null {
  if (!session) return null;
  const hist = session.history.find((h) => h.visitId === visitId);
  if (!hist || hist.afterHours) return null;
  const now = new Date().toISOString();
  hist.decision = action === "approve" ? "Approved" : "Rejected";
  hist.decisionReason = action === "reject" ? reason || null : reason || null;
  hist.decisionAt = now;
  if (action === "reject") {
    hist.notes = [hist.notes, `Host-pending reject: ${reason || ""}`].filter(Boolean).join(" · ");
  } else {
    hist.notes = [hist.notes, "Host-pending approve (Admin/SH)"].filter(Boolean).join(" · ");
    if (!hist.passId) hist.passId = "P-LOCAL";
  }
  persistFixtureSession();
  return hist;
}

export function applyAfterHoursDecisionLocal(
  visitId: string,
  action: "approve" | "reject",
  reason: string,
): HistoryVisit | null {
  if (!session) return null;
  const hist = session.history.find((h) => h.visitId === visitId);
  if (!hist) return null;
  const now = new Date().toISOString();
  hist.decision = action === "approve" ? "Approved" : "Rejected";
  hist.decisionReason = reason;
  hist.decisionAt = now;
  hist.afterHoursApproveReason = reason;
  if (action === "reject") {
    hist.notes = [hist.notes, `SH reject: ${reason}`].filter(Boolean).join(" · ");
  } else {
    hist.notes = [hist.notes, `SH approve: ${reason}`].filter(Boolean).join(" · ");
  }
  persistFixtureSession();
  return hist;
}

export function upsertBlacklistLocal(entry: BlacklistEntry, isNew: boolean): void {
  if (!session) return;
  if (isNew) {
    session.blacklist.push(entry);
  } else {
    const i = session.blacklist.findIndex((b) => b.id === entry.id);
    if (i >= 0) session.blacklist[i] = entry;
  }
  persistFixtureSession();
}

export function fixtureReports(fx: FixturesFile): GateReport[] {
  return fx.reportsTodayByGate;
}

function fallbackFixtures(): FixturesFile {
  return {
    meta: {
      school: "Demo International School",
      timezone: "Asia/Calcutta",
      generated: "2026-09-16",
      watermark: "DEMO",
      overdueHoursDefault: 4,
      gates: [...GATE_ENUMS],
      visitorTypes: ["Parent", "Vendor", "Guest", "Official", "Alumni"],
      idTypes: ["Aadhaar", "DL", "Voter", "Passport", "Other"],
      roles: { admin: "Office Admin", securityHead: "Security Head" },
    },
    staff: [
      { id: "H01", name: "Meera Kulkarni", role: "Principal" },
      { id: "H02", name: "Rahul Deshpande", role: "Admin Officer" },
      { id: "H03", name: "Anita Joshi", role: "Primary Coordinator" },
      { id: "H04", name: "Sanjay Patil", role: "Accounts" },
      { id: "G01", name: "Gate — Ramesh", role: "Guard" },
    ],
    blacklist: [],
    inside: [
      {
        visitId: "V-20260916-014",
        name: "Priya Sharma",
        type: "Parent",
        mobile: "9822011122",
        hostId: "H03",
        host: "Anita Joshi · Primary Coordinator",
        purpose: "PTM follow-up, Class 4B",
        gate: "Main Gate",
        timeIn: "2026-09-16T14:10:00+05:30",
        passId: "P-4F21",
        status: "Inside",
        flags: [],
        blacklistHit: false,
      },
    ],
    history: [
      {
        visitId: "V-AH-VENDOR",
        name: "Ravi Deshmukh",
        mobile: "9822098801",
        type: "Vendor",
        purpose: "After-hours AC repair — Main Gate",
        host: "Anita Joshi",
        hostId: "H03",
        decision: "Pending",
        decisionReason: null,
        decisionAt: "2026-09-16T19:30:00+05:30",
        gateIn: "Main Gate",
        timeIn: null,
        gateOut: null,
        timeOut: null,
        checkoutType: "Never",
        durationMin: null,
        blacklistHit: false,
        registeredBy: "Gate — Ramesh",
        notes: "After-hours Vendor demo — SH approve required",
        passId: null,
        createdAt: "2026-09-16T19:30:00+05:30",
        afterHours: true,
        policyTrigger: "outside_hours",
        afterHoursEvaluatedAt: "2026-09-16T19:30:00+05:30",
      },
      {
        visitId: "V-AH-HOLIDAY",
        name: "Deepak Nair",
        mobile: "9822098802",
        type: "Parent",
        purpose: "Holiday walk-in — collect notebooks",
        host: "Meera Kulkarni",
        hostId: "H01",
        decision: "Approved",
        decisionReason: "Holiday walk-in verified by Security Head",
        decisionAt: "2026-10-20T10:40:00+05:30",
        gateIn: "Main Gate",
        timeIn: null,
        gateOut: null,
        timeOut: null,
        checkoutType: "Never",
        durationMin: null,
        blacklistHit: false,
        registeredBy: "Gate — Ramesh",
        notes: "Holiday Parent demo — SH approved; pass P-7K88",
        passId: "P-7K88",
        createdAt: "2026-10-20T10:30:00+05:30",
        afterHours: true,
        policyTrigger: "holiday",
        afterHoursEvaluatedAt: "2026-10-20T10:30:00+05:30",
        afterHoursApproveReason: "Holiday walk-in verified by Security Head",
      },
      {
        visitId: "V-20260917-HOST",
        name: "Kavita Rao",
        mobile: "9822098810",
        type: "Parent",
        purpose: "Meet class teacher — host pending",
        host: "Anita Joshi",
        hostId: "H03",
        decision: "Pending",
        decisionReason: null,
        decisionAt: null,
        gateIn: "Main Gate",
        timeIn: null,
        gateOut: null,
        timeOut: null,
        checkoutType: "Never",
        durationMin: null,
        blacklistHit: false,
        registeredBy: "Gate — Ramesh",
        notes: "In-hours Parent demo — Admin/SH may decide while host is busy",
        passId: null,
        createdAt: "2026-09-17T10:15:00+05:30",
        afterHours: false,
        policyTrigger: null,
        afterHoursEvaluatedAt: "2026-09-17T10:15:00+05:30",
      },
    ],
    campusHours: seedCampusHours(),
    holidays: seedHolidays(),
    zones: seedZones(),
    escortRules: seedEscortRules(),
    reportsTodayByGate: GATE_ENUMS.map((gate) => ({
      gate,
      checkIns: 0,
      checkOuts: 0,
      stillInside: gate === "Main Gate" ? 1 : 0,
      rejects: 0,
      blacklistHits: 0,
      forceCheckouts: 0,
      uniqueMobiles: 0,
      medianApprovalSec: 0,
      peakInside: 0,
    })),
  };
}
