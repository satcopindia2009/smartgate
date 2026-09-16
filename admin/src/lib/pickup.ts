import type { Gate, Student } from "./types";

const GATE_SHORT: Record<string, string> = {
  "G-MAIN": "Main",
  "G-PED": "Pedestrian",
  "G-STAFF": "Staff",
  "G-BUS": "Bus Bay",
  "Main Gate": "Main",
  "Pedestrian Gate": "Pedestrian",
  "Staff Gate": "Staff",
  "Bus Bay": "Bus Bay",
};

export function studentClassLabel(student?: Pick<Student, "class" | "section"> | null): string {
  if (!student) return "—";
  const klass = String(student.class || "").trim();
  const section = String(student.section || "").trim();
  if (!klass && !section) return "—";
  if (!section) return `Class ${klass}`;
  return `Class ${klass}-${section}`;
}

export function pickupGateLabel(gateId?: string | null, gates: Gate[] = []): string {
  if (!gateId) return "—";
  if (GATE_SHORT[gateId]) return GATE_SHORT[gateId];
  const found = gates.find((g) => g.id === gateId || g.name === gateId);
  if (found) return GATE_SHORT[found.name] || found.name.replace(/ Gate$/, "");
  return GATE_SHORT[String(gateId)] || String(gateId);
}

export function custodyBadgeClass(flag?: string | null): string {
  if (flag === "court_order") return "badge-court";
  if (flag === "restricted") return "badge-restricted";
  return "";
}

export function custodyBadgeLabel(flag?: string | null): string {
  if (flag === "court_order") return "COURT ORDER";
  if (flag === "restricted") return "RESTRICTED";
  return "";
}

export function pickupStatusClass(status?: string | null): string {
  const map: Record<string, string> = {
    Released: "chip-released",
    BlockedNotAuthorized: "chip-blocked-auth",
    BlockedCustody: "chip-blocked-custody",
    ReleasedWithOverride: "chip-override",
    Matching: "chip-matching",
    Draft: "chip-matching",
  };
  return map[status || ""] || "";
}

