import type { ApiVisit, Gate, HistoryVisit, LiveVisitor, Staff } from "./types";
import { GATE_ENUMS } from "./constants";
import { isOverdue } from "./format";

const GATE_ID_BY_NAME: Record<string, string> = {
  "Main Gate": "G-MAIN",
  "Pedestrian Gate": "G-PED",
  "Staff Gate": "G-STAFF",
  "Bus Bay": "G-BUS",
};

export function gateName(gates: Gate[], id?: string | null, fallback?: string): string {
  if (!id && fallback) return fallback;
  const found = gates.find((g) => g.id === id);
  if (found) return found.name;
  if (id && GATE_ENUMS.includes(id as (typeof GATE_ENUMS)[number])) return id;
  return fallback || id || "—";
}

export function gateIdFromName(name?: string | null): string | undefined {
  if (!name) return undefined;
  return GATE_ID_BY_NAME[name];
}

export function hostLabel(staff: Staff[], hostId?: string, fallback?: string): string {
  const row = staff.find((s) => s.id === hostId);
  if (!row) return fallback || "—";
  const title = row.roleTitle || row.role;
  return title ? `${row.name} · ${title}` : row.name;
}

export function hostShort(staff: Staff[], hostId?: string, fallback?: string): string {
  const row = staff.find((s) => s.id === hostId);
  return row?.name || fallback || "—";
}

export function toLiveVisitor(
  visit: ApiVisit,
  gates: Gate[],
  staff: Staff[],
): LiveVisitor {
  const flags: string[] = [];
  if (visit.overdue || isOverdue(visit.timeIn)) flags.push("overdue");
  if (visit.blacklistHit) flags.push("blacklist-alert");
  if (visit.afterHours) flags.push(visit.policyTrigger || "after_hours");
  return {
    visitId: visit.id,
    name: visit.visitorName,
    type: visit.visitorType,
    mobile: visit.mobile,
    hostId: visit.hostId,
    host: hostLabel(staff, visit.hostId),
    purpose: visit.purpose,
    gate: gateName(gates, visit.gateInId || visit.gateId),
    gateId: visit.gateInId || visit.gateId || undefined,
    timeIn: visit.timeIn || "",
    passId: visit.passId || "—",
    status: "Inside",
    flags,
    blacklistHit: Boolean(visit.blacklistHit),
    blacklistId: visit.blacklistId,
    afterHours: Boolean(visit.afterHours),
    policyTrigger: visit.policyTrigger || null,
    afterHoursEvaluatedAt: visit.afterHoursEvaluatedAt || null,
    escortRequired: Boolean(visit.escortRequired),
    allowedZones: visit.allowedZones || [],
    escortStaffId: visit.escortStaffId || null,
    escortName: visit.escortName || null,
    escortWaived: Boolean(visit.escortWaived),
  };
}

function decisionOf(visit: ApiVisit): { decision: string; reason?: string | null } {
  if (visit.status === "pending") return { decision: "Pending" };
  if (visit.status === "rejected") {
    return { decision: "Rejected", reason: visit.rejectReason };
  }
  if (visit.decidedAt || visit.status !== "pending") {
    return { decision: "Approved" };
  }
  return { decision: "Pending" };
}

function checkoutLabel(visit: ApiVisit): string {
  const ct = (visit.checkoutType || "").toLowerCase();
  if (ct === "force") return "Force";
  if (ct === "normal") return "Normal";
  return "Never";
}

export function toHistoryVisit(
  visit: ApiVisit,
  gates: Gate[],
  staff: Staff[],
): HistoryVisit {
  const dec = decisionOf(visit);
  let durationMin: number | null = null;
  if (visit.timeIn && visit.timeOut) {
    durationMin = Math.max(
      0,
      Math.round((new Date(visit.timeOut).getTime() - new Date(visit.timeIn).getTime()) / 60000),
    );
  }
  return {
    visitId: visit.id,
    name: visit.visitorName,
    mobile: visit.mobile,
    type: visit.visitorType,
    purpose: visit.purpose,
    host: hostShort(staff, visit.hostId),
    decision: dec.decision,
    decisionReason: dec.reason,
    decisionAt: visit.decidedAt,
    gateIn: visit.timeIn ? gateName(gates, visit.gateInId || visit.gateId) : null,
    timeIn: visit.timeIn,
    gateOut: visit.timeOut ? gateName(gates, visit.gateOutId || visit.gateInId) : null,
    timeOut: visit.timeOut,
    checkoutType: checkoutLabel(visit),
    durationMin,
    blacklistHit: Boolean(visit.blacklistHit),
    registeredBy: "Gate — Ramesh",
    notes: visit.notes || (visit.forceCheckoutReason ? `Force checkout: ${visit.forceCheckoutReason}` : ""),
    passId: visit.passId || null,
    createdAt: visit.createdAt || null,
    afterHours: Boolean(visit.afterHours),
    policyTrigger: visit.policyTrigger || null,
    afterHoursEvaluatedAt: visit.afterHoursEvaluatedAt || null,
    afterHoursApproveReason: visit.afterHoursApproveReason || null,
    hostId: visit.hostId,
    escortRequired: Boolean(visit.escortRequired),
    allowedZones: visit.allowedZones || [],
    escortStaffId: visit.escortStaffId || null,
    escortName: visit.escortName || (visit.escortStaffId ? hostShort(staff, visit.escortStaffId) : null),
    escortWaived: Boolean(visit.escortWaived),
  };
}

export function mergeVisitsById<T extends { visitId?: string; id?: string }>(lists: T[][]): T[] {
  const out = new Map<string, T>();
  lists.flat().forEach((row) => {
    const id = row.visitId || row.id;
    if (id) out.set(id, row);
  });
  return [...out.values()];
}

export function isAfterHoursPending(h: Pick<HistoryVisit, "afterHours" | "decision">): boolean {
  return Boolean(h.afterHours) && h.decision === "Pending";
}

export function isHostPending(h: Pick<HistoryVisit, "afterHours" | "decision">): boolean {
  return !h.afterHours && h.decision === "Pending";
}

export function matchesLiveSearch(v: LiveVisitor, q: string): boolean {
  if (!q) return true;
  const needle = q.toLowerCase().trim();
  const mobile = String(v.mobile || "");
  const last4 = mobile.slice(-4);
  const hay = [v.name, v.host, v.passId, v.visitId, mobile, last4, v.purpose]
    .join(" ")
    .toLowerCase();
  return hay.includes(needle);
}
