import { FormEvent, useCallback, useEffect, useState } from "react";
import { canEditCampusHours, useAuth } from "../auth/AuthContext";
import { useToast } from "../components/Toast";
import {
  ApiError,
  createHoliday,
  deleteHoliday,
  getCampusHours,
  getEscortRules,
  isNetworkError,
  listHolidays,
  listZones,
  patchZone,
  putCampusHours,
  putEscortRules,
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
import { CAMPUS_TZ, VISITOR_TYPES } from "../lib/constants";
import {
  getEscortFixtureSession,
  saveRulesLocal,
  saveZonesLocal,
  seedEscortRules,
  seedZones,
  ZONE_KEYS,
} from "../lib/escort";
import { loadFixtures } from "../lib/fixtures";
import type { CampusHoursRow, EscortZoneRule, HolidayEntry, Weekday, ZoneLabel } from "../lib/types";

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
  const [zones, setZones] = useState<ZoneLabel[]>(seedZones());
  const [rules, setRules] = useState<EscortZoneRule[]>(seedEscortRules());
  const [savingEscort, setSavingEscort] = useState(false);

  const useLocal = usingFixtures || source === "fixtures" || !token || Boolean(token?.startsWith("fixture:"));

  const load = useCallback(async () => {
    const fx = await loadFixtures();
    if (source === "fixtures" || !token || token.startsWith("fixture:")) {
      const sess = getAfterHoursFixtureSession(fx.campusHours, fx.holidays);
      const escort = getEscortFixtureSession(fx.zones, fx.escortRules);
      setHours(mergeHours(sess.hours));
      setHolidays([...sess.holidays].sort((a, b) => a.date.localeCompare(b.date)));
      setZones(escort.zones);
      setRules(escort.rules);
      setUsingFixtures(true);
      setLoading(false);
      return;
    }
    try {
      const [h, hol, z, er] = await Promise.all([
        getCampusHours(token),
        listHolidays(token),
        listZones(token),
        getEscortRules(token),
      ]);
      setHours(mergeHours(h.data.length ? h.data : seedCampusHours()));
      setHolidays([...(hol.data || [])].sort((a, b) => a.date.localeCompare(b.date)));
      setZones(z.data.length ? z.data : seedZones());
      setRules(er.data.length ? er.data : seedEscortRules());
      setUsingFixtures(false);
    } catch (err) {
      const sess = getAfterHoursFixtureSession(fx.campusHours || seedCampusHours(), fx.holidays || seedHolidays());
      const escort = getEscortFixtureSession(fx.zones || seedZones(), fx.escortRules || seedEscortRules());
      setHours(mergeHours(sess.hours));
      setHolidays([...sess.holidays].sort((a, b) => a.date.localeCompare(b.date)));
      setZones(escort.zones);
      setRules(escort.rules);
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

  async function saveZones() {
    if (!canWrite) {
      showToast("Zone labels are Admin or Security Head only", "warning");
      return;
    }
    setSavingEscort(true);
    try {
      if (!useLocal && token) {
        const updated = await Promise.all(zones.map((z) => patchZone(token, z.key, z.label.trim() || z.key)));
        setZones(updated);
        setUsingFixtures(false);
      } else {
        setZones(saveZonesLocal(zones));
      }
      showToast("Zone labels saved", "success");
    } catch (err) {
      setZones(saveZonesLocal(zones));
      setUsingFixtures(true);
      showToast(
        err instanceof ApiError ? `Saved in fixtures fallback: ${err.message}` : "Zone labels saved in fixtures fallback",
        "warning",
      );
    } finally {
      setSavingEscort(false);
    }
  }

  async function saveRules() {
    if (!canWrite) {
      showToast("Escort rules are Admin or Security Head only", "warning");
      return;
    }
    const payload = VISITOR_TYPES.map((vt) => {
      const row = rules.find((r) => r.visitorType === vt) || {
        visitorType: vt,
        escortRequired: vt === "Vendor",
        allowedZones: ["reception"],
      };
      return {
        ...row,
        escortRequired: row.allowedZones.includes("restricted") ? true : row.escortRequired,
      };
    });
    setSavingEscort(true);
    try {
      if (!useLocal && token) {
        const res = await putEscortRules(token, payload);
        setRules(res.data.length ? res.data : payload);
        setUsingFixtures(false);
      } else {
        setRules(saveRulesLocal(payload));
      }
      showToast("Escort rules saved", "success");
    } catch (err) {
      setRules(saveRulesLocal(payload));
      setUsingFixtures(true);
      showToast(
        err instanceof ApiError ? `Saved in fixtures fallback: ${err.message}` : "Escort rules saved in fixtures fallback",
        "warning",
      );
    } finally {
      setSavingEscort(false);
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
          <h1>Access rules</h1>
          <p>
            Hours + holidays · escort / zones · {CAMPUS_TZ} · Admin / SH write · Gate cannot
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

      <div className="hours-grid" style={{ marginTop: 16 }}>
        <div className="detail-card">
          <div className="detail-head">
            <h2 className="section-title" style={{ marginTop: 0 }}>
              Zone labels
            </h2>
            {canWrite && (
              <button type="button" className="btn btn-primary btn-sm" disabled={savingEscort} onClick={() => void saveZones()}>
                Save labels
              </button>
            )}
          </div>
          <p className="form-hint">Keys fixed · school-renamable only · adding keys = Later</p>
          <div className="holiday-list">
            {zones.map((z) => (
              <div className="holiday-item" key={z.key}>
                <div>
                  <code className="tiny">{z.key}</code>
                  <div className="subline">system key</div>
                </div>
                <input
                  type="text"
                  aria-label={`Label for ${z.key}`}
                  value={z.label}
                  disabled={!canWrite}
                  onChange={(e) =>
                    setZones((prev) => prev.map((row) => (row.key === z.key ? { ...row, label: e.target.value } : row)))
                  }
                />
              </div>
            ))}
          </div>
        </div>

        <div className="detail-card">
          <div className="detail-head">
            <h2 className="section-title" style={{ marginTop: 0 }}>
              Escort rules
            </h2>
            {canWrite && (
              <button type="button" className="btn btn-primary btn-sm" disabled={savingEscort} onClick={() => void saveRules()}>
                Save rules
              </button>
            )}
          </div>
          <p className="form-hint">Vendor default ON · restricted zone forces escort. Gate cannot edit.</p>
          {VISITOR_TYPES.map((vt) => {
            const rule = rules.find((r) => r.visitorType === vt) || {
              visitorType: vt,
              escortRequired: vt === "Vendor",
              allowedZones: ["reception"],
            };
            return (
              <div className="escort-rule" key={vt}>
                <div className="escort-rule-head">
                  <strong>{vt}</strong>
                  <label className="field-check" style={{ margin: 0 }}>
                    <input
                      type="checkbox"
                      checked={rule.escortRequired || rule.allowedZones.includes("restricted")}
                      disabled={!canWrite}
                      onChange={(e) =>
                        setRules((prev) => {
                          const next = prev.some((r) => r.visitorType === vt)
                            ? prev.map((r) => (r.visitorType === vt ? { ...r, escortRequired: e.target.checked } : r))
                            : [...prev, { ...rule, escortRequired: e.target.checked }];
                          return next;
                        })
                      }
                    />
                    Escort required
                  </label>
                </div>
                <div className="zone-picks">
                  {ZONE_KEYS.map((key) => {
                    const on = rule.allowedZones.includes(key);
                    return (
                      <button
                        key={key}
                        type="button"
                        className={`chip${on ? " active" : ""}`}
                        disabled={!canWrite}
                        onClick={() =>
                          setRules((prev) => {
                            const current = prev.find((r) => r.visitorType === vt) || rule;
                            const allowed = on
                              ? current.allowedZones.filter((z) => z !== key)
                              : [...current.allowedZones, key];
                            const escortRequired = allowed.includes("restricted") ? true : current.escortRequired;
                            const row = { ...current, allowedZones: allowed, escortRequired };
                            if (prev.some((r) => r.visitorType === vt)) {
                              return prev.map((r) => (r.visitorType === vt ? row : r));
                            }
                            return [...prev, row];
                          })
                        }
                      >
                        {zones.find((z) => z.key === key)?.label || key}
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
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
