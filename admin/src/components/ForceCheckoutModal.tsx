import { useEffect, useState } from "react";
import type { LiveVisitor } from "../lib/types";

interface Props {
  visitor: LiveVisitor | null;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}

export function ForceCheckoutModal({ visitor, busy, onCancel, onConfirm }: Props) {
  const [reason, setReason] = useState("");

  useEffect(() => {
    setReason("");
  }, [visitor]);

  useEffect(() => {
    if (!visitor) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onCancel();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [visitor, onCancel]);

  if (!visitor) return null;

  const trimmed = reason.trim();

  return (
    <div
      className="modal-overlay open"
      role="dialog"
      aria-modal="true"
      aria-labelledby="force-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="modal">
        <h3 id="force-title">Force checkout</h3>
        <p className="modal-sub">
          {visitor.name} · {visitor.passId} — reason required for Admin or Security Head
        </p>
        <label htmlFor="force-reason" className="sr-only">
          Reason
        </label>
        <textarea
          id="force-reason"
          value={reason}
          autoFocus
          placeholder="e.g. School closing bell / visitor left without scan"
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
            onClick={() => onConfirm(trimmed)}
          >
            Force checkout
          </button>
        </div>
      </div>
    </div>
  );
}
