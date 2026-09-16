import { FormEvent, useCallback, useEffect, useState } from "react";
import { canWriteBlacklist, useAuth } from "../auth/AuthContext";
import { useAudit } from "../components/AuditContext";
import { ExportButton, ExportModal } from "../components/ExportModal";
import { useToast } from "../components/Toast";
import { createBlacklist, isNetworkError, listBlacklist, patchBlacklist } from "../lib/api";
import { ID_TYPES } from "../lib/constants";
import { getFixtureSession, loadFixtures, upsertBlacklistLocal } from "../lib/fixtures";
import { avatarClass, formatDateTime, formatMobile, initials, maskGovtId } from "../lib/format";
import type { BlacklistEntry, Severity } from "../lib/types";

const emptyForm = {
  name: "",
  mobile: "",
  idType: "",
  idNumber: "",
  reason: "",
  severity: "Block" as Severity,
  expiresOn: "",
  notes: "",
  active: true,
};

export function BlacklistPage() {
  const { token, user, source } = useAuth();
  const { showToast } = useToast();
  const { pushAudit } = useAudit();
  const [exportOpen, setExportOpen] = useState(false);
  const canWrite = canWriteBlacklist(user?.role);
  const [rows, setRows] = useState<BlacklistEntry[]>([]);
  const [usingFixtures, setUsingFixtures] = useState(source === "fixtures");
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  const load = useCallback(async () => {
    await loadFixtures();
    if (source === "fixtures" || !token || token.startsWith("fixture:")) {
      const sess = await getFixtureSession();
      setRows(sess.blacklist);
      setUsingFixtures(true);
      return;
    }
    try {
      const res = await listBlacklist(token);
      setRows(res.data);
      setUsingFixtures(false);
    } catch {
      const sess = await getFixtureSession();
      setRows(sess.blacklist);
      setUsingFixtures(true);
    }
  }, [source, token]);

  useEffect(() => {
    void load();
  }, [load]);

  function openAdd() {
    if (!canWrite) {
      showToast("Blacklist write is Security Head only", "warning");
      return;
    }
    setEditId(null);
    setForm(emptyForm);
    setOpen(true);
  }

  function openEdit(row: BlacklistEntry) {
    if (!canWrite) {
      showToast("Blacklist write is Security Head only", "warning");
      return;
    }
    setEditId(row.id);
    setForm({
      name: row.name,
      mobile: row.mobile || "",
      idType: row.idType || "",
      idNumber: row.idNumber || "",
      reason: row.reason,
      severity: (row.severity as Severity) || "Block",
      expiresOn: row.expiresOn || "",
      notes: row.notes || "",
      active: row.active,
    });
    setOpen(true);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canWrite) return;
    const name = form.name.trim();
    const reason = form.reason.trim();
    const mobile = form.mobile.replace(/\D/g, "").slice(-10);
    const idType = form.idType;
    const idNumber = form.idNumber.replace(/\s+/g, "");
    if (!name || !reason) {
      showToast("Full name and reason are required", "warning");
      return;
    }
    if (!mobile && !(idType && idNumber)) {
      showToast("Enter a mobile or govt ID type and number", "warning");
      return;
    }
    const body = {
      name,
      mobile: mobile || null,
      idType: idType || null,
      idNumber: idNumber || null,
      reason,
      severity: form.severity,
      expiresOn: form.expiresOn || null,
      notes: form.notes.trim() || null,
      active: form.active,
    };
    try {
      if (!usingFixtures && token && !token.startsWith("fixture:")) {
        if (editId) await patchBlacklist(token, editId, body);
        else await createBlacklist(token, { ...body, name, reason, severity: form.severity });
        showToast(editId ? "Blacklist entry updated" : "Blacklist entry added", "success");
        setOpen(false);
        await load();
        return;
      }
    } catch (err) {
      if (!isNetworkError(err) && !(err instanceof Error)) {
        showToast(err instanceof Error ? err.message : "Save failed", "error");
        return;
      }
    }
    const sess = await getFixtureSession();
    const existing = editId ? sess.blacklist.find((b) => b.id === editId) : null;
    const entry: BlacklistEntry = {
      id: existing?.id || `BL-${String(sess.blacklist.length + 1).padStart(2, "0")}`,
      name,
      mobile: mobile || null,
      idType: idType || null,
      idNumber: idNumber || null,
      reason,
      severity: form.severity,
      active: form.active,
      addedBy: existing?.addedBy || user?.displayName || "Security Head",
      addedAt: existing?.addedAt || new Date().toISOString(),
      expiresOn: form.expiresOn || null,
      notes: form.notes.trim() || null,
    };
    upsertBlacklistLocal(entry, !existing);
    setRows((await getFixtureSession()).blacklist);
    showToast(existing ? "Blacklist entry updated" : `Blacklist entry added · ${entry.id}`, "success");
    setOpen(false);
  }

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Blacklist</h1>
          <p>
            Hard match mobile or govt ID · name-only never auto-blocks
            {usingFixtures && <span className="source-inline"> · fixtures fallback</span>}
          </p>
        </div>
        <div className="bl-top-actions">
          {!canWrite && (
            <span className="bl-role-hint">View only — blacklist write is Security Head only</span>
          )}
          {canWrite && (
            <button type="button" className="btn btn-primary" onClick={openAdd}>
              + Add entry
            </button>
          )}
          <ExportButton onClick={() => setExportOpen(true)} />
        </div>
      </div>
      <div className="board">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Mobile</th>
              <th>ID</th>
              <th>Severity</th>
              <th>Reason</th>
              <th>Active</th>
              <th>Added</th>
              <th>Expires</th>
              <th>Notes</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={10}>No blacklist entries</td>
              </tr>
            ) : (
              rows.map((b) => (
                <tr key={b.id} className={b.active ? "" : "bl-inactive"}>
                  <td>
                    <div className="visitor-cell">
                      <div className={`avatar avatar-md ${avatarClass("Guest")}`}>{initials(b.name)}</div>
                      <strong>{b.name}</strong>
                    </div>
                  </td>
                  <td>{formatMobile(b.mobile)}</td>
                  <td>
                    {b.idType && b.idNumber ? (
                      <>
                        {b.idType} · <code className="tiny">{maskGovtId(b.idNumber)}</code>
                      </>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td>
                    <span className={`tag ${b.severity === "Block" ? "sev-block" : "sev-alert"}`}>
                      {b.severity}
                    </span>
                  </td>
                  <td>
                    <div
                      className={`purpose-cell${expanded[b.id] ? " expanded" : ""}`}
                      onClick={() => setExpanded((p) => ({ ...p, [b.id]: !p[b.id] }))}
                    >
                      {b.reason}
                    </div>
                  </td>
                  <td>
                    {b.active ? <span className="yes">Yes</span> : <span className="dim">No</span>}
                  </td>
                  <td>
                    {b.addedBy}
                    <div className="subline">{formatDateTime(b.addedAt)}</div>
                  </td>
                  <td>{b.expiresOn || "—"}</td>
                  <td>
                    <div className="notes-cell">{b.notes || "—"}</div>
                  </td>
                  <td>
                    {canWrite ? (
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => openEdit(b)}>
                        Edit
                      </button>
                    ) : (
                      <span className="tiny dim">Security Head only</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {open && (
        <div
          className="modal-overlay open"
          role="dialog"
          aria-modal="true"
          onClick={(e) => {
            if (e.target === e.currentTarget) setOpen(false);
          }}
        >
          <div className="modal bl-modal">
            <h3>{editId ? "Edit blacklist entry" : "Add blacklist entry"}</h3>
            <p className="modal-sub">Security Head write · mobile or govt ID required</p>
            <form onSubmit={onSubmit}>
              <div className="field">
                <label>
                  Full name <span className="req">*</span>
                </label>
                <input
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  required
                />
              </div>
              <div className="field-row">
                <div className="field">
                  <label>Mobile</label>
                  <input
                    value={form.mobile}
                    onChange={(e) => setForm({ ...form, mobile: e.target.value })}
                    placeholder="10-digit IN mobile"
                  />
                </div>
                <div className="field">
                  <label>
                    Severity <span className="req">*</span>
                  </label>
                  <select
                    value={form.severity}
                    onChange={(e) => setForm({ ...form, severity: e.target.value as Severity })}
                  >
                    <option>Block</option>
                    <option>Alert</option>
                  </select>
                </div>
              </div>
              <div className="field-row">
                <div className="field">
                  <label>Govt ID type</label>
                  <select
                    value={form.idType}
                    onChange={(e) => setForm({ ...form, idType: e.target.value })}
                  >
                    <option value="">—</option>
                    {ID_TYPES.map((t) => (
                      <option key={t}>{t}</option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label>Govt ID number</label>
                  <input
                    value={form.idNumber}
                    onChange={(e) => setForm({ ...form, idNumber: e.target.value })}
                  />
                </div>
              </div>
              <p className="form-hint">Mobile or govt ID type+number required (at least one).</p>
              <div className="field">
                <label>
                  Reason <span className="req">*</span>
                </label>
                <textarea
                  value={form.reason}
                  onChange={(e) => setForm({ ...form, reason: e.target.value })}
                  required
                />
              </div>
              <div className="field-row">
                <div className="field">
                  <label>Expires on</label>
                  <input
                    type="date"
                    value={form.expiresOn}
                    onChange={(e) => setForm({ ...form, expiresOn: e.target.value })}
                  />
                </div>
                <label className="field-check">
                  <input
                    type="checkbox"
                    checked={form.active}
                    onChange={(e) => setForm({ ...form, active: e.target.checked })}
                  />
                  Active
                </label>
              </div>
              <div className="field">
                <label>Notes</label>
                <textarea
                  value={form.notes}
                  onChange={(e) => setForm({ ...form, notes: e.target.value })}
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  {editId ? "Save changes" : "Add entry"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      <ExportModal
        open={exportOpen}
        initialScope="blacklist"
        onClose={() => setExportOpen(false)}
        onAudit={pushAudit}
        bundles={{
          blacklist: {
            headers: [
              "id",
              "name",
              "mobile",
              "idType",
              "idNumber",
              "reason",
              "severity",
              "active",
              "addedBy",
              "addedAt",
              "expiresOn",
              "notes",
              "demo_watermark",
            ],
            rows: rows.map((b) => [
              b.id,
              b.name,
              b.mobile,
              b.idType,
              maskGovtId(b.idNumber),
              b.reason,
              b.severity,
              b.active ? "Y" : "N",
              b.addedBy,
              b.addedAt,
              b.expiresOn,
              b.notes,
              "DEMO",
            ]),
            filter: "all blacklist entries",
          },
        }}
      />
    </section>
  );
}
