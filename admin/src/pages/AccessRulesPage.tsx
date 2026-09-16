import { FormEvent, useCallback, useEffect, useState } from "react";
import { canEditCampusHours, useAuth } from "../auth/AuthContext";
import { useToast } from "../components/Toast";
import {
  ApiError,
  createHoliday,
  deleteHoliday,
  getCampusHours,
  isNetworkError,
  listHolidays,
  putCampusHours,
} from "../lib/api";
import {
  addHolidayLocal,
  formatHolidayDate,
  getAfterHoursFixtureSession,
  hoursPayload,
  overnightNeedsConfirm,
  removeHolidayLocal,
  saveHoursLocal,
  seedCampusHours,
  seedHolidays,
  WEEKDAY_LABELS,
  WEEKDAYS,
} from "../lib/afterHours";
import { CAMPUS_TZ } from "../lib/constants";
import { loadFixtures } from "../lib/fixtures";
import type { CampusHoursRow, HolidayEntry, Weekday } from "../lib/types";

function emptyHours(): CampusHoursRow[] {
  return WEEKDAYS.map((weekday) => ({
    weekday,
    timezone: CAMPUS_TZ,
    openTime: weekday === "sun" ? null : "08:00",
    closeTime: weekday === "sun" ? null : weekday === "sat" ? "13:00" : "18:00",
    closed: weekday === "sun",
    overnight: false,
  }));
}

function mergeHours(rows: CampusHoursRow[]): CampusHoursRow[] {
  const byDay = new Map(rows.map((r) => [r.weekday, r]));
  return WEEKDAYS.map((weekday) => {
    const found = byDay.get(weekday);
    return found ? { ...found, weekday } : emptyHours().find((r) => r.weekday === weekday)!;
  });
}

