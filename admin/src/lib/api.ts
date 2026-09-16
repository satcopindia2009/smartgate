import { API_BASE } from "./constants";
import type {
  ApiList,
  ApiVisit,
  AuthorizedPickupPerson,
  AuthUser,
  BlacklistEntry,
  CustodyFlagRecord,
  Gate,
  GateReport,
  LoginResponse,
  PickupEvent,
  Staff,
  Student,
  CampusHoursRow,
  HolidayEntry,
} from "./types";

export class ApiError extends Error {
  status: number;
  code: string;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function isNetworkError(err: unknown): boolean {
  return (
    err instanceof TypeError ||
    (err instanceof Error && /failed to fetch|network|load/i.test(err.message))
  );
}

async function parseBody(res: Response): Promise<Record<string, unknown>> {
  try {
    return (await res.json()) as Record<string, unknown>;
  } catch {
    return {};
  }
}

export async function apiRequest<T>(
  path: string,
  opts: RequestInit & { token?: string } = {},
): Promise<T> {
  const headers = new Headers(opts.headers);
  if (opts.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (opts.token) headers.set("Authorization", `Bearer ${opts.token}`);

  const res = await fetch(`${API_BASE}${path}`, { ...opts, headers });
  const data = await parseBody(res);
  if (!res.ok) {
    const err = (data.error as { code?: string; message?: string } | undefined) || {};
    throw new ApiError(
      res.status,
      err.code || "ERROR",
      err.message || res.statusText || "Request failed",
    );
  }
  return data as T;
}

export function loginApi(username: string, password: string) {
  return apiRequest<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function meApi(token: string) {
  return apiRequest<AuthUser & { meta?: { watermark?: string } }>("/auth/me", { token });
}

export function listGates(token: string) {
  return apiRequest<ApiList<Gate>>("/gates", { token });
}

export function listStaff(token: string) {
  return apiRequest<ApiList<Staff>>("/staff?active=true", { token });
}

export function listInside(token: string, params: Record<string, string | boolean | undefined> = {}) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === "" || v === false) return;
    q.set(k, String(v));
  });
  const suffix = q.toString() ? `?${q}` : "";
  return apiRequest<ApiList<ApiVisit>>(`/visits/inside${suffix}`, { token });
}

export function forceCheckoutApi(token: string, visitId: string, reason: string) {
  return apiRequest<ApiVisit>(`/visits/${encodeURIComponent(visitId)}/force-checkout`, {
    method: "POST",
    token,
    body: JSON.stringify({ reason }),
  });
}

export function listVisits(
  token: string,
  params: Record<string, string | boolean | undefined> = {},
) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === "" || v === false) return;
    q.set(k, String(v));
  });
  const suffix = q.toString() ? `?${q}` : "";
  return apiRequest<ApiList<ApiVisit>>(`/visits${suffix}`, { token });
}

export function listBlacklist(token: string) {
  return apiRequest<ApiList<BlacklistEntry>>("/blacklist", { token });
}

export function createBlacklist(
  token: string,
  body: Partial<BlacklistEntry> & { name: string; reason: string; severity: string },
) {
  return apiRequest<BlacklistEntry>("/blacklist", {
    method: "POST",
    token,
    body: JSON.stringify(body),
  });
}

export function patchBlacklist(token: string, id: string, body: Partial<BlacklistEntry>) {
  return apiRequest<BlacklistEntry>(`/blacklist/${encodeURIComponent(id)}`, {
    method: "PATCH",
    token,
    body: JSON.stringify(body),
  });
}

export function todayByGate(token: string) {
  return apiRequest<ApiList<GateReport>>("/reports/today-by-gate", { token });
}

export function rangeByGate(token: string, from: string, to: string) {
  return apiRequest<ApiList<GateReport>>(
    `/reports/range-by-gate?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    { token },
  );
}

export function visitorTypeMix(token: string, from: string, to: string) {
  return apiRequest<ApiList<{ visitorType: string; count: number }>>(
    `/reports/visitor-type-mix?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    { token },
  );
}

function querySuffix(params: Record<string, string | boolean | number | undefined> = {}) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === "" || v === false) return;
    q.set(k, String(v));
  });
  return q.toString() ? `?${q}` : "";
}

export function listStudents(
  token: string,
  params: Record<string, string | boolean | number | undefined> = {},
) {
  return apiRequest<ApiList<Student>>(`/students${querySuffix(params)}`, { token });
}

