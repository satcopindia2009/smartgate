import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { AUTH_STORAGE_KEY } from "../lib/constants";
import { ApiError, isNetworkError, loginApi, meApi } from "../lib/api";
import { fixtureLogin } from "../lib/fixtures";
import type { AuthUser, DataSource } from "../lib/types";

interface StoredAuth {
  token: string;
  user: AuthUser;
  source: DataSource;
}

interface AuthContextValue {
  ready: boolean;
  token: string | null;
  user: AuthUser | null;
  source: DataSource;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readStored(): StoredAuth | null {
  try {
    const raw = sessionStorage.getItem(AUTH_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as StoredAuth) : null;
  } catch {
    return null;
  }
}

function writeStored(value: StoredAuth | null) {
  if (!value) {
    sessionStorage.removeItem(AUTH_STORAGE_KEY);
    return;
  }
  sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(value));
}

function assertAdminRole(user: AuthUser) {
  if (user.role !== "admin" && user.role !== "security_head") {
    throw new Error("This dashboard is for Office Admin and Security Head only.");
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [source, setSource] = useState<DataSource>("api");

  const apply = useCallback((next: StoredAuth | null) => {
    writeStored(next);
    setToken(next?.token ?? null);
    setUser(next?.user ?? null);
    setSource(next?.source ?? "api");
  }, []);

  useEffect(() => {
    const stored = readStored();
    if (!stored) {
      setReady(true);
      return;
    }
    if (stored.source === "fixtures" || stored.token.startsWith("fixture:")) {
      apply({ ...stored, source: "fixtures" });
      setReady(true);
      return;
    }
    meApi(stored.token)
      .then((me) => {
        assertAdminRole(me);
        apply({ token: stored.token, user: me, source: "api" });
      })
      .catch(() => {
        apply(null);
      })
      .finally(() => setReady(true));
  }, [apply]);

  const login = useCallback(
    async (username: string, password: string) => {
      try {
        const res = await loginApi(username, password);
        assertAdminRole(res.user);
        apply({ token: res.accessToken, user: res.user, source: "api" });
      } catch (err) {
        if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
          if (err.code === "FORBIDDEN" || err.message.includes("Office Admin")) {
            throw err;
          }
          const local = fixtureLogin(username, password);
          if (local && err.status === 403) {
            throw new Error("This dashboard is for Office Admin and Security Head only.");
          }
          throw new Error(err.message || "Invalid username or password");
        }
        if (err instanceof Error && err.message.includes("Office Admin")) {
          throw err;
        }
        if (isNetworkError(err) || err instanceof ApiError) {
          const local = fixtureLogin(username, password);
          if (!local) {
            throw new Error(
              err instanceof ApiError ? err.message : "Invalid username or password",
            );
          }
          apply({
            token: `fixture:${username}`,
            user: local,
            source: "fixtures",
          });
          return;
        }
        const local = fixtureLogin(username, password);
        if (local) {
          apply({
            token: `fixture:${username}`,
            user: local,
            source: "fixtures",
          });
          return;
        }
        throw err instanceof Error ? err : new Error("Login failed");
      }
    },
    [apply],
  );

  const logout = useCallback(() => apply(null), [apply]);

  const value = useMemo(
    () => ({ ready, token, user, source, login, logout }),
    [ready, token, user, source, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}

export function canWriteBlacklist(role?: string | null) {
  return role === "security_head";
}

export function canForceCheckout(role?: string | null) {
  return role === "admin" || role === "security_head";
}

export function canEditPickupList(role?: string | null) {
  return role === "admin" || role === "security_head";
}

export function canSetCourtOrder(role?: string | null) {
  return role === "security_head";
}

export function canEditCampusHours(role?: string | null) {
  return role === "admin" || role === "security_head";
}

export function canApproveAfterHours(role?: string | null) {
  return role === "security_head";
}

export function canTriggerBlast(role?: string | null) {
  return role === "admin" || role === "security_head";
}
