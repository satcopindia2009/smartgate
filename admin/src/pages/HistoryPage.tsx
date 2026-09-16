import { useCallback, useEffect, useMemo, useState } from "react";
import { canApproveAfterHours, useAuth } from "../auth/AuthContext";
import { AfterHoursDecisionModal } from "../components/AfterHoursDecisionModal";
import { useAudit } from "../components/AuditContext";
import { ExportButton, ExportModal } from "../components/ExportModal";
import { IconSearch } from "../components/Icons";
import { useToast } from "../components/Toast";
import { ApiError, approveVisitApi, isNetworkError, listGates, listStaff, listVisits, rejectVisitApi } from "../lib/api";
import { matchesAfterHoursFlag, policyTriggerLabel } from "../lib/afterHours";
import { GATE_ENUMS, VISITOR_TYPES } from "../lib/constants";
import { applyAfterHoursDecisionLocal, fixtureGates, fixtureStaff, getFixtureSession, loadFixtures } from "../lib/fixtures";
import { avatarClass, formatDateTime, formatDurationMin, formatMobile, initials, todayIso, typeClass } from "../lib/format";
import { toHistoryVisit } from "../lib/mapVisit";
import type { Gate, HistoryVisit, Staff } from "../lib/types";

export function HistoryPage() {
  const { token, user, source } = useAuth();
  const { pushAudit } = useAudit();
  const { showToast } = useToast();
  const canDecideAh = canApproveAfterHours(user?.role);
  const [rows, setRows] = useState<HistoryVisit[]>([]);
  const [gates, setGates] = useState<Gate[]>([]);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [hostId, setHostId] = useState("");
  const [exportOpen, setExportOpen] = useState(false);
  const [q, setQ] = useState("");
  const [gate, setGate] = useState("");
  const [type, setType] = useState("");
  const [decision, setDecision] = useState("");
  const [checkout, setCheckout] = useState("");
  const [bl, setBl] = useState("");
  const [ahFlag, setAhFlag] = useState("");
  const [dateFrom, setDateFrom] = useState(todayIso());
  const [dateTo, setDateTo] = useState(todayIso());
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [noteOpen, setNoteOpen] = useState<Record<string, boolean>>({});
  const [usingFixtures, setUsingFixtures] = useState(source === "fixtures");
  const [decideTarget, setDecideTarget] = useState<HistoryVisit | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const fx = await loadFixtures();
    const gLocal = fixtureGates();
    const span =
      (new Date(dateTo).getTime() - new Date(dateFrom).getTime()) / 86400000;
    if (span > 90) {
      showToast("Date range max 90 days", "warning");
      return;
    }
    if (source === "fixtures" || !token || token.startsWith("fixture:")) {
      const sess = await getFixtureSession();
      setGates(gLocal);
      setStaff(fixtureStaff(fx));
      setRows(sess.history);
      setUsingFixtures(true);
      return;
    }
    try {
      const [g, s, visits] = await Promise.all([
        listGates(token),
        listStaff(token),
        listVisits(token, {
          dateFrom,
          dateTo,
          afterHours: ahFlag === "afterhours" || ahFlag === "pending_sh" || ahFlag === "holiday" ? true : undefined,
          policyTrigger: ahFlag === "holiday" ? "holiday" : undefined,
          status: ahFlag === "pending_sh" ? "pending" : undefined,
        }),
      ]);
      setGates(g.data);
      setStaff(s.data);
      setRows(visits.data.map((v) => toHistoryVisit(v, g.data, s.data as Staff[])));
      setUsingFixtures(false);
    } catch (err) {
      const sess = await getFixtureSession();
      setGates(fixtureGates());
      setRows(sess.history);
      setUsingFixtures(true);
      if (!isNetworkError(err)) {
        /* fallback anyway */
      }
    }
  }, [source, token, dateFrom, dateTo, ahFlag, showToast]);

  useEffect(() => {
    void load();
  }, [load]);

  async function confirmAhDecision(action: "approve" | "reject", reason: string) {
    if (!decideTarget) return;
    if (!canDecideAh) {
      showToast("After-hours Approve is Security Head only", "warning");
      return;
    }
    if (!reason.trim()) {
      showToast("Decision reason is required", "warning");
      return;
    }
    setBusy(true);
    try {
      if (!usingFixtures && token && !token.startsWith("fixture:")) {
        if (action === "approve") {
          await approveVisitApi(token, decideTarget.visitId, reason);
        } else {
          await rejectVisitApi(token, decideTarget.visitId, reason);
        }
        showToast(
          action === "approve"
            ? `SH approved ${decideTarget.name}`
            : `SH rejected ${decideTarget.name}`,
          action === "approve" ? "success" : "warning",
        );
        setDecideTarget(null);
        await load();
      } else {
        applyAfterHoursDecisionLocal(decideTarget.visitId, action, reason);
        showToast(
          action === "approve"
            ? `SH approved ${decideTarget.name} (fixtures)`
            : `SH rejected ${decideTarget.name} (fixtures)`,
          "warning",
        );
        setDecideTarget(null);
        const sess = await getFixtureSession();
        setRows(sess.history);
      }
    } catch (err) {
      applyAfterHoursDecisionLocal(decideTarget.visitId, action, reason);
      const sess = await getFixtureSession();
      setRows(sess.history);
      setUsingFixtures(true);
      setDecideTarget(null);
      showToast(
        err instanceof ApiError ? `Saved in fixtures fallback: ${err.message}` : "Decision saved in fixtures fallback",
        "warning",
      );
    } finally {
      setBusy(false);
    }
  }

  const filtered = useMemo(() => {
    return [...rows]
      .filter((h) => {
        if (usingFixtures && dateFrom) {
          const d = (h.timeIn || h.decisionAt || h.afterHoursEvaluatedAt || h.createdAt || "").slice(0, 10);
          if (d && d < dateFrom) return false;
        }
        if (usingFixtures && dateTo) {
          const d = (h.timeIn || h.decisionAt || h.afterHoursEvaluatedAt || h.createdAt || "").slice(0, 10);
          if (d && d > dateTo) return false;
        }
        if (hostId) {
          const hostName = staff.find((s) => s.id === hostId)?.name;
          if (hostName && h.host !== hostName && !h.host.startsWith(hostName)) return false;
        }
        if (type && h.type !== type) return false;
        if (gate && h.gateIn !== gate && h.gateOut !== gate) return false;
        if (decision && h.decision !== decision) return false;
        if (checkout && h.checkoutType !== checkout) return false;
        if (bl === "1" && !h.blacklistHit) return false;
        if (!matchesAfterHoursFlag(h.afterHours, h.policyTrigger, ahFlag, h.decision)) return false;
        if (q) {
          const hay = [h.name, h.mobile, h.visitId, h.host, h.purpose, h.notes, h.passId, h.policyTrigger]
            .join(" ")
            .toLowerCase();
          if (!hay.includes(q.toLowerCase().trim())) return false;
        }
        return true;
      })
      .sort((a, b) =>
        String(b.timeIn || b.decisionAt || b.afterHoursEvaluatedAt || b.createdAt || "").localeCompare(
          String(a.timeIn || a.decisionAt || a.afterHoursEvaluatedAt || a.createdAt || ""),
        ),
      );
  }, [rows, usingFixtures, dateFrom, dateTo, type, gate, hostId, staff, decision, checkout, bl, ahFlag, q]);

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Visit history</h1>
          <p>
            Default today · max 90 days · afterHours / policyTrigger filters
            {usingFixtures && <span className="source-inline"> · fixtures fallback</span>}
          </p>
        </div>
      </div>
      <div className="toolbar">
        <div className="search">
          <IconSearch />
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Name, mobile, visit id…"
            aria-label="Search history"
          />
        </div>
        <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} aria-label="From" />
        <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} aria-label="To" />
        <select value={gate} onChange={(e) => setGate(e.target.value)} aria-label="Gate">
          <option value="">All gates</option>
          {(gates.length ? gates.map((g) => g.name) : [...GATE_ENUMS]).map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
        <select value={hostId} onChange={(e) => setHostId(e.target.value)} aria-label="Host">
          <option value="">All hosts</option>
          {staff
            .filter((s) => s.id.startsWith("H"))
            .map((h) => (
              <option key={h.id} value={h.id}>
                {h.name}
              </option>
            ))}
        </select>
        <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Type">
          <option value="">All types</option>
          {VISITOR_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <select value={decision} onChange={(e) => setDecision(e.target.value)} aria-label="Decision">
          <option value="">All decisions</option>
          <option>Approved</option>
          <option>Rejected</option>
          <option>Pending</option>
        </select>
        <select value={checkout} onChange={(e) => setCheckout(e.target.value)} aria-label="Checkout">
          <option value="">All checkout</option>
          <option value="Normal">Normal</option>
          <option value="Force">Force</option>
          <option value="Never">Never</option>
        </select>
        <select value={bl} onChange={(e) => setBl(e.target.value)} aria-label="Blacklist">
          <option value="">Any blacklist</option>
          <option value="1">Blacklist hit only</option>
        </select>
        <select value={ahFlag} onChange={(e) => setAhFlag(e.target.value)} aria-label="After-hours">
          <option value="">Any hours</option>
          <option value="afterhours">After-hours</option>
          <option value="holiday">Holiday</option>
          <option value="pending_sh">Pending SH</option>
        </select>
        <ExportButton onClick={() => setExportOpen(true)} />
      </div>
      <p className="form-hint">
        Seed: Evening Vendor Ravi Deshmukh <code>V-AH-VENDOR</code> (today) · Holiday Parent Deepak Nair pass{" "}
        <code>P-7K88</code> on 20 Oct 2026. Priya Sharma <code>P-4F21</code> unchanged.
      </p>
      <div className="board">
        <table>
          <thead>
            <tr>
              <th>Visit</th>
              <th>Visitor</th>
              <th>Type</th>
              <th>Purpose</th>
              <th>Host</th>
              <th>Decision</th>
              <th>In</th>
              <th>Out</th>
              <th>Checkout</th>
              <th>Dur</th>
              <th>BL</th>
              <th>Flags</th>
              <th>Notes</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={14}>No matching visits</td>
              </tr>
            ) : (
              filtered.map((h) => {
                const decCls =
                  h.decision === "Approved"
                    ? "approved"
                    : h.decision === "Rejected"
                      ? "rejected"
                      : "pending";
                return (
                  <tr key={h.visitId}>
                    <td>
                      <code className="tiny">{h.visitId}</code>
                      {h.passId ? <div className="subline">{h.passId}</div> : null}
                    </td>
                    <td>
                      <div className="visitor-cell">
                        <div className={`avatar avatar-md ${avatarClass(h.type)}`}>{initials(h.name)}</div>
                        <div>
                          <strong>{h.name}</strong>
                          <span>{formatMobile(h.mobile)}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className={typeClass(h.type)}>{h.type}</span>
                    </td>
                    <td>
                      <div
                        className={`purpose-cell${expanded[h.visitId] ? " expanded" : ""}`}
                        onClick={() => setExpanded((p) => ({ ...p, [h.visitId]: !p[h.visitId] }))}
                      >
                        {h.purpose || "—"}
                      </div>
                    </td>
                    <td>{h.host || "—"}</td>
                    <td>
                      <span className={`status-pill status-${decCls}`}>{h.decision}</span>
                      {h.decisionReason && <div className="subline">{h.decisionReason}</div>}
                    </td>
                    <td>
                      {h.gateIn || "—"}
                      <div className="subline">{formatDateTime(h.timeIn)}</div>
                    </td>
                    <td>
                      {h.timeOut ? (
                        <>
                          {h.gateOut || "—"}
                          <div className="subline">{formatDateTime(h.timeOut)}</div>
                        </>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td>{h.checkoutType || "—"}</td>
                    <td>{formatDurationMin(h.durationMin)}</td>
                    <td>{h.blacklistHit ? <span className="flag flag-bl">hit</span> : "—"}</td>
                    <td>
                      {h.afterHours ? (
                        <span className="flag flag-ah">{policyTriggerLabel(h.policyTrigger)}</span>
                      ) : (
                        <span className="dim">—</span>
                      )}
                    </td>
                    <td>
                      <div
                        className={`notes-cell${noteOpen[h.visitId] ? " expanded" : ""}`}
                        onClick={() => setNoteOpen((p) => ({ ...p, [h.visitId]: !p[h.visitId] }))}
                      >
                        {h.notes || "—"}
                      </div>
                    </td>
                    <td>
                      {h.afterHours && h.decision === "Pending" ? (
                        canDecideAh ? (
                          <button type="button" className="btn btn-primary btn-sm" onClick={() => setDecideTarget(h)}>
                            SH decide
                          </button>
                        ) : (
                          <span className="subline">SH only</span>
                        )
                      ) : null}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      <ExportModal
        open={exportOpen}
        initialScope="history"
        onClose={() => setExportOpen(false)}
        onAudit={pushAudit}
        bundles={{
          history: {
            headers: [
              "visitId",
              "name",
              "mobile",
              "type",
              "purpose",
              "host",
              "decision",
              "gateIn",
              "timeIn",
              "gateOut",
              "timeOut",
              "checkoutType",
              "durationMin",
              "blacklistHit",
              "afterHours",
              "policyTrigger",
              "notes",
              "demo_watermark",
            ],
            rows: filtered.map((h) => [
              h.visitId,
              h.name,
              h.mobile,
              h.type,
              h.purpose,
              h.host,
              h.decision,
              h.gateIn,
              h.timeIn,
              h.gateOut,
              h.timeOut,
              h.checkoutType,
              h.durationMin,
              h.blacklistHit ? "Y" : "N",
              h.afterHours ? "Y" : "N",
              h.policyTrigger || "",
              h.notes,
              "DEMO",
            ]),
            filter:
              [gate && `gate=${gate}`, type && `type=${type}`, decision && `decision=${decision}`, checkout && `checkout=${checkout}`, ahFlag && `ah=${ahFlag}`, q && `q=${q}`]
                .filter(Boolean)
                .join(", ") || "all history (no filters)",
          },
        }}
      />
      <AfterHoursDecisionModal
        visit={decideTarget}
        busy={busy}
        onCancel={() => setDecideTarget(null)}
        onDecide={(action, reason) => void confirmAhDecision(action, reason)}
      />
    </section>
  );
}
