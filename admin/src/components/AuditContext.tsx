import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import type { ExportScope } from "./ExportModal";

export interface AuditRow {
  who: string;
  when: string;
  scope: ExportScope;
  filter: string;
}

const LABELS: Record<ExportScope, string> = {
  history: "History (filtered)",
  inside: "Currently inside",
  blacklist: "Blacklist",
  gates: "Daily gate summary",
};

const AuditContext = createContext<{
  rows: AuditRow[];
  pushAudit: (who: string, when: string, scope: ExportScope, filter: string) => void;
} | null>(null);

export function AuditProvider({ children }: { children: ReactNode }) {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const pushAudit = useCallback((who: string, when: string, scope: ExportScope, filter: string) => {
    setRows((prev) => [{ who, when, scope, filter }, ...prev].slice(0, 12));
  }, []);
  const value = useMemo(() => ({ rows, pushAudit }), [rows, pushAudit]);
  return <AuditContext.Provider value={value}>{children}</AuditContext.Provider>;
}

export function useAudit() {
  const ctx = useContext(AuditContext);
  if (!ctx) throw new Error("useAudit must be used within AuditProvider");
  return ctx;
}

export function AuditPanel() {
  const { rows } = useAudit();
  return (
    <div className="audit-panel" aria-live="polite">
      <h3>CSV export audit (demo)</h3>
      <div className="audit-list">
        {rows.length === 0 ? (
          <div className="audit-empty">No exports yet · Admin / Security Head · max 10k · UTF-8 · IST</div>
        ) : (
          rows.map((e, i) => (
            <div className="audit-row" key={`${e.when}-${i}`}>
              <strong>{e.who}</strong> · {e.when}
              <br />
              Scope: <strong>{LABELS[e.scope]}</strong> · Filter: {e.filter}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
