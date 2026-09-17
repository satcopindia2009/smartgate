export const SCHOOL_TZ = "Asia/Calcutta";
export const CAMPUS_TZ = "Asia/Kolkata";
export const SCHOOL_NAME = "Demo International School";
export const OVERDUE_HOURS_DEFAULT = 4;
export const LIVE_REFRESH_MS = 30_000;
export const API_BASE =
  import.meta.env.VITE_API_BASE_URL ||
  "https://replacing-spyware-yes-due.trycloudflare.com/v1";

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
export const PICKUP_IMPORT_STATE_KEY = "satcop-admin-pickup-import-overlay";
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

/** Reserved demo tenant — never the import default. */
export const DEMO_SCHOOL_ID = "SCH-DEMO-01";
/** First real school. Template school_code is PRANAY; JWT schoolId is SCH-PRANAY-01. */
export const FIRST_SCHOOL_NAME_EXAMPLE = "Pranay School Pune";
export const FIRST_SCHOOL_CODE_EXAMPLE = "PRANAY";
export const IMPORT_MAX_ROWS = 5_000;

export const DEMO_LOGINS = {
  admin: {
    password: "admin123",
    role: "admin" as const,
    displayName: "Office Admin",
    id: "U-ADMIN",
    schoolId: DEMO_SCHOOL_ID,
    schoolCode: DEMO_SCHOOL_ID,
    staffId: "H02",
  },
  security: {
    password: "sh123",
    role: "security_head" as const,
    displayName: "Security Head",
    id: "U-SH",
    schoolId: DEMO_SCHOOL_ID,
    schoolCode: DEMO_SCHOOL_ID,
    staffId: null,
  },
  "pranay.admin": {
    password: "PranayAdmin@2026",
    role: "admin" as const,
    displayName: "Pranay School Admin",
    id: "U-PRANAY-ADMIN",
    schoolId: "SCH-PRANAY-01",
    schoolCode: "PRANAY",
    staffId: "PS-H02",
  },
  "pranay.sh": {
    password: "PranaySH@2026",
    role: "security_head" as const,
    displayName: "Pranay Security Head",
    id: "U-PRANAY-SH",
    schoolId: "SCH-PRANAY-01",
    schoolCode: "PRANAY",
    staffId: "PS-H01",
  },
} as const;
