import {
  CUSTODY_FLAGS,
  DEMO_SCHOOL_ID,
  FIRST_SCHOOL_CODE_EXAMPLE,
  ID_TYPES,
  IMPORT_MAX_ROWS,
  PICKUP_CONSENT_VERSION,
  PICKUP_IMPORT_STATE_KEY,
  PICKUP_RELATIONS,
} from "./constants";
import { downloadCsv, rowsToCsv } from "./csv";
import type {
  AuthorizedPickupPerson,
  CustodyFlagRecord,
  CustodyFlagValue,
  PickupRelation,
  Student,
  StudentImportError,
  StudentImportResult,
  StudentImportRow,
} from "./types";

export const IMPORT_SOT_HEADERS = [
  "school_code",
  "student_external_id",
  "student_name",
  "class",
  "section",
  "person_name",
  "relation",
  "mobile",
  "id_last4",
  "id_type",
  "effective_from",
  "effective_to",
  "custody_flag",
  "gate_instruction",
  "allowed_person_mobiles",
  "blocked_person_mobiles",
  "consent_version",
  "consent_at",
  "person_active",
  "legal_hold",
] as const;

const REQUIRED_HEADERS = [
  "student_external_id",
  "student_name",
  "class",
  "section",
  "person_name",
  "relation",
  "mobile",
] as const;

const FORBIDDEN_HEADER_RE =
  /(court[_\s-]?pdf|case[_\s-]?narrative|document[_\s-]?(url|path|file)|opposing[_\s-]?(party|address)|image[_\s-]?(url|bytes|data|file)|court[_\s-]?(doc|file|order[_\s-]?(pdf|url|path)))/i;

const DEMO_SEED_INTERNAL_IDS = new Set(["STU-AARAV", "STU-KABIR"]);
const DEMO_SEED_EXTERNAL_IDS = new Set(["5b-17", "3a-09", "4a-09"]);
const DEMO_SEED_NAMES = new Set(["aarav mehta", "kabir singh"]);

const RELATION_SET = new Set<string>(PICKUP_RELATIONS);
const ID_TYPE_SET = new Set<string>(ID_TYPES);
const CUSTODY_SET = new Set<string>(CUSTODY_FLAGS);

export interface NormalizedImportRow extends StudentImportRow {
  rowNumber: number;
  mobileDigits: string;
  relationNorm: PickupRelation;
  custodyNorm: CustodyFlagValue;
  personActive: boolean;
  legalHold: boolean;
  allowedMobiles: string[];
  blockedMobiles: string[];
}

export interface ImportFileError {
  code: string;
  field?: string;
  message: string;
}

export interface ImportPreview {
  filename: string;
  csvText: string;
  rows: StudentImportRow[];
  validRows: NormalizedImportRow[];
  result: StudentImportResult;
  fileErrors: ImportFileError[];
}

export interface ImportOverlay {
  schoolId: string;
  students: Student[];
  people: Record<string, AuthorizedPickupPerson[]>;
  custody: Record<string, CustodyFlagRecord>;
}

export function isDemoSchoolId(schoolId?: string | null): boolean {
  return (schoolId || "").trim() === DEMO_SCHOOL_ID;
}

export function isDemoSeedStudent(student?: Pick<Student, "id" | "studentId" | "name"> | null): boolean {
  if (!student) return false;
  if (DEMO_SEED_INTERNAL_IDS.has(student.id)) return true;
  if (DEMO_SEED_EXTERNAL_IDS.has(String(student.studentId || "").trim().toLowerCase())) return true;
  return DEMO_SEED_NAMES.has(String(student.name || "").trim().toLowerCase());
}

/** Template school_code: JWT tenant if it is not the demo seed; never default SCH-DEMO-01. */
export function templateSchoolCode(schoolId?: string | null): string {
  const id = (schoolId || "").trim();
  if (id && !isDemoSchoolId(id)) return id;
  return FIRST_SCHOOL_CODE_EXAMPLE;
}

