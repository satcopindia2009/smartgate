import { useEffect, useRef, useState } from "react";
import { canImportStudents, useAuth } from "../auth/AuthContext";
import {
  ApiError,
  commitStudentImport,
  ImportEndpointMissingError,
  isNetworkError,
  validateStudentImport,
} from "../lib/api";
import { DEMO_SCHOOL_ID, FIRST_SCHOOL_NAME_EXAMPLE } from "../lib/constants";
import { schoolDisplayName } from "../lib/school";
import {
  applyValidRowsLocal,
  downloadImportTemplate,
  previewCsvText,
  templateSchoolCode,
  type ImportPreview,
} from "../lib/studentImport";
import type { Student, StudentImportError, StudentImportResult } from "../lib/types";
import { IconDownload, IconImport } from "./Icons";
import { useToast } from "./Toast";

type Stage = "upload" | "preview" | "done";

interface Props {
  open: boolean;
  students: Student[];
  schoolName?: string | null;
  onClose: () => void;
  onCommitted: () => void;
  onOpenCustody?: (studentExternalId: string) => void;
  onAudit: (who: string, when: string, filter: string) => void;
}

function auditWhen(): string {
  return (
    new Date().toLocaleString("en-IN", {
      day: "numeric",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true,
      timeZone: "Asia/Calcutta",
    }) + " IST"
  );
}

function resultSourceLabel(source?: string, usedLocal?: boolean) {
  if (usedLocal || source === "local") return "local dry-run";
  return "API";
}