export function listAuthorizedPickup(token: string, studentId: string) {
  return apiRequest<ApiList<AuthorizedPickupPerson>>(
    `/students/${encodeURIComponent(studentId)}/authorized-pickup`,
    { token },
  );
}

export function createAuthorizedPickup(
  token: string,
  studentId: string,
  body: {
    name: string;
    relation: string;
    mobile: string;
    idType?: string | null;
    idNumber?: string | null;
    idLast4?: string | null;
    active?: boolean;
    effectiveFrom?: string | null;
    effectiveTo?: string | null;
    pickupConsentVersion: string;
    pickupConsentAt: string;
  },
) {
  return apiRequest<AuthorizedPickupPerson>(
    `/students/${encodeURIComponent(studentId)}/authorized-pickup`,
    { method: "POST", token, body: JSON.stringify(body) },
  );
}

export function patchAuthorizedPickup(
  token: string,
  studentId: string,
  personId: string,
  body: Partial<AuthorizedPickupPerson>,
) {
  return apiRequest<AuthorizedPickupPerson>(
    `/students/${encodeURIComponent(studentId)}/authorized-pickup/${encodeURIComponent(personId)}`,
    { method: "PATCH", token, body: JSON.stringify(body) },
  );
}

export function getCustodyFlag(token: string, studentId: string) {
  return apiRequest<CustodyFlagRecord>(
    `/students/${encodeURIComponent(studentId)}/custody-flag`,
    { token },
  );
}

export function putCustodyFlag(
  token: string,
  studentId: string,
  body: {
    flag: string;
    gateInstruction?: string;
    blockedPersonIds?: string[];
    allowedPersonIds?: string[] | null;
  },
) {
  return apiRequest<CustodyFlagRecord>(`/students/${encodeURIComponent(studentId)}/custody-flag`, {
    method: "PUT",
    token,
    body: JSON.stringify(body),
  });
}

function asList<T>(value: ApiList<T> | T[] | T): ApiList<T> {
  if (Array.isArray(value)) return { data: value };
  if (value && typeof value === "object" && Array.isArray((value as ApiList<T>).data)) {
    return value as ApiList<T>;
  }
  return { data: value ? [value as T] : [] };
}

export async function getCampusHours(token: string) {
  const res = await apiRequest<ApiList<CampusHoursRow> | CampusHoursRow[]>("/access-rules/hours", {
    token,
  });
  return asList<CampusHoursRow>(res);
}

export async function putCampusHours(token: string, rows: CampusHoursRow[]) {
  const res = await apiRequest<ApiList<CampusHoursRow> | CampusHoursRow[]>("/access-rules/hours", {
    method: "PUT",
    token,
    body: JSON.stringify(rows),
  });
  return asList<CampusHoursRow>(res);
}

export async function listHolidays(token: string) {
  const res = await apiRequest<ApiList<HolidayEntry> | HolidayEntry[]>("/access-rules/holidays", {
    token,
  });
  return asList<HolidayEntry>(res);
}

export async function createHoliday(token: string, body: { date: string; label?: string | null }) {
  const res = await apiRequest<HolidayEntry | ApiList<HolidayEntry>>("/access-rules/holidays", {
    method: "POST",
    token,
    body: JSON.stringify(body),
  });
  if (res && typeof res === "object" && "id" in res) return res as HolidayEntry;
  const list = asList<HolidayEntry>(res);
  const created = list.data[list.data.length - 1];
  if (!created) throw new ApiError(500, "ERROR", "Holiday create returned no row");
  return created;
}

export async function deleteHoliday(token: string, id: string) {
  return apiRequest<Record<string, unknown>>(`/access-rules/holidays/${encodeURIComponent(id)}`, {
    method: "DELETE",
    token,
  });
}

export function approveVisitApi(token: string, visitId: string, reason?: string) {
  return apiRequest<ApiVisit>(`/visits/${encodeURIComponent(visitId)}/approve`, {
    method: "POST",
    token,
    body: JSON.stringify(reason ? { reason } : {}),
  });
}

export function rejectVisitApi(token: string, visitId: string, reason: string) {
  return apiRequest<ApiVisit>(`/visits/${encodeURIComponent(visitId)}/reject`, {
    method: "POST",
    token,
    body: JSON.stringify({ reason }),
  });
}

export function listPickups(
  token: string,
  params: Record<string, string | boolean | undefined> = {},
) {
  return apiRequest<ApiList<PickupEvent>>(`/pickups${querySuffix(params)}`, { token });
}