export function AccessRulesPage() {
  const { token, user, source } = useAuth();
  const { showToast } = useToast();
  const canWrite = canEditCampusHours(user?.role);
  const [hours, setHours] = useState<CampusHoursRow[]>(emptyHours());
  const [holidays, setHolidays] = useState<HolidayEntry[]>([]);
  const [usingFixtures, setUsingFixtures] = useState(source === "fixtures");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [date, setDate] = useState("");
  const [label, setLabel] = useState("");

  const useLocal = usingFixtures || source === "fixtures" || !token || Boolean(token?.startsWith("fixture:"));

  const load = useCallback(async () => {
    const fx = await loadFixtures();
    if (source === "fixtures" || !token || token.startsWith("fixture:")) {
      const sess = getAfterHoursFixtureSession(fx.campusHours, fx.holidays);
      setHours(mergeHours(sess.hours));
      setHolidays([...sess.holidays].sort((a, b) => a.date.localeCompare(b.date)));
      setUsingFixtures(true);
      setLoading(false);
      return;
    }
    try {
      const [h, hol] = await Promise.all([getCampusHours(token), listHolidays(token)]);
      setHours(mergeHours(h.data.length ? h.data : seedCampusHours()));
      setHolidays([...(hol.data || [])].sort((a, b) => a.date.localeCompare(b.date)));
      setUsingFixtures(false);
    } catch (err) {
      const sess = getAfterHoursFixtureSession(fx.campusHours || seedCampusHours(), fx.holidays || seedHolidays());
      setHours(mergeHours(sess.hours));
      setHolidays([...sess.holidays].sort((a, b) => a.date.localeCompare(b.date)));
      setUsingFixtures(true);
      if (!isNetworkError(err) && err instanceof Error) {
        showToast(err.message, "warning");
      }
    } finally {
      setLoading(false);
    }
  }, [source, token, showToast]);

  useEffect(() => {
    void load();
  }, [load]);

  function patchDay(weekday: Weekday, patch: Partial<CampusHoursRow>) {
    setHours((prev) => prev.map((row) => (row.weekday === weekday ? { ...row, ...patch } : row)));
  }

  async function saveHours() {
    if (!canWrite) {
      showToast("Hours editor is Admin or Security Head only — Gate cannot write", "warning");
      return;
    }
    const overnight = hours.find(overnightNeedsConfirm);
    if (overnight) {
      showToast(
        `${WEEKDAY_LABELS[overnight.weekday]} close is before open — confirm Overnight or fix times`,
        "warning",
      );
      return;
    }
    setSaving(true);
    const payload = hoursPayload(hours);
    try {
      if (!useLocal && token) {
        const res = await putCampusHours(token, payload);
        setHours(mergeHours(res.data.length ? res.data : payload));
        setUsingFixtures(false);
      } else {
        setHours(mergeHours(saveHoursLocal(payload)));
      }
      showToast("Campus hours saved · no eng deploy", "success");
    } catch (err) {
      setHours(mergeHours(saveHoursLocal(payload)));
      setUsingFixtures(true);
      showToast(
        err instanceof ApiError ? `Saved in fixtures fallback: ${err.message}` : "Hours saved in fixtures fallback",
        "warning",
      );
    } finally {
      setSaving(false);
    }
  }

  async function onAddHoliday(e: FormEvent) {
    e.preventDefault();
    if (!canWrite) {
      showToast("Holiday calendar is Admin or Security Head only", "warning");
      return;
    }
    if (!date) {
      showToast("Pick a holiday date", "warning");
      return;
    }
    const body = { date, label: label.trim() || null };
    try {
      if (!useLocal && token) {
        const created = await createHoliday(token, body);
        setHolidays((prev) => [...prev, created].sort((a, b) => a.date.localeCompare(b.date)));
      } else {
        const created = addHolidayLocal(body.date, body.label);
        setHolidays((prev) => [...prev, created].sort((a, b) => a.date.localeCompare(b.date)));
      }
      showToast(`${formatHolidayDate(date)} added`, "success");
      setAddOpen(false);
      setDate("");
      setLabel("");
    } catch (err) {
      const created = addHolidayLocal(body.date, body.label);
      setHolidays((prev) => {
        if (prev.some((h) => h.id === created.id || h.date === created.date)) return prev;
        return [...prev, created].sort((a, b) => a.date.localeCompare(b.date));
      });
      setUsingFixtures(true);
      showToast(
        err instanceof ApiError ? `Saved in fixtures fallback: ${err.message}` : "Holiday saved in fixtures fallback",
        "warning",
      );
      setAddOpen(false);
      setDate("");
      setLabel("");
    }
  }

  async function onRemoveHoliday(id: string) {
    if (!canWrite) {
      showToast("Holiday calendar is Admin or Security Head only", "warning");
      return;
    }
    try {
      if (!useLocal && token) {
        await deleteHoliday(token, id);
      } else {
        removeHolidayLocal(id);
      }
      setHolidays((prev) => prev.filter((h) => h.id !== id));
      showToast("Holiday removed", "success");
    } catch (err) {
      removeHolidayLocal(id);
      setHolidays((prev) => prev.filter((h) => h.id !== id));
      setUsingFixtures(true);
      showToast(
        err instanceof ApiError ? `Removed in fixtures fallback: ${err.message}` : "Holiday removed in fixtures fallback",
        "warning",
      );
    }
  }

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Campus hours + holidays</h1>
          <p>
            Per-campus · {CAMPUS_TZ} · Admin / Security Head write · Gate cannot
            {usingFixtures && <span className="source-inline"> · fixtures fallback</span>}
          </p>
        </div>
        {canWrite && (
          <button type="button" className="btn btn-primary" disabled={saving || loading} onClick={() => void saveHours()}>
            {saving ? "Saving…" : "Save hours"}
          </button>
        )}
      </div>

      {!canWrite && (
        <div className="oa-lock show">Hours and holidays are read-only for this role. Gate cannot edit.</div>
      )}

      <div className="hours-grid">
        <div className="detail-card">
          <h2 className="section-title" style={{ marginTop: 0 }}>
            Weekday hours
          </h2>
          <p className="form-hint">Close exclusive · exact 18:00 = after_hours. Sticky eval at registration (AC-C4e).</p>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Day</th>
                  <th>Open</th>
                  <th>Close</th>
                  <th>Closed</th>
                  <th>Overnight</th>
                </tr>
              </thead>
              <tbody>
                {hours.map((row) => (
                  <tr key={row.weekday}>
                    <td>{WEEKDAY_LABELS[row.weekday]}</td>
                    <td>
                      <input
                        type="time"
                        aria-label={`${WEEKDAY_LABELS[row.weekday]} open`}
                        value={row.openTime || ""}
                        disabled={!canWrite || row.closed}
                        onChange={(e) => patchDay(row.weekday, { openTime: e.target.value || null })}
                      />
                    </td>
                    <td>
                      <input
                        type="time"
                        aria-label={`${WEEKDAY_LABELS[row.weekday]} close`}
                        value={row.closeTime || ""}
                        disabled={!canWrite || row.closed}
                        onChange={(e) => patchDay(row.weekday, { closeTime: e.target.value || null })}
                      />
                    </td>
                    <td>
                      <label className="field-check" style={{ margin: 0 }}>
                        <input
                          type="checkbox"
                          checked={row.closed}
                          disabled={!canWrite}
                          onChange={(e) =>
                            patchDay(row.weekday, {
                              closed: e.target.checked,
                              openTime: e.target.checked ? null : row.openTime || "08:00",
                              closeTime: e.target.checked ? null : row.closeTime || "18:00",
                            })
                          }
                        />
                        Closed
                      </label>
                    </td>
                    <td>
                      <label className="field-check" style={{ margin: 0 }}>
                        <input
                          type="checkbox"
                          checked={Boolean(row.overnight)}
                          disabled={!canWrite || row.closed}
                          onChange={(e) => patchDay(row.weekday, { overnight: e.target.checked })}
                        />
                        Overnight
                      </label>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="detail-card">
          <div className="detail-head">
            <h2 className="section-title" style={{ marginTop: 0 }}>
              Holiday calendar
            </h2>
            {canWrite && (
              <button type="button" className="btn btn-primary btn-sm" onClick={() => setAddOpen(true)}>
                + Add holiday
              </button>
            )}
          </div>
          <p className="form-hint">CSV import = Later · not P2. Seed: Diwali 20 Oct 2026 · HOL-DIWALI.</p>
          {holidays.length === 0 ? (
            <div className="pickup-empty">No holidays on the calendar</div>
          ) : (
            <div className="holiday-list">
              {holidays.map((h) => (
                <div className="holiday-item" key={h.id}>
                  <div>
                    <strong>{formatHolidayDate(h.date)}</strong>
                    <div className="subline">
                      {h.label || "School closed"}
                      {h.id === "HOL-DIWALI" ? " · HOL-DIWALI" : ""}
                    </div>
                  </div>
                  {canWrite && (
                    <button type="button" className="btn btn-ghost btn-sm" onClick={() => void onRemoveHoliday(h.id)}>
                      Remove
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="policy-lock">
        After-hours / holiday Approve policy: <strong>Security Head only</strong> (not dual host+SH) · Priority-P2 lock
        A4. Host Approve is a no-op. Evaluation is sticky at registration.
      </div>

      {addOpen && (
        <div
          className="modal-overlay open"
          role="dialog"
          aria-modal="true"
          aria-labelledby="hol-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setAddOpen(false);
          }}
        >
          <div className="modal modal-form">
            <h3 id="hol-title">Add holiday</h3>
            <p className="modal-sub">Closed date · optional label · Asia/Kolkata</p>
            <form onSubmit={(e) => void onAddHoliday(e)}>
              <div className="field">
                <label htmlFor="hol-date">
                  Date <span className="req">*</span>
                </label>
                <input id="hol-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
              </div>
              <div className="field">
                <label htmlFor="hol-label">Label</label>
                <input
                  id="hol-label"
                  value={label}
                  onChange={(e) => setLabel(e.target.value)}
                  placeholder="e.g. Diwali"
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setAddOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Add holiday
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  );
}