export function StudentImportModal({
  open,
  students,
  schoolName,
  onClose,
  onCommitted,
  onOpenCustody,
  onAudit,
}: Props) {
  const { user, token, source } = useAuth();
  const { showToast } = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [stage, setStage] = useState<Stage>("upload");
  const [busy, setBusy] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [usedLocalValidate, setUsedLocalValidate] = useState(false);
  const [commitResult, setCommitResult] = useState<StudentImportResult | null>(null);
  const [commitLocal, setCommitLocal] = useState(false);

  const canImport = canImportStudents(user?.role);
  const tenantId = (user?.schoolId || "").trim();
  const tenantCode = (user?.schoolCode || "").trim();
  const tenantName = schoolDisplayName(user);
  const exampleCode = templateSchoolCode(tenantId, tenantCode);

  useEffect(() => {
    if (!open) return;
    setStage("upload");
    setPreview(null);
    setCommitResult(null);
    setUsedLocalValidate(false);
    setCommitLocal(false);
    setBusy(false);
    setDragOver(false);
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !busy) onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose, busy]);

  if (!open) return null;

  const who = user?.displayName || (user?.role === "security_head" ? "Security Head" : "Office Admin");

  async function runValidate(file: File) {
    if (!canImport) {
      showToast("Import is Admin / Security Head only", "error");
      return;
    }
    if (!tenantId) {
      showToast("No JWT schoolId — import is tenant-scoped", "error");
      return;
    }
    const name = file.name || "import.csv";
    if (/\.xlsx?$/i.test(name)) {
      showToast("Save as CSV UTF-8 (AC-IMP-8). Day-1 accepts CSV only.", "warning");
      return;
    }
    if (!/\.csv$/i.test(name) && file.type && !/csv|text\/plain/i.test(file.type)) {
      showToast("Upload a UTF-8 CSV file", "warning");
      return;
    }
    setBusy(true);
    try {
      const csvText = await file.text();
      const local = previewCsvText(csvText, tenantId, students, name, tenantCode);
      let next = local;
      let localValidate = true;
      if (token && !token.startsWith("fixture:") && source !== "fixtures") {
        try {
          const apiResult = await validateStudentImport(token, {
            filename: name,
            rows: local.rows,
            csvText,
          });
          const failedRows = new Set(apiResult.errors.map((e) => e.row));
          const validRows = local.validRows.filter((r) => !failedRows.has(r.rowNumber));
          next = {
            ...local,
            validRows,
            result: { ...apiResult, valid: apiResult.valid ?? validRows.length },
          };
          localValidate = false;
        } catch (err) {
          if (err instanceof ImportEndpointMissingError || isNetworkError(err)) {
            localValidate = true;
          } else if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
            showToast(err.message || "Import forbidden", "error");
            return;
          } else if (err instanceof ApiError) {
            next = {
              ...local,
              validRows: [],
              fileErrors: [{ code: err.code || "VALIDATION", message: err.message }],
              result: {
                imported: 0,
                updated: 0,
                failed: local.rows.length || 1,
                valid: 0,
                errors: [{ row: 0, field: null, code: err.code || "VALIDATION", message: err.message }],
                dryRun: true,
                source: "api",
                filename: name,
              },
            };
            localValidate = false;
            setPreview(next);
            setUsedLocalValidate(false);
            setStage("preview");
            showToast(err.message || "Validate failed", "error");
            return;
          } else {
            localValidate = true;
          }
        }
      }
      setPreview(next);
      setUsedLocalValidate(localValidate);
      setStage("preview");
      if (next.fileErrors.length) {
        showToast(next.fileErrors[0].message, "error");
      } else {
        showToast(
          `Validated ${next.rows.length} row${next.rows.length === 1 ? "" : "s"} · ${resultSourceLabel(next.result.source, localValidate)}`,
          "success",
        );
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Could not read CSV", "error");
    } finally {
      setBusy(false);
    }
  }

  async function onConfirm() {
    if (!preview || !canImport || !tenantId) return;
    if (!preview.validRows.length) {
      showToast("No valid rows to commit", "warning");
      return;
    }
    setBusy(true);
    try {
      let result: StudentImportResult | null = null;
      let usedLocal = true;
      if (token && !token.startsWith("fixture:") && source !== "fixtures") {
        try {
          result = await commitStudentImport(token, {
            filename: preview.filename,
            rows: preview.validRows,
            csvText: preview.csvText,
          });
          usedLocal = false;
        } catch (err) {
          if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
            showToast(err.message || "Import forbidden", "error");
            return;
          }
          if (!(err instanceof ImportEndpointMissingError || isNetworkError(err) || err instanceof ApiError)) {
            throw err;
          }
          usedLocal = true;
        }
      }
      if (!result) {
        result = applyValidRowsLocal(tenantId, preview.validRows, students);
        usedLocal = true;
      }
      setCommitResult(result);
      setCommitLocal(usedLocal);
      setStage("done");
      onAudit(
        who,
        auditWhen(),
        `${preview.filename} · ok=${result.imported + result.updated} fail=${result.failed} · ${usedLocal ? "local" : "api"}`,
      );
      onCommitted();
      showToast(
        usedLocal
          ? `Local apply · ${result.imported} created · ${result.updated} updated`
          : `Imported · ${result.imported} created · ${result.updated} updated`,
        "success",
      );
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Commit failed", "error");
    } finally {
      setBusy(false);
    }
  }

  const errors: StudentImportError[] = preview?.result.errors || [];
  const validCount = preview?.validRows.length ?? preview?.result.valid ?? 0;
  const failedCount = preview?.result.failed ?? 0;
  const f6Errors = errors.filter((e) => e.code === "F6_GATE_INSTRUCTION");

  return (
    <div
      className="modal-overlay open"
      role="dialog"
      aria-modal="true"
      aria-labelledby="import-title"
      onClick={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <div className="modal import-modal">
        <h3 id="import-title">Import CSV</h3>
        <p className="modal-sub">
          Admin / Security Head · UTF-8 · one row = one authorized person · no court PDF
        </p>

        <div className="import-tenant">
          <div>
            <strong>Signed-in tenant</strong>
            <span>{tenantName}</span>
            <span>{tenantId || "missing schoolId"}{tenantCode ? ` · school_code ${tenantCode}` : ""}</span>
            {schoolName && schoolName !== tenantName ? <span>{schoolName}</span> : null}
          </div>
          <div>
            <strong>Template example</strong>
            <span>
              {FIRST_SCHOOL_NAME_EXAMPLE} · school_code {exampleCode}
            </span>
            {tenantId === DEMO_SCHOOL_ID ? (
              <span className="import-warn">
                Demo seed is not the import default. Aarav / Kabir stay untouched unless this session is the demo
                tenant and the file keys match them.
              </span>
            ) : (
              <span>school_code may be blank (implied by JWT) or must match this tenant.</span>
            )}
          </div>
        </div>

        {stage === "upload" && (
          <>
            <div className="import-actions">
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => downloadImportTemplate(tenantId, tenantCode)}
              >
                <IconDownload />
                Download template
              </button>
            </div>
            <button
              type="button"
              className={`import-drop${dragOver ? " over" : ""}`}
              disabled={busy || !canImport}
              onClick={() => inputRef.current?.click()}
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                const file = e.dataTransfer.files?.[0];
                if (file) void runValidate(file);
              }}
            >
              <IconImport />
              <strong>{busy ? "Validating…" : "Upload CSV"}</strong>
              <span>UTF-8 · max 5,000 person-rows · validate preview before commit</span>
            </button>
            <input
              ref={inputRef}
              className="sr-only"
              type="file"
              accept=".csv,text/csv,text/plain"
              aria-label="Upload pickup CSV"
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = "";
                if (file) void runValidate(file);
              }}
            />
            <p className="import-note">
              Rejected: court_pdf, case_narrative, document_url, image bytes. court_order with a blank
              gate_instruction is a row error (F6). Host cannot import.
            </p>
          </>
        )}

        {stage === "preview" && preview && (
          <>
            <div className="import-counts" aria-live="polite">
              <div className="import-stat">
                <strong>{preview.rows.length}</strong>
                <span>rows</span>
              </div>
              <div className="import-stat ok">
                <strong>{validCount}</strong>
                <span>valid</span>
              </div>
              <div className="import-stat">
                <strong>{preview.result.imported}</strong>
                <span>will create</span>
              </div>
              <div className="import-stat">
                <strong>{preview.result.updated}</strong>
                <span>will update</span>
              </div>
              <div className={`import-stat${failedCount ? " bad" : ""}`}>
                <strong>{failedCount}</strong>
                <span>row errors</span>
              </div>
            </div>
            <p className="import-meta">
              {preview.filename}
              {" · "}
              {resultSourceLabel(preview.result.source, usedLocalValidate)}
              {usedLocalValidate ? " (API validate not present)" : ""}
              {" · commit valid rows only"}
            </p>
            {errors.length > 0 && (
              <div className="import-errors table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>Field</th>
                      <th>Code</th>
                      <th>Message</th>
                    </tr>
                  </thead>
                  <tbody>
                    {errors.slice(0, 80).map((e, i) => (
                      <tr key={`${e.row}-${e.code}-${i}`}>
                        <td>{e.row || "—"}</td>
                        <td>{e.field || "—"}</td>
                        <td>{e.code}</td>
                        <td>{e.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {f6Errors.length > 0 && (
              <p className="import-f6">
                F6 rows need a gate instruction. Fix in the custody editor after a valid import, or correct the CSV.
                {onOpenCustody ? (
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => {
                      const f6 = f6Errors[0];
                      const ext =
                        f6.studentExternalId ||
                        preview.rows.find((r) => r.student_external_id)?.student_external_id;
                      if (ext) onOpenCustody(ext);
                    }}
                  >
                    Open custody editor
                  </button>
                ) : null}
              </p>
            )}
            <div className="modal-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setStage("upload")} disabled={busy}>
                Back
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => void onConfirm()}
                disabled={busy || !validCount}
              >
                {busy ? "Committing…" : `Confirm commit · ${validCount} valid`}
              </button>
            </div>
          </>
        )}

        {stage === "done" && commitResult && (
          <>
            <div className="import-counts" aria-live="polite">
              <div className="import-stat ok">
                <strong>{commitResult.imported}</strong>
                <span>created</span>
              </div>
              <div className="import-stat">
                <strong>{commitResult.updated}</strong>
                <span>updated</span>
              </div>
              <div className={`import-stat${commitResult.failed ? " bad" : ""}`}>
                <strong>{commitResult.failed}</strong>
                <span>failed</span>
              </div>
            </div>
            <p className="import-meta">
              {preview?.filename} · {commitLocal ? "local apply (import API not present)" : "API commit"} ·
              demo seed isolated from other tenants
            </p>
            {commitResult.errors.length > 0 && (
              <div className="import-errors table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>Field</th>
                      <th>Code</th>
                      <th>Message</th>
                    </tr>
                  </thead>
                  <tbody>
                    {commitResult.errors.slice(0, 40).map((e, i) => (
                      <tr key={`${e.row}-${e.code}-${i}`}>
                        <td>{e.row || "—"}</td>
                        <td>{e.field || "—"}</td>
                        <td>{e.code}</td>
                        <td>{e.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="modal-actions">
              <button type="button" className="btn btn-primary" onClick={onClose}>
                Done
              </button>
            </div>
          </>
        )}

        {stage === "upload" && (
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={onClose} disabled={busy}>
              Cancel
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export function ImportCsvButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  return (
    <button type="button" className="btn btn-primary btn-sm" onClick={onClick} disabled={disabled}>
      <IconImport />
      Import CSV
    </button>
  );
}
