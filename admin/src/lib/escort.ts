import { AFTER_HOURS_FIXTURE_STATE_KEY } from "./constants";
import type { EscortZoneRule, ZoneKey, ZoneLabel } from "./types";
import { VISITOR_TYPES } from "./constants";

export const ZONE_KEYS: ZoneKey[] = [
  "reception",
  "admin",
  "classroom",
  "sports",
  "lab",
  "restricted",
  "parking",
];

export const ZONE_DEFAULT_LABELS: Record<ZoneKey, string> = {
  reception: "Reception / Lobby",
  admin: "Admin block",
  classroom: "Classroom wing",
  sports: "Sports / playground",
  lab: "Labs / IT",
  restricted: "Restricted (principal / accounts / stores / server)",
  parking: "Bus bay / parking",
};

const ESCORT_DEFAULTS: Record<string, { escortRequired: boolean; allowedZones: ZoneKey[] }> = {
  Vendor: { escortRequired: true, allowedZones: ["reception", "admin"] },
  Parent: { escortRequired: false, allowedZones: ["reception"] },
  Guest: { escortRequired: false, allowedZones: ["reception"] },
  Official: { escortRequired: false, allowedZones: ["reception", "admin"] },
  Alumni: { escortRequired: false, allowedZones: ["reception"] },
};

export function seedZones(): ZoneLabel[] {
  return ZONE_KEYS.map((key) => ({
    key,
    label: ZONE_DEFAULT_LABELS[key],
    schoolId: "SCH-DEMO-01",
    updatedByUserId: "U-ADMIN",
  }));
}

export function seedEscortRules(): EscortZoneRule[] {
  return VISITOR_TYPES.map((visitorType) => {
    const def = ESCORT_DEFAULTS[visitorType] || ESCORT_DEFAULTS.Guest;
    return {
      schoolId: "SCH-DEMO-01",
      visitorType,
      escortRequired: def.escortRequired,
      allowedZones: [...def.allowedZones],
      updatedByUserId: "U-ADMIN",
    };
  });
}

export function zoneLabelMap(zones: ZoneLabel[]): Record<string, string> {
  const map: Record<string, string> = {};
  zones.forEach((z) => {
    map[z.key] = z.label;
  });
  ZONE_KEYS.forEach((key) => {
    if (!map[key]) map[key] = ZONE_DEFAULT_LABELS[key];
  });
  return map;
}

export function formatAllowedZones(keys: string[] | undefined, zones?: ZoneLabel[]): string {
  if (!keys || !keys.length) return "—";
  const labels = zoneLabelMap(zones || []);
  return keys.map((k) => labels[k] || k).join(", ");
}

export function escortCell(required?: boolean, name?: string | null, waived?: boolean): string {
  if (waived) return "Waived";
  if (!required) return "No";
  return name ? name : "Required";
}

const ESCORT_STATE_KEY = `${AFTER_HOURS_FIXTURE_STATE_KEY}-escort`;

export interface EscortFixtureSession {
  zones: ZoneLabel[];
  rules: EscortZoneRule[];
}

let session: EscortFixtureSession | null = null;

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T;
}

export function getEscortFixtureSession(
  zones?: ZoneLabel[],
  rules?: EscortZoneRule[],
): EscortFixtureSession {
  if (session) return session;
  try {
    const raw = sessionStorage.getItem(ESCORT_STATE_KEY);
    if (raw) {
      session = JSON.parse(raw) as EscortFixtureSession;
      return session;
    }
  } catch {
    /* ignore */
  }
  session = {
    zones: clone(zones && zones.length ? zones : seedZones()),
    rules: clone(rules && rules.length ? rules : seedEscortRules()),
  };
  return session;
}

function persist(): void {
  if (!session) return;
  try {
    sessionStorage.setItem(ESCORT_STATE_KEY, JSON.stringify(session));
  } catch {
    /* ignore */
  }
}

export function saveZonesLocal(zones: ZoneLabel[]): ZoneLabel[] {
  const sess = getEscortFixtureSession();
  sess.zones = zones.map((z) => ({ ...z, updatedAt: new Date().toISOString() }));
  persist();
  return clone(sess.zones);
}

export function saveRulesLocal(rules: EscortZoneRule[]): EscortZoneRule[] {
  const sess = getEscortFixtureSession();
  sess.rules = rules.map((r) => ({
    ...r,
    escortRequired: r.allowedZones.includes("restricted") ? true : r.escortRequired,
    updatedAt: new Date().toISOString(),
  }));
  persist();
  return clone(sess.rules);
}
