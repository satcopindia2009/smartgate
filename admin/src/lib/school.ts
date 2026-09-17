import { DEMO_SCHOOL_ID } from "./constants";
import type { AuthUser } from "./types";

export const PRANAY_SCHOOL_ID = "SCH-PRANAY-01";
export const PRANAY_SCHOOL_CODE = "PRANAY";
export const PRANAY_SCHOOL_NAME = "Pranay School Pune";
export const DEMO_SCHOOL_NAME = "Demo International School";

export function isDemoSchoolId(schoolId?: string | null): boolean {
  return (schoolId || "").trim() === DEMO_SCHOOL_ID;
}

/** True for real tenants — no DEMO watermark, no getFixtureSession fallback. */
export function withoutDemoChrome(
  user?: Pick<AuthUser, "schoolId"> | null,
): boolean {
  return !isDemoSchoolId(user?.schoolId);
}

function pickStr(...vals: unknown[]): string | null {
  for (const v of vals) {
    if (typeof v === "string" && v.trim()) return v.trim();
  }
  return null;
}

const SCHOOL_DIRECTORY: Record<string, { name: string; code: string }> = {
  [DEMO_SCHOOL_ID]: { name: DEMO_SCHOOL_NAME, code: DEMO_SCHOOL_ID },
  [PRANAY_SCHOOL_ID]: { name: PRANAY_SCHOOL_NAME, code: PRANAY_SCHOOL_CODE },
};

export function schoolRecord(schoolId?: string | null) {
  return SCHOOL_DIRECTORY[(schoolId || "").trim()] || null;
}

export function schoolDisplayName(
  user?: Pick<AuthUser, "schoolId" | "schoolName"> | null,
): string {
  const rec = schoolRecord(user?.schoolId);
  if (rec) return rec.name;
  const named = pickStr(user?.schoolName);
  if (named && named === DEMO_SCHOOL_NAME && !isDemoSchoolId(user?.schoolId)) {
    return (user?.schoolId || "").trim() || "School";
  }
  if (named) return named;
  return (user?.schoolId || "").trim() || "School";
}

export function tenantSchoolCode(
  schoolId?: string | null,
  schoolCode?: string | null,
): string | null {
  const explicit = (schoolCode || "").trim();
  if (explicit) return explicit;
  return schoolRecord(schoolId)?.code || null;
}

/** Template school_code: first real school PRANAY. Never SCH-DEMO-01. */
export function templateSchoolCode(
  schoolId?: string | null,
  schoolCode?: string | null,
): string {
  const id = (schoolId || "").trim();
  const code = (schoolCode || "").trim();
  if (id === PRANAY_SCHOOL_ID) return PRANAY_SCHOOL_CODE;
  if (code && id !== DEMO_SCHOOL_ID && code !== DEMO_SCHOOL_ID) return code;
  if (id && id !== DEMO_SCHOOL_ID) {
    return schoolRecord(id)?.code || PRANAY_SCHOOL_CODE;
  }
  return PRANAY_SCHOOL_CODE;
}

export function acceptedSchoolCodes(
  schoolId?: string | null,
  schoolCode?: string | null,
): string[] {
  const out = new Set<string>();
  const id = (schoolId || "").trim();
  const code = (schoolCode || "").trim();
  if (id) out.add(id);
  if (code) out.add(code);
  const rec = schoolRecord(id);
  if (rec?.code) out.add(rec.code);
  return [...out];
}

export function authUserFromPayload(
  raw: AuthUser & { school_name?: string | null; school_code?: string | null; name?: string | null },
): AuthUser {
  return enrichAuthUser({
    ...raw,
    schoolName: pickStr(raw.schoolName, raw.school_name) || raw.schoolName,
    schoolCode: pickStr(raw.schoolCode, raw.school_code) || raw.schoolCode,
  });
}

export function enrichAuthUser(user: AuthUser): AuthUser {
  const rec = schoolRecord(user.schoolId);
  let schoolName = pickStr(user.schoolName) || rec?.name || null;
  let schoolCode = pickStr(user.schoolCode) || rec?.code || null;
  if (!isDemoSchoolId(user.schoolId)) {
    if (schoolName === DEMO_SCHOOL_NAME) schoolName = rec?.name || user.schoolId || null;
    if (schoolCode === DEMO_SCHOOL_ID) schoolCode = rec?.code || null;
  }
  return {
    ...user,
    schoolName,
    schoolCode,
  };
}
