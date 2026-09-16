import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { CSV_MAX_ROWS, downloadCsv, istFileStamp, rowsToCsv } from "../lib/csv";
import { useToast } from "./Toast";
import { IconExport } from "./Icons";

export type ExportScope = "history" | "inside" | "blacklist" | "gates";

export interface ExportBundle {
  headers: string[];
  rows: unknown[][];
  filter: string;
}

const LABELS: Record<ExportScope, { title: string; hint: string }> = {
  history: { title: "History (filtered)", hint: "Visit history with current History filters" },
  inside: { title: "Currently inside", hint: "Live board with current Live filters" },
  blacklist: { title: "Blacklist", hint: "All blacklist rows · masked IDs only" },
  gates: { title: "Daily gate summary", hint: "Today / range-by-gate metrics" },
};

interface Props {
  open: boolean;
  initialScope: ExportScope;
  bundles: Partial<Record<ExportScope, ExportBundle>>;
  onClose: () => void;
  onAudit: (who: string, when: string, scope: ExportScope, filter: string) => void;
}

export function ExportModal({ open, initialScope, bundles, onClose, onAudit }: Props) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [scope, setScope] = useState<ExportScope>(initialScope);

  useEffect(() => {
    if (open) setScope(initialScope);
  }, [open, initialScope]);

  if (!open) return null;

  const who = user?.displayName || (user?.role === "security_head" ? "Security Head" : "Office Admin");
  const built = bundles[scope];

  function confirm() {
    if (!built || !built.rows.length) {
      showToast("No rows to export for this scope/filter", "warning");
      return;
    }
    if (built.rows.length > CSV_MAX_ROWS) {
      showToast("Over 10k rows — narrow the date filter", "warning");
      return;
    }
    const when = new Date().toLocaleString("en-IN", {
      day: "numeric",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true,
      timeZone: "Asia/Calcutta",
    }) + " IST";
    downloadCsv(
      `satcop-vms-${scope}-${istFileStamp()}-IST.csv`,
      rowsToCsv(built.headers, built.rows),
    );
    onAudit(who, when, scope, `${built.filter} · ${built.rows.length} rows`);
    onClose();
    showToast(`CSV downloaded · ${built.rows.length} rows · audit logged`, "success");
  }

  return (
    <div
      className="modal-overlay open"
      role="dialog"
      aria-modal="true"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal export-modal">
        <h3>Export CSV</h3>
        <p className="modal-sub">UTF-8 · IST labeled · photo as URL · DEMO watermark · max 10k rows</p>
        <div className="scope-grid" role="radiogroup" aria-label="Export scope">
          {(Object.keys(LABELS) as ExportScope[]).map((key) => (
            <label
              key={key}
              className={`scope-option${scope === key ? " active" : ""}`}
              onClick={() => setScope(key)}
            >
              <input type="radio" name="export-scope" checked={scope === key} readOnly />
              <div>
                <strong>{LABELS[key].title}</strong>
                <span>{LABELS[key].hint}</span>
              </div>
            </label>
          ))}
        </div>
        <p className="export-meta">
          Exported by {who}
          <br />
          Filter: {built?.filter || "—"}
        </p>
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn-primary" onClick={confirm}>
            <IconExport />
            Download CSV
          </button>
        </div>
      </div>
    </div>
  );
}

export function ExportButton({ onClick, title }: { onClick: () => void; title?: string }) {
  return (
    <button type="button" className="btn btn-ghost" onClick={onClick} title={title || "Export CSV"}>
      <IconExport />
      Export
    </button>
  );
}
