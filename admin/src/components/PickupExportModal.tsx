import { FormEvent, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { CSV_MAX_ROWS, downloadCsv, istFileStamp, rowsToCsv } from "../lib/csv";
import { useToast } from "./Toast";
import { IconExport } from "./Icons";

interface Props {
  open: boolean;
  filter: string;
  headers: string[];
  rows: unknown[][];
  onClose: () => void;
  onAudit: (who: string, when: string, scope: "pickup_history", filter: string) => void;
}

export function PickupExportModal({ open, filter, headers, rows, onClose, onAudit }: Props) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [purpose, setPurpose] = useState("");

  if (!open) return null;

  const who = user?.displayName || (user?.role === "security_head" ? "Security Head" : "Office Admin");

  function confirm(e: FormEvent) {
    e.preventDefault();
    const why = purpose.trim();
    if (!why) {
      showToast("Export purpose required (AC-D9)", "warning");
      return;
    }
    if (!rows.length) {
      showToast("No rows to export for this filter", "warning");
      return;
    }
    if (rows.length > CSV_MAX_ROWS) {
      showToast("Over 10k rows — narrow the filter", "warning");
      return;
    }
    const when =
      new Date().toLocaleString("en-IN", {
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
      `satcop-vms-pickup-history-${istFileStamp()}-IST.csv`,
      rowsToCsv(headers, rows),
    );
    onAudit(who, when, "pickup_history", `${filter} · purpose=${why} · ${rows.length} rows`);
    setPurpose("");
    onClose();
    showToast(`Pickup history CSV · ${rows.length} rows · purpose logged`, "success");
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
        <h3>Export pickup history</h3>
        <p className="modal-sub">Purpose required for bulk pickup export (AC-D9) · UTF-8 · IST · DEMO</p>
        <form onSubmit={confirm}>
          <div className="field">
            <label htmlFor="pickup-export-purpose">
              Export purpose <span className="req">*</span>
            </label>
            <input
              id="pickup-export-purpose"
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              placeholder="e.g. Weekly security audit"
              autoFocus
            />
          </div>
          <p className="export-meta">
            Exported by {who}
            <br />
            Filter: {filter || "all pickup events"}
            <br />
            Rows: {rows.length}
          </p>
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={!purpose.trim()}>
              <IconExport />
              Download CSV
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
