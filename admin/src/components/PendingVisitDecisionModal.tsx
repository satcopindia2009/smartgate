import { useEffect, useState } from "react";
import type { HistoryVisit } from "../lib/types";

const REJECT_REASONS = [
  "Host unavailable",
  "Visit postponed",
  "Purpose not valid today",
  "Ask visitor to rebook",
];

interface Props {
  visit: HistoryVisit | null;
  busy?: boolean;
  onCancel: () => void;
  onReject: (reason: string) => void;
}

export function PendingVisitDecisionModal({ visit, busy, onCancel, onReject }: Props) {
  const [reason, setReason] = useState("");
  const [chip, setChip] = useState("");

  useEffect(() => {
    setReason("");
    setChip("");
  }, [visit]);

  useEffect(() => {
    if (!visit) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onCancel();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [visit, onCancel]);

  if (!visit) return null;

  const trimmed = reason.trim();

  return (
    <div
      className="modal-overlay open"
      role="dialog"
      aria-modal="true"
      aria-labelledby="host-pending-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="modal ah-decision-modal">
        <h3 id="host-pending-title">Reject host-pending visit</h3>
        <p className="modal-sub">
          {visit.name} · {visit.type} · {visit.host || "host"} — reject reason required
        </p>
        <div className="reason-chips">
          {REJECT_REASONS.map((label) => (
            <button
              key={label}
              type="button"
              className={`chip${chip === label ? " active" : ""}`}
              onClick={() => {
                setChip(label);
                setReason(label);
              }}
            >
              {label}
            </button>
          ))}
        </div>
        <label htmlFor="host-pending-reason" className="sr-only">
          Reject reason
        </label>
        <textarea
          id="host-pending-reason"
          value={reason}
          autoFocus
          placeholder="Reason for audit (required)"
          onChange={(e) => setReason(e.target.value)}
        />
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-danger"
            disabled={!trimmed || busy}
            onClick={() => onReject(trimmed)}
          >
            Reject
          </button>
        </div>
      </div>
    </div>
  );
}
