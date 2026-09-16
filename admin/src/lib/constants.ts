export const SCHOOL_TZ = "Asia/Calcutta";
export const CAMPUS_TZ = "Asia/Kolkata";
export const SCHOOL_NAME = "Demo International School";
export const OVERDUE_HOURS_DEFAULT = 4;
export const LIVE_REFRESH_MS = 30_000;
export const API_BASE =
  import.meta.env.VITE_API_BASE_URL ||
  "https://pensions-usb-loops-direction.trycloudflare.com/v1";

export const GATE_ENUMS = [
  "Main Gate",
  "Pedestrian Gate",
  "Staff Gate",
  "Bus Bay",
] as const;

export const VISITOR_TYPES = [
  "Parent",
  "Vendor",
  "Guest",
  "Official",
  "Alumni",
] as const;

export const ID_TYPES = ["Aadhaar", "DL", "Voter", "Passport", "Other"] as const;

export const AUTH_STORAGE_KEY = "satcop-admin-auth";
export const FIXTURE_STATE_KEY = "satcop-admin-fixture-state";
export const PICKUP_FIXTURE_STATE_KEY = "satcop-admin-pickup-fixture-state";
export const AFTER_HOURS_FIXTURE_STATE_KEY = "satcop-admin-afterhours-fixture-state";
export const BLAST_FIXTURE_STATE_KEY = "satcop-admin-blast-fixture-state";
export const LAST_BLAST_ID_KEY = "satcop-admin-last-blast-id";
export const PICKUP_CONSENT_VERSION = "pickup_notice_en_hi_v1";
export const BLAST_INSTRUCTION_MAX = 160;
export const SEED_BLAST_ID = "B-20260916-03";

export const PICKUP_RELATIONS = ["parent", "guardian", "sibling", "relative", "other"] as const;
export const CUSTODY_FLAGS = ["none", "restricted", "court_order"] as const;
export const PICKUP_STATUSES = [
  "Released",
  "BlockedNotAuthorized",
  "BlockedCustody",
  "ReleasedWithOverride",
  "Matching",
] as const;

export const DEMO_LOGINS = {
  admin: { password: "admin123", role: "admin" as const, displayName: "Office Admin" },
  security: {
    password: "sh123",
    role: "security_head" as const,
    displayName: "Security Head",
  },
} as const;
