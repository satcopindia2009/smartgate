import { useEffect, useState } from "react";
import { policyTriggerLabel } from "../lib/afterHours";
import type { HistoryVisit } from "../lib/types";

const REASONS = [
  "Urgent facility work",
  "Pre-arranged vendor",
  "Holiday walk-in verified",
  "Reject — return tomorrow",
];

interface Props {
  visit: HistoryVisit | null;
  busy?: boolean;
  onCancel: () => void;
  onDecide: (action: "approve" | "reject", reason: string) => void;
}

export function AfterHoursDecisionModal({ visit, busy, onCancel, onDecide }: Props) {
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
  const trigger = policyTriggerLabel(visit.policyTrigger);

  return (
    <div
      className="modal-overlay open"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ah-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="modal ah-decision-modal">
        <h3 id="ah-title">After-hours decision</h3>
        <p className="modal-sub">
          {visit.name} · {visit.type} · {trigger} — Admin or Security Head · reason required (A6)
        </p>
        <div className="reason-chips">
          {REASONS.map((label) => (
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
        <label htmlFor="ah-reason" className="sr-only">
          Decision reason
        </label>
        <textarea
          id="ah-reason"
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
            onClick={() => onDecide("reject", trimmed)}
          >
            Reject
          </button>
          <button
            type="button"
            className="btn btn-success"
            disabled={!trimmed || busy}
            onClick={() => onDecide("approve", trimmed)}
          >
            Approve
          </button>
        </div>
      </div>
    </div>
  );
}
