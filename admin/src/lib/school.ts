import { DEMO_SCHOOL_ID } from "./constants";
import type { AuthUser } from "./types";

export const PRANAY_SCHOOL_ID = "SCH-PRANAY-01";
export const PRANAY_SCHOOL_CODE = "PRANAY";
export const PRANAY_SCHOOL_NAME = "Pranay School Pune";
export const DEMO_SCHOOL_NAME = "Demo International School";

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
  const named = (user?.schoolName || "").trim();
  if (named) return named;
  const rec = schoolRecord(user?.schoolId);
  if (rec) return rec.name;
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

export function enrichAuthUser(user: AuthUser): AuthUser {
  const rec = schoolRecord(user.schoolId);
  return {
    ...user,
    schoolName: user.schoolName || rec?.name || null,
    schoolCode: user.schoolCode || rec?.code || null,
  };
}
