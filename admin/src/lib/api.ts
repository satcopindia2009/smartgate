import { API_BASE } from "./constants";
import type {
  ApiList,
  ApiVisit,
  AuthUser,
  BlacklistEntry,
  Gate,
  GateReport,
  LoginResponse,
  Staff,
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

export function visitorTypeMix(token: string, from: string, to: string) {
  return apiRequest<ApiList<{ visitorType: string; count: number }>>(
    `/reports/visitor-type-mix?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    { token },
  );
}
