import { useEffect, useMemo } from "react";
import { BLAST_INSTRUCTION_MAX } from "../lib/constants";
import {
  blastIdOf,
  blastStatusClass,
  countsLabel,
  recipientStatusClass,
} from "../lib/blast";
import { formatDateTime } from "../lib/format";
import type { BlastPreview, BlastTemplate, EmergencyBlast } from "../lib/types";

type Stage = "confirm" | "results";

interface Props {
  open: boolean;
  stage: Stage;
  templates: BlastTemplate[];
  preview: BlastPreview | null;
  blast: EmergencyBlast | null;
  selectedTemplateId: string;
  instruction: string;
  confirmed: boolean;
  busy?: boolean;
  loadError?: string | null;
  onTemplateChange: (id: string) => void;
  onInstructionChange: (value: string) => void;
  onConfirmedChange: (value: boolean) => void;
  onCancel: () => void;
  onConfirmSend: () => void;
  onRetryFailed: () => void;
}

export function EmergencyBlastModal({
  open,
  stage,
  templates,
  preview,
  blast,
  selectedTemplateId,
  instruction,
  confirmed,
  busy,
  loadError,
  onTemplateChange,
  onInstructionChange,
  onConfirmedChange,
  onCancel,
  onConfirmSend,
  onRetryFailed,
}: Props) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !busy) onCancel();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, busy, onCancel]);

  const template = useMemo(
    () => templates.find((t) => t.id === selectedTemplateId) || templates[0] || null,
    [templates, selectedTemplateId],
  );

  if (!open) return null;

  const previewText = instruction.trim() || preview?.instructionPreview || template?.instruction || "";
  const insideCount = preview?.insideCount ?? 0;
  const smsPlanned = preview?.channelsSummary?.sms?.planned ?? insideCount;
  const waHold = preview?.channelsSummary?.whatsappHold !== false;
  const canSend = Boolean(template && confirmed && !busy && !loadError);
  const failed = blast?.counts?.failed || 0;

  return (
    <div
      className="modal-overlay open"
      role="dialog"
      aria-modal="true"
      aria-labelledby="blast-title"
      onClick={(e) => {
        if (e.target === e.currentTarget && !busy) onCancel();
      }}
    >
      <div className="modal blast-modal">
        {stage === "confirm" ? (
          <>
            <h3 id="blast-title">Emergency blast</h3>
            <p className="modal-sub">
              Admin | Security Head · confirm required (B2) · audience = currently inside (B1)
            </p>
            {loadError && <p className="login-error">{loadError}</p>}
            <div className="blast-audience">
              <strong>{insideCount}</strong> currently inside will be messaged
              <span className="subline">
                SMS mock · {smsPlanned} planned
                {waHold ? " · WhatsApp HOLD → skipped_hold" : ""} · escort staff out
              </span>
            </div>
            <div className="field">
              <label>Template</label>
              <div className="blast-template-picks">
                {templates.map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    className={`chip${selectedTemplateId === t.id ? " active" : ""}`}
                    onClick={() => onTemplateChange(t.id)}
                    disabled={busy}
                  >
                    {t.name}
                  </button>
                ))}
              </div>
            </div>
            <div className="blast-preview">
              <div className="blast-preview-label">Template preview</div>
              <p>{previewText || "Select a template"}</p>
              <div className="subline">
                Channel {template?.channel || "sms"} · template-only (B4) · optional short instruction
                below overrides this text
              </div>
            </div>
            <div className="field">
              <label htmlFor="blast-instruction">
                Optional short instruction <span className="dim">({instruction.length}/{BLAST_INSTRUCTION_MAX})</span>
              </label>
              <textarea
                id="blast-instruction"
                value={instruction}
                maxLength={BLAST_INSTRUCTION_MAX}
                placeholder="Optional · max 160 characters · leave blank to send the template as-is"
                onChange={(e) => onInstructionChange(e.target.value)}
                disabled={busy}
              />
            </div>
            <label className="blast-confirm-check">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => onConfirmedChange(e.target.checked)}
                disabled={busy}
              />
              <span>
                I confirm sending this template to everyone currently inside. This does{" "}
                <strong>not</strong> check anyone out (B6).
              </span>
            </label>
            <div className="modal-actions">
              <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={busy}>
                Cancel
              </button>
              <button
                type="button"
                className="btn btn-danger"
                disabled={!canSend}
                onClick={onConfirmSend}
              >
                {busy ? "Sending…" : "Confirm & send"}
              </button>
            </div>
          </>
        ) : (
          <>
            <h3 id="blast-title">Blast results</h3>
            <p className="modal-sub">
              Full audit (B5) · SMS mock / WA skipped_hold (B3) · who’s-inside unchanged (B6)
            </p>
            {blast ? (
              <>
                <div className="blast-audit-meta">
                  <div>
                    <span className="dim">Blast</span>
                    <strong>
                      <code>{blastIdOf(blast)}</code>
                    </strong>
                  </div>
                  <div>
                    <span className="dim">Status</span>
                    <span className={blastStatusClass(blast.status)}>{blast.status}</span>
                  </div>
                  <div>
                    <span className="dim">Triggered</span>
                    <strong>
                      {blast.triggeredByUserId} · {formatDateTime(blast.triggeredAt)}
                    </strong>
                  </div>
                  <div>
                    <span className="dim">Confirmed</span>
                    <strong>{formatDateTime(blast.confirmAt)}</strong>
                  </div>
                  <div>
                    <span className="dim">Template</span>
                    <strong>{blast.templateId}</strong>
                  </div>
                  <div>
                    <span className="dim">Audience</span>
                    <strong>
                      {blast.insideCount} inside · {blast.recipientCount} recipients · {countsLabel(blast)}
                    </strong>
                  </div>
                </div>
                <div className="blast-preview">
                  <div className="blast-preview-label">Instruction snapshot</div>
                  <p>{blast.instruction}</p>
                </div>
                <div className="blast-results-board">
                  <table>
                    <thead>
                      <tr>
                        <th>Visit</th>
                        <th>Mobile</th>
                        <th>Channel</th>
                        <th>Status</th>
                        <th>Provider</th>
                        <th>Error</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(blast.recipients || []).map((r, i) => (
                        <tr key={`${r.visitId || "row"}-${r.channel}-${i}`}>
                          <td>
                            <code>{r.visitId || "—"}</code>
                          </td>
                          <td>{r.mobileMasked}</td>
                          <td>{r.channel}</td>
                          <td>
                            <span className={recipientStatusClass(r.status)}>{r.status}</span>
                          </td>
                          <td className="tiny">{r.providerMessageId || "—"}</td>
                          <td className="tiny">{r.errorCode || "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            ) : (
              <p className="dim">No blast audit loaded.</p>
            )}
            <div className="modal-actions">
              <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={busy}>
                Close
              </button>
              {failed > 0 && (
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={busy}
                  onClick={onRetryFailed}
                >
                  {busy ? "Retrying…" : `Retry failed (${failed})`}
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