export function requireTenantSchoolId(schoolId?: string | null): string {
  const id = (schoolId || "").trim();
  if (!id) {
    throw new Error("Import is tenant-scoped — signed-in JWT schoolId is required.");
  }
  return id;
}

export function parseCsvText(text: string): { headers: string[]; rows: string[][] } {
  const src = String(text || "").replace(/^\uFEFF/, "");
  const table: string[][] = [];
  let row: string[] = [];
  let field = "";
  let i = 0;
  let inQuotes = false;
  while (i < src.length) {
    const c = src[i];
    if (inQuotes) {
      if (c === '"') {
        if (src[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        inQuotes = false;
        i += 1;
        continue;
      }
      field += c;
      i += 1;
      continue;
    }
    if (c === '"') {
      inQuotes = true;
      i += 1;
      continue;
    }
    if (c === ",") {
      row.push(field);
      field = "";
      i += 1;
      continue;
    }
    if (c === "\r") {
      i += 1;
      continue;
    }
    if (c === "\n") {
      row.push(field);
      table.push(row);
      row = [];
      field = "";
      i += 1;
      continue;
    }
    field += c;
    i += 1;
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    table.push(row);
  }
  while (table.length && table[table.length - 1].every((cell) => !String(cell || "").trim())) {
    table.pop();
  }
  const headers = (table[0] || []).map((h) => String(h || "").replace(/^\uFEFF/, "").trim());
  return { headers, rows: table.slice(1) };
}

export function headerKey(name: string): string {
  return String(name || "")
    .replace(/^\uFEFF/, "")
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, "_");
}

export function isForbiddenHeader(name: string): boolean {
  const key = headerKey(name);
  if (!key) return false;
  return FORBIDDEN_HEADER_RE.test(key) || FORBIDDEN_HEADER_RE.test(name);
}

export function normalizeMobileDigits(raw: string): string {
  const digits = String(raw || "").replace(/\D/g, "");
  if (digits.length === 12 && digits.startsWith("91")) return digits.slice(-10);
  if (digits.length === 11 && digits.startsWith("0")) return digits.slice(-10);
  if (digits.length > 10 && digits.startsWith("91")) return digits.slice(-10);
  return digits;
}

function parseYn(raw: string, defaultYes: boolean): boolean | null {
  const s = String(raw || "").trim().toLowerCase();
  if (!s) return defaultYes;
  if (["y", "yes", "true", "1", "active"].includes(s)) return true;
  if (["n", "no", "false", "0", "inactive"].includes(s)) return false;
  return null;
}

function isIsoDate(raw: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(raw);
}

function isIsoDateTime(raw: string): boolean {
  if (isIsoDate(raw)) return true;
  const t = Date.parse(raw);
  return !Number.isNaN(t);
}

function splitMobiles(raw: string): string[] {
  return String(raw || "")
    .split(/[;,\s]+/)
    .map((m) => normalizeMobileDigits(m))
    .filter(Boolean);
}

function emptyRow(): StudentImportRow {
  return {
    school_code: "",
    student_external_id: "",
    student_name: "",
    class: "",
    section: "",
    person_name: "",
    relation: "",
    mobile: "",
    id_last4: "",
    id_type: "",
    effective_from: "",
    effective_to: "",
    custody_flag: "",
    gate_instruction: "",
    allowed_person_mobiles: "",
    blocked_person_mobiles: "",
    consent_version: "",
    consent_at: "",
    person_active: "",
    legal_hold: "",
  };
}

function err(
  row: number,
  field: string | undefined,
  code: string,
  message: string,
  studentExternalId?: string,
): StudentImportError {
  return { row, field: field || null, code, message, studentExternalId: studentExternalId || null };
}

function cellsToRow(headers: string[], cells: string[]): StudentImportRow {
  const out = emptyRow();
  headers.forEach((h, i) => {
    const key = headerKey(h);
    if (key in out) {
      (out as unknown as Record<string, string>)[key] = String(cells[i] ?? "").trim();
    }
  });
  return out;
}

function rowIsBlank(row: StudentImportRow): boolean {
  return IMPORT_SOT_HEADERS.every((h) => !String(row[h] || "").trim());
}

function custodySignature(row: Pick<NormalizedImportRow, "custodyNorm" | "gate_instruction" | "allowedMobiles" | "blockedMobiles">) {
  return JSON.stringify({
    flag: row.custodyNorm,
    gate: (row.gate_instruction || "").trim(),
    allowed: [...row.allowedMobiles].sort(),
    blocked: [...row.blockedMobiles].sort(),
  });
}

export function validateImportRows(
  headers: string[],
  dataRows: string[][],
  tenantSchoolId: string,
  existing: Student[] = [],
  filename = "import.csv",
): { fileErrors: ImportFileError[]; validRows: NormalizedImportRow[]; rows: StudentImportRow[]; result: StudentImportResult } {
  const fileErrors: ImportFileError[] = [];
  const errors: StudentImportError[] = [];
  const mappedHeaders = headers.map(headerKey);

  if (!tenantSchoolId.trim()) {
    fileErrors.push({
      code: "TENANT_REQUIRED",
      message: "Import is tenant-scoped via the signed-in JWT schoolId. No schoolId on this session.",
    });
  }

  const forbidden = headers.filter((h) => isForbiddenHeader(h));
  if (forbidden.length) {
    fileErrors.push({
      code: "FORBIDDEN_COLUMN",
      field: forbidden.join(", "),
      message: `Court PDF / document columns are rejected (P4 / AC-D5 / AC-IMP-4): ${forbidden.join(", ")}`,
    });
  }

  const missing = REQUIRED_HEADERS.filter((h) => !mappedHeaders.includes(h));
  if (missing.length) {
    fileErrors.push({
      code: "MISSING_COLUMN",
      field: missing.join(", "),
      message: `Missing required SoT column(s): ${missing.join(", ")}`,
    });
  }

  if (dataRows.length > IMPORT_MAX_ROWS) {
    fileErrors.push({
      code: "FILE_TOO_LARGE",
      message: `Max ${IMPORT_MAX_ROWS.toLocaleString()} person-rows per file. This file has ${dataRows.length}.`,
    });
  }

  const rows: StudentImportRow[] = [];
  const validRows: NormalizedImportRow[] = [];
  if (fileErrors.length) {
    return {
      fileErrors,
      validRows,
      rows,
      result: {
        imported: 0,
        updated: 0,
        failed: dataRows.length,
        valid: 0,
        errors: fileErrors.map((f) => err(0, f.field, f.code, f.message)),
        dryRun: true,
        source: "local",
        filename,
      },
    };
  }

  dataRows.forEach((cells, idx) => {
    const rowNumber = idx + 2;
    const row = cellsToRow(headers, cells);
    if (rowIsBlank(row)) return;
    rows.push(row);

    const schoolCode = row.school_code.trim();
    if (schoolCode && schoolCode !== tenantSchoolId) {
      errors.push(
        err(
          rowNumber,
          "school_code",
          "TENANT_MISMATCH",
          `school_code '${schoolCode}' does not match signed-in tenant '${tenantSchoolId}'`,
        ),
      );
    }

    if (!row.student_external_id.trim()) {
      errors.push(err(rowNumber, "student_external_id", "MISSING_VALUE", "student_external_id is required"));
    }
    if (!row.student_name.trim()) {
      errors.push(err(rowNumber, "student_name", "MISSING_VALUE", "student_name is required"));
    }
    if (!row.class.trim()) {
      errors.push(err(rowNumber, "class", "MISSING_VALUE", "class is required"));
    }
    if (!row.section.trim()) {
      errors.push(err(rowNumber, "section", "MISSING_VALUE", "section is required"));
    }
    if (!row.person_name.trim()) {
      errors.push(err(rowNumber, "person_name", "MISSING_VALUE", "person_name is required"));
    }

    const relationNorm = row.relation.trim().toLowerCase();
    if (!relationNorm) {
      errors.push(err(rowNumber, "relation", "MISSING_VALUE", "relation is required"));
    } else if (!RELATION_SET.has(relationNorm)) {
      errors.push(
        err(rowNumber, "relation", "INVALID_ENUM", `relation must be ${PICKUP_RELATIONS.join(" | ")}`),
      );
    }

    const mobileDigits = normalizeMobileDigits(row.mobile);
    if (!row.mobile.trim()) {
      errors.push(err(rowNumber, "mobile", "MISSING_VALUE", "mobile is required"));
    } else if (mobileDigits.length !== 10) {
      errors.push(err(rowNumber, "mobile", "INVALID_MOBILE", "mobile must normalize to 10 IN digits"));
    }

    if (row.id_last4 && !/^\d{4}$/.test(row.id_last4)) {
      errors.push(err(rowNumber, "id_last4", "INVALID_ENUM", "id_last4 must be exactly 4 digits"));
    }
    if (row.id_type && !ID_TYPE_SET.has(row.id_type)) {
      errors.push(err(rowNumber, "id_type", "INVALID_ENUM", `id_type must be ${ID_TYPES.join(" | ")}`));
    }
    if (row.effective_from && !isIsoDate(row.effective_from)) {
      errors.push(err(rowNumber, "effective_from", "INVALID_DATE", "effective_from must be YYYY-MM-DD"));
    }
    if (row.effective_to && !isIsoDate(row.effective_to)) {
      errors.push(err(rowNumber, "effective_to", "INVALID_DATE", "effective_to must be YYYY-MM-DD"));
    }
    if (row.consent_at && !isIsoDateTime(row.consent_at)) {
      errors.push(err(rowNumber, "consent_at", "INVALID_DATE", "consent_at must be YYYY-MM-DD or ISO datetime"));
    }

    const custodyRaw = row.custody_flag.trim().toLowerCase() || "none";
    if (!CUSTODY_SET.has(custodyRaw)) {
      errors.push(
        err(rowNumber, "custody_flag", "INVALID_ENUM", `custody_flag must be ${CUSTODY_FLAGS.join(" | ")}`),
      );
    }
    if (custodyRaw === "court_order" && !row.gate_instruction.trim()) {
      errors.push(
        err(
          rowNumber,
          "gate_instruction",
          "F6_GATE_INSTRUCTION",
          "court_order requires a non-empty gate_instruction (F6 · fail closed)",
          row.student_external_id,
        ),
      );
    }
    if (row.gate_instruction.length > 280) {
      errors.push(err(rowNumber, "gate_instruction", "INVALID_ENUM", "gate_instruction must be ≤280 characters"));
    }

    const personActive = parseYn(row.person_active, true);
    if (personActive === null) {
      errors.push(err(rowNumber, "person_active", "INVALID_ENUM", "person_active must be Y or N"));
    }
    const legalHold = parseYn(row.legal_hold, false);
    if (legalHold === null) {
      errors.push(err(rowNumber, "legal_hold", "INVALID_ENUM", "legal_hold must be Y or N"));
    }

    const rowHasError = errors.some((e) => e.row === rowNumber);
    if (rowHasError) return;

    validRows.push({
      ...row,
      rowNumber,
      mobileDigits,
      relationNorm: relationNorm as PickupRelation,
      custodyNorm: custodyRaw as CustodyFlagValue,
      personActive: Boolean(personActive),
      legalHold: Boolean(legalHold),
      allowedMobiles: splitMobiles(row.allowed_person_mobiles),
      blockedMobiles: splitMobiles(row.blocked_person_mobiles),
      consent_version: row.consent_version.trim() || PICKUP_CONSENT_VERSION,
      consent_at: row.consent_at.trim() || new Date().toISOString(),
    });
  });

  const byStudent = new Map<string, NormalizedImportRow[]>();
  validRows.forEach((row) => {
    const key = row.student_external_id.trim().toLowerCase();
    const list = byStudent.get(key) || [];
    list.push(row);
    byStudent.set(key, list);
  });

  const conflictExt = new Set<string>();
  byStudent.forEach((list, ext) => {
    const sigs = new Set(
      list
        .filter((r) => r.custody_flag.trim() || r.gate_instruction.trim() || r.allowedMobiles.length || r.blockedMobiles.length)
        .map((r) => custodySignature(r)),
    );
    if (sigs.size > 1) conflictExt.add(ext);
  });

  if (conflictExt.size) {
    const kept: NormalizedImportRow[] = [];
    validRows.forEach((row) => {
      const ext = row.student_external_id.trim().toLowerCase();
      if (conflictExt.has(ext)) {
        errors.push(
          err(
            row.rowNumber,
            "custody_flag",
            "CUSTODY_CONFLICT",
            `Conflicting custody / gate_instruction / allow-block list for student ${row.student_external_id}`,
          ),
        );
      } else {
        kept.push(row);
      }
    });
    validRows.length = 0;
    validRows.push(...kept);
  }

  const existingExt = new Set(
    existing
      .filter((s) => !s.schoolId || s.schoolId === tenantSchoolId)
      .map((s) => String(s.studentId || "").trim().toLowerCase())
      .filter(Boolean),
  );
  const createExt = new Set<string>();
  const updateExt = new Set<string>();
  validRows.forEach((row) => {
    const ext = row.student_external_id.trim().toLowerCase();
    if (existingExt.has(ext)) updateExt.add(ext);
    else createExt.add(ext);
  });

  const failedRows = new Set(errors.filter((e) => e.row > 0).map((e) => e.row));
  return {
    fileErrors,
    validRows,
    rows,
    result: {
      imported: createExt.size,
      updated: updateExt.size,
      failed: failedRows.size,
      valid: validRows.length,
      errors,
      dryRun: true,
      source: "local",
      filename,
    },
  };
}

export function previewCsvText(
  csvText: string,
  tenantSchoolId: string,
  existing: Student[],
  filename: string,
): ImportPreview {
  const { headers, rows } = parseCsvText(csvText);
  const validated = validateImportRows(headers, rows, tenantSchoolId, existing, filename);
  return {
    filename,
    csvText,
    rows: validated.rows,
    validRows: validated.validRows,
    result: validated.result,
    fileErrors: validated.fileErrors,
  };
}

export function buildTemplateCsv(schoolId?: string | null): string {
  const schoolCode = templateSchoolCode(schoolId);
  const headers = [...IMPORT_SOT_HEADERS];
  const rows: unknown[][] = [
    [
      schoolCode,
      "3A-21",
      "Diya Kulkarni",
      "3",
      "A",
      "Ananya Kulkarni",
      "parent",
      "9000001001",
      "1001",
      "Aadhaar",
      "",
      "",
      "none",
      "",
      "",
      "",
      PICKUP_CONSENT_VERSION,
      "2026-06-15",
      "Y",
      "N",
    ],
    [
      schoolCode,
      "2B-08",
      "Arjun Desai",
      "2",
      "B",
      "Kavya Desai",
      "guardian",
      "9000001002",
      "1002",
      "DL",
      "",
      "",
      "none",
      "",
      "",
      "",
      PICKUP_CONSENT_VERSION,
      "2026-06-15",
      "Y",
      "N",
    ],
  ];
  return rowsToCsv(headers, rows);
}

export function downloadImportTemplate(schoolId?: string | null) {
  downloadCsv("satcop-pickup-import-template.csv", buildTemplateCsv(schoolId));
}

function overlayStorageKey(schoolId: string) {
  return `${PICKUP_IMPORT_STATE_KEY}:${schoolId}`;
}

export function loadImportOverlay(schoolId: string): ImportOverlay {
  const empty: ImportOverlay = { schoolId, students: [], people: {}, custody: {} };
  if (!schoolId) return empty;
  try {
    const raw = sessionStorage.getItem(overlayStorageKey(schoolId));
    if (!raw) return empty;
    const parsed = JSON.parse(raw) as ImportOverlay;
    if (!parsed || parsed.schoolId !== schoolId) return empty;
    return {
      schoolId,
      students: parsed.students || [],
      people: parsed.people || {},
      custody: parsed.custody || {},
    };
  } catch {
    return empty;
  }
}

export function saveImportOverlay(overlay: ImportOverlay) {
  try {
    sessionStorage.setItem(overlayStorageKey(overlay.schoolId), JSON.stringify(overlay));
  } catch {
    /* ignore quota */
  }
}

export function filterStudentsForTenant(students: Student[], tenantSchoolId: string): Student[] {
  if (!tenantSchoolId) return students;
  if (isDemoSchoolId(tenantSchoolId)) {
    return students.filter((s) => !s.schoolId || s.schoolId === DEMO_SCHOOL_ID);
  }
  return students.filter((s) => {
    if (isDemoSeedStudent(s)) return false;
    if (s.schoolId && s.schoolId !== tenantSchoolId) return false;
    return true;
  });
}

export function mergeOverlayStudents(base: Student[], overlay: ImportOverlay, tenantSchoolId: string): Student[] {
  const scoped = filterStudentsForTenant(base, tenantSchoolId);
  const byExt = new Map(scoped.map((s) => [String(s.studentId || s.id).toLowerCase(), s]));
  const out = [...scoped];
  overlay.students.forEach((s) => {
    if (s.schoolId && s.schoolId !== tenantSchoolId) return;
    if (!isDemoSchoolId(tenantSchoolId) && isDemoSeedStudent(s)) return;
    const key = String(s.studentId || s.id).toLowerCase();
    const existing = byExt.get(key);
    if (!existing) {
      out.push(s);
      byExt.set(key, s);
      return;
    }
    const idx = out.findIndex((row) => row.id === existing.id);
    if (idx >= 0) out[idx] = { ...existing, ...s, id: existing.id, schoolId: tenantSchoolId };
  });
  return out;
}

function studentKey(student: Student): string {
  return String(student.studentId || student.id).toLowerCase();
}

export function applyValidRowsLocal(
  tenantSchoolId: string,
  validRows: NormalizedImportRow[],
  existing: Student[],
): StudentImportResult {
  const overlay = loadImportOverlay(tenantSchoolId);
  const known = new Map<string, Student>();
  filterStudentsForTenant([...existing, ...overlay.students], tenantSchoolId).forEach((s) => {
    known.set(studentKey(s), s);
  });

  let imported = 0;
  let updated = 0;
  const now = new Date().toISOString();

  const byExt = new Map<string, NormalizedImportRow[]>();
  validRows.forEach((row) => {
    const key = row.student_external_id.trim().toLowerCase();
    const list = byExt.get(key) || [];
    list.push(row);
    byExt.set(key, list);
  });

  byExt.forEach((list, ext) => {
    const first = list[0];
    let student = known.get(ext);
    if (student && !isDemoSchoolId(tenantSchoolId) && isDemoSeedStudent(student)) {
      student = undefined;
    }
    if (!student) {
      const id = `STU-IMP-${tenantSchoolId}-${first.student_external_id}`
        .toUpperCase()
        .replace(/[^A-Z0-9-]/g, "-")
        .replace(/-+/g, "-")
        .slice(0, 72);
      student = {
        id,
        schoolId: tenantSchoolId,
        studentId: first.student_external_id.trim(),
        name: first.student_name.trim(),
        class: first.class.trim(),
        section: first.section.trim(),
        active: true,
        legalHold: first.legalHold,
        createdAt: now,
        updatedAt: now,
      };
      overlay.students.push(student);
      known.set(ext, student);
      imported += 1;
    } else {
      student = {
        ...student,
        name: first.student_name.trim(),
        class: first.class.trim(),
        section: first.section.trim(),
        legalHold: first.legalHold,
        schoolId: tenantSchoolId,
        updatedAt: now,
      };
      const idx = overlay.students.findIndex((s) => s.id === student!.id);
      if (idx >= 0) overlay.students[idx] = student;
      else overlay.students.push(student);
      known.set(ext, student);
      updated += 1;
    }

    const people = overlay.people[student.id] ? [...overlay.people[student.id]] : [];
    list.forEach((row) => {
      const existingPerson = people.find((p) => normalizeMobileDigits(p.mobile) === row.mobileDigits);
      const person: AuthorizedPickupPerson = {
        id: existingPerson?.id || `APP-IMP-${Date.now()}-${row.mobileDigits}`,
        schoolId: tenantSchoolId,
        studentId: student!.id,
        name: row.person_name.trim(),
        relation: row.relationNorm,
        mobile: row.mobileDigits,
        idType: row.id_type || null,
        idLast4: row.id_last4 || null,
        active: row.personActive,
        effectiveFrom: row.effective_from || null,
        effectiveTo: row.effective_to || null,
        blockedByCustody: existingPerson?.blockedByCustody ?? false,
        pickupConsentVersion: row.consent_version,
        pickupConsentAt: row.consent_at,
        updatedAt: now,
        createdAt: existingPerson?.createdAt || now,
      };
      if (existingPerson) {
        const i = people.findIndex((p) => p.id === existingPerson.id);
        people[i] = person;
      } else {
        people.push(person);
      }
    });
    overlay.people[student.id] = people;

    const lastCustody = [...list].reverse().find((r) => r.custody_flag.trim() || r.gate_instruction.trim());
    if (lastCustody) {
      overlay.custody[student.id] = {
        studentId: student.id,
        schoolId: tenantSchoolId,
        flag: lastCustody.custodyNorm,
        gateInstruction: lastCustody.gate_instruction.slice(0, 280),
        blockedPersonIds: [],
        allowedPersonIds: null,
        updatedAt: now,
      };
    }
  });

  overlay.schoolId = tenantSchoolId;
  saveImportOverlay(overlay);
  return {
    imported,
    updated,
    failed: 0,
    valid: validRows.length,
    errors: [],
    dryRun: false,
    source: "local",
  };
}

export function overlayPeopleFor(schoolId: string, studentId: string): AuthorizedPickupPerson[] | null {
  const overlay = loadImportOverlay(schoolId);
  return overlay.people[studentId] || null;
}

export function overlayCustodyFor(schoolId: string, studentId: string): CustodyFlagRecord | null {
  const overlay = loadImportOverlay(schoolId);
  return overlay.custody[studentId] || null;
}

export function normalizeImportApiResult(
  raw: Record<string, unknown>,
  filename: string,
  dryRun: boolean,
): StudentImportResult {
  const errorsRaw = Array.isArray(raw.errors) ? raw.errors : [];
  const errors: StudentImportError[] = errorsRaw.map((item) => {
    const e = (item || {}) as Record<string, unknown>;
    return {
      row: Number(e.row || 0),
      field: (e.field as string) || null,
      code: String(e.code || "ERROR"),
      message: String(e.message || "Row failed"),
      studentExternalId: (e.studentExternalId as string) || (e.student_external_id as string) || null,
    };
  });
  const imported = Number(raw.imported ?? raw.created ?? 0) || 0;
  const updated = Number(raw.updated ?? 0) || 0;
  const failed = Number(raw.failed ?? errors.length) || 0;
  return {
    imported,
    updated,
    failed,
    valid: Number(raw.valid ?? 0) || undefined,
    errors,
    dryRun,
    source: "api",
    filename,
  };
}
