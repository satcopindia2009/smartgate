import { useCallback, useEffect, useMemo, useState } from "react";
import { canApproveHostPending, canForceCheckout, canTriggerBlast, useAuth } from "../auth/AuthContext";
import { PendingVisitDecisionModal } from "../components/PendingVisitDecisionModal";
import { useAudit } from "../components/AuditContext";
import { EmergencyBlastModal } from "../components/EmergencyBlastModal";
import { ExportButton, ExportModal, type ExportScope } from "../components/ExportModal";
import { ForceCheckoutModal } from "../components/ForceCheckoutModal";
import { GateMultiSelect } from "../components/GateMultiSelect";
import { IconBlast, IconSearch } from "../components/Icons";
import { useToast } from "../components/Toast";
import {
  ApiError,
  confirmBlastApi,
  approveVisitApi,
  forceCheckoutApi,
  getBlast,
  getBlastConfig,
  isNetworkError,
  listBlastTemplates,
  listGates,
  listInside,
  listStaff,
  listVisits,
  postBlast,
  previewBlast,
  rejectVisitApi,
  retryFailedBlast,
  todayByGate,
} from "../lib/api";
import { matchesAfterHoursFlag, policyTriggerLabel } from "../lib/afterHours";
import {
  blastIdOf,
  confirmBlastLocal,
  countsLabel,
  getBlastLocal,
  latestBlastLocal,
  listActiveTemplates,
  previewFromInside,
  rememberLastBlastId,
  rememberedLastBlastId,
  retryFailedLocal,
  seedBlastConfig,
  SEED_TEMPLATES,
} from "../lib/blast";
import { escortCell, formatAllowedZones } from "../lib/escort";
import { schoolDisplayName, withoutDemoChrome } from "../lib/school";
import {
  BLAST_INSTRUCTION_MAX,
  LIVE_REFRESH_MS,
  OVERDUE_HOURS_DEFAULT,
  SEED_BLAST_ID,
  VISITOR_TYPES,
} from "../lib/constants";
import {
  applyForceCheckoutLocal,
  applyHostPendingDecisionLocal,
  fixtureGates,
  fixtureReports,
  fixtureStaff,
  getFixtureSession,
  loadFixtures,
} from "../lib/fixtures";
import {
  avatarClass,
  formatClock,
  formatDuration,
  formatMobile,
  formatTime,
  initials,
  isOverdue,
  todayIso,
  typeClass,
} from "../lib/format";
import {
  isAfterHoursPending,
  isHostPending,
  matchesLiveSearch,
  mergeVisitsById,
  toHistoryVisit,
  toLiveVisitor,
} from "../lib/mapVisit";
import type {
  BlastPreview,
  BlastTemplate,
  EmergencyBlast,
  Gate,
  GateReport,
  HistoryVisit,
  LiveVisitor,
  Staff,
} from "../lib/types";

export function LivePage() {
  const { token, user, source } = useAuth();
  const { showToast } = useToast();
  const { pushAudit } = useAudit();
  const [exportOpen, setExportOpen] = useState(false);
  const [exportScope, setExportScope] = useState<ExportScope>("inside");
  const [clock, setClock] = useState(formatClock);
  const [nowMs, setNowMs] = useState(Date.now());
  const [gates, setGates] = useState<Gate[]>([]);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [rows, setRows] = useState<LiveVisitor[]>([]);
  const [pendingAh, setPendingAh] = useState<HistoryVisit[]>([]);
  const [pendingHost, setPendingHost] = useState<HistoryVisit[]>([]);
  const [rejectTarget, setRejectTarget] = useState<HistoryVisit | null>(null);
  const [reports, setReports] = useState<GateReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [usingFixtures, setUsingFixtures] = useState(source === "fixtures");
  const [q, setQ] = useState("");
  const [type, setType] = useState("");
  const [hostId, setHostId] = useState("");
  const [gateIds, setGateIds] = useState<string[]>([]);
  const [flag, setFlag] = useState("");
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [forceTarget, setForceTarget] = useState<LiveVisitor | null>(null);
  const [busy, setBusy] = useState(false);
  const [blastEnabled, setBlastEnabled] = useState(false);
  const [lastBlast, setLastBlast] = useState<EmergencyBlast | null>(null);
  const [blastOpen, setBlastOpen] = useState(false);
  const [blastStage, setBlastStage] = useState<"confirm" | "results">("confirm");
  const [blastTemplates, setBlastTemplates] = useState<BlastTemplate[]>(SEED_TEMPLATES);
  const [blastPreview, setBlastPreview] = useState<BlastPreview | null>(null);
  const [blastTemplateId, setBlastTemplateId] = useState("tpl_evac_assembly");
  const [blastInstruction, setBlastInstruction] = useState("");
  const [blastConfirmed, setBlastConfirmed] = useState(false);
  const [blastBusy, setBlastBusy] = useState(false);
  const [blastLoadError, setBlastLoadError] = useState<string | null>(null);
  const [resultBlast, setResultBlast] = useState<EmergencyBlast | null>(null);
  const canBlast = canTriggerBlast(user?.role);
  const canDecideHost = canApproveHostPending(user?.role);

  const hideDemo = withoutDemoChrome(user);

  const refresh = useCallback(async () => {
    const fx = await loadFixtures();
    if (source === "fixtures" || !token || token.startsWith("fixture:")) {
      if (hideDemo) {
        setGates([]);
        setStaff([]);
        setRows([]);
        setPendingAh([]);
        setPendingHost([]);
        setReports([]);
        setUsingFixtures(false);
        setBlastEnabled(false);
        setLastBlast(null);
        setLoading(false);
        return;
      }
      const sess = await getFixtureSession();
      setGates(fixtureGates());
      setStaff(fixtureStaff(fx));
      setRows(sess.inside);
      setPendingAh(sess.history.filter(isAfterHoursPending));
      setPendingHost(sess.history.filter(isHostPending));
      setReports(fixtureReports(fx));
      setUsingFixtures(true);
      setBlastEnabled(seedBlastConfig().emergencyBlastEnabled);
      setLastBlast(getBlastLocal(rememberedLastBlastId()) || latestBlastLocal());
      setLoading(false);
      return;
    }
    try {
      const today = todayIso();
      const [g, s, inside, pendingToday, pendingAhOpen, pendingStatus, rep] = await Promise.all([
        listGates(token),
        listStaff(token),
        listInside(token),
        listVisits(token, { afterHours: true, dateFrom: today, dateTo: today }).catch(() => ({ data: [] })),
        listVisits(token, { afterHours: true }).catch(() => ({ data: [] })),
        listVisits(token, { status: "pending" }).catch(() => ({ data: [] })),
        todayByGate(token).catch(() => ({ data: [] as GateReport[] })),
      ]);
      setGates(g.data);
      setStaff(s.data);
      setRows(inside.data.map((v) => toLiveVisitor(v, g.data, s.data)));
      const pendingMerged = mergeVisitsById([pendingToday.data, pendingAhOpen.data, pendingStatus.data]).map(
        (v) => toHistoryVisit(v, g.data, s.data),
      );
      setPendingAh(pendingMerged.filter(isAfterHoursPending));
      setPendingHost(pendingMerged.filter(isHostPending));
      setReports(rep.data.length ? rep.data : fixtureReports(fx));
      setUsingFixtures(false);
      try {
        const cfg = await getBlastConfig(token);
        setBlastEnabled(Boolean(cfg.emergencyBlastEnabled));
        if (cfg.emergencyBlastEnabled) {
          const wanted = rememberedLastBlastId();
          const latest =
            (await getBlast(token, wanted).catch(() => null)) ||
            (wanted !== SEED_BLAST_ID
              ? await getBlast(token, SEED_BLAST_ID).catch(() => null)
              : null);
          if (latest) rememberLastBlastId(blastIdOf(latest));
          setLastBlast(latest);
        } else {
          setLastBlast(null);
        }
      } catch {
        setBlastEnabled(seedBlastConfig().emergencyBlastEnabled);
        setLastBlast(latestBlastLocal());
      }
    } catch (err) {
      if (hideDemo) {
        setUsingFixtures(false);
        if (err instanceof Error) {
          showToast(err.message, "error");
        }
        return;
      }
      const sess = await getFixtureSession();
      setGates(fixtureGates());
      setStaff(fixtureStaff(fx));
      setRows(sess.inside);
      setPendingAh(sess.history.filter(isAfterHoursPending));
      setPendingHost(sess.history.filter(isHostPending));
      setReports(fixtureReports(fx));
      setUsingFixtures(true);
      setBlastEnabled(seedBlastConfig().emergencyBlastEnabled);
      setLastBlast(getBlastLocal(rememberedLastBlastId()) || latestBlastLocal());
      if (isNetworkError(err)) {
        /* silent fallback */
      }
    } finally {
      setLoading(false);
    }
  }, [source, token, hideDemo, showToast]);

  useEffect(() => {
    void refresh();
    const poll = window.setInterval(() => void refresh(), LIVE_REFRESH_MS);
    const tick = window.setInterval(() => {
      setClock(formatClock());
      setNowMs(Date.now());
    }, 15_000);
    return () => {
      window.clearInterval(poll);
      window.clearInterval(tick);
    };
  }, [refresh]);

  const hosts = useMemo(
    () => staff.filter((s) => s.id.startsWith("H")),
    [staff],
  );

  const filtered = useMemo(() => {
    return rows.filter((v) => {
      if (type && v.type !== type) return false;
      if (hostId && v.hostId !== hostId) return false;
      if (gateIds.length) {
        const gid = v.gateId || gates.find((g) => g.name === v.gate)?.id;
        if (!gid || !gateIds.includes(gid)) return false;
      }
      if (flag === "overdue" && !isOverdue(v.timeIn, v.flags)) return false;
      if (flag === "blacklist" && !v.blacklistHit) return false;
      if (!matchesAfterHoursFlag(v.afterHours, v.policyTrigger, flag, v.status)) return false;
      if (!matchesLiveSearch(v, q)) return false;
      return true;
    });
  }, [rows, type, hostId, gateIds, flag, q, gates]);

  const stats = useMemo(() => {
    const overdue = rows.filter((v) => isOverdue(v.timeIn, v.flags)).length;
    const outToday = reports.reduce((s, g) => s + (g.checkOuts || 0), 0);
    const blHits = Math.max(
      reports.reduce((s, g) => s + (g.blacklistHits || 0), 0),
      rows.filter((v) => v.blacklistHit).length,
    );
    return { inside: rows.length, overdue, outToday, blHits };
  }, [rows, reports]);

  async function decideHostPending(target: HistoryVisit, action: "approve" | "reject", reason?: string) {
    if (target.afterHours) {
      showToast("After-hours Approve is Admin or Security Head only — use History to decide", "warning");
      return;
    }
    if (!canDecideHost) {
      showToast("Host-pending Approve is Office Admin or Security Head only", "warning");
      return;
    }
    if (action === "reject" && !reason?.trim()) {
      showToast("Enter a reject reason", "warning");
      return;
    }
    setBusy(true);
    try {
      if (!usingFixtures && token && !token.startsWith("fixture:")) {
        if (action === "approve") {
          await approveVisitApi(token, target.visitId);
        } else {
          await rejectVisitApi(token, target.visitId, reason!.trim());
        }
        showToast(
          action === "approve" ? `Approved ${target.name}` : `Rejected ${target.name}`,
          action === "approve" ? "success" : "warning",
        );
        setRejectTarget(null);
        await refresh();
      } else {
        applyHostPendingDecisionLocal(target.visitId, action, reason);
        setPendingHost((prev) => prev.filter((p) => p.visitId !== target.visitId));
        showToast(
          action === "approve"
            ? `Approved ${target.name} (fixtures)`
            : `Rejected ${target.name} (fixtures)`,
          "warning",
        );
        setRejectTarget(null);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        showToast(err.message, "error");
        return;
      }
      const fallback = (err instanceof ApiError && err.status === 404) || isNetworkError(err);
      if (fallback && !hideDemo) {
        await getFixtureSession();
        applyHostPendingDecisionLocal(target.visitId, action, reason);
        setPendingHost((prev) => prev.filter((p) => p.visitId !== target.visitId));
        setUsingFixtures(true);
        setRejectTarget(null);
        showToast(
          err instanceof ApiError
            ? `Saved in fixtures fallback: ${err.message}`
            : "Decision saved in fixtures fallback",
          "warning",
        );
        return;
      }
      showToast(err instanceof Error ? err.message : "Decision failed", "error");
    } finally {
      setBusy(false);
    }
  }

  async function confirmForce(reason: string) {
    if (!forceTarget) return;
    if (!reason.trim()) {
      showToast("Enter a force-checkout reason", "warning");
      return;
    }
    if (!canForceCheckout(user?.role)) {
      showToast("Force checkout is Admin or Security Head only", "warning");
      return;
    }
    setBusy(true);
    try {
      if (!usingFixtures && token && !token.startsWith("fixture:")) {
        await forceCheckoutApi(token, forceTarget.visitId, reason);
        showToast(`Force checkout: ${forceTarget.name} — ${reason}`, "warning");
        setForceTarget(null);
        await refresh();
      } else {
        applyForceCheckoutLocal(forceTarget.visitId, reason);
        setRows((prev) => prev.filter((r) => r.visitId !== forceTarget.visitId));
        showToast(`Force checkout: ${forceTarget.name} — ${reason}`, "warning");
        setForceTarget(null);
      }
    } catch (err) {
      applyForceCheckoutLocal(forceTarget.visitId, reason);
      setRows((prev) => prev.filter((r) => r.visitId !== forceTarget.visitId));
      showToast(
        `Force checkout saved in fixtures fallback: ${forceTarget.name}`,
        "warning",
      );
      setForceTarget(null);
      if (err instanceof Error && !isNetworkError(err)) {
        /* already toasted fallback */
      }
    } finally {
      setBusy(false);
    }
  }

  const useLocalBlast =
    usingFixtures || source === "fixtures" || !token || Boolean(token?.startsWith("fixture:"));

  async function loadBlastConfirm(templateId: string) {
    setBlastLoadError(null);
    const localInside = rows;
    if (useLocalBlast) {
      const templates = listActiveTemplates();
      setBlastTemplates(templates);
      const tpl = templates.find((t) => t.id === templateId) || templates[0] || null;
      setBlastPreview(previewFromInside(localInside, tpl));
      return;
    }
    try {
      const [cfg, listed, preview] = await Promise.all([
        getBlastConfig(token!),
        listBlastTemplates(token!, true),
        previewBlast(token!, templateId),
      ]);
      if (!cfg.emergencyBlastEnabled) {
        setBlastEnabled(false);
        setBlastLoadError("Emergency blast is not enabled for this school");
        return;
      }
      const templates = listActiveTemplates(listed.data.length ? listed.data : SEED_TEMPLATES);
      setBlastTemplates(templates);
      setBlastPreview(preview);
    } catch (err) {
      const templates = listActiveTemplates();
      setBlastTemplates(templates);
      const tpl = templates.find((t) => t.id === templateId) || templates[0] || null;
      setBlastPreview(previewFromInside(localInside, tpl));
      if (err instanceof ApiError && err.status === 404) {
        setBlastEnabled(false);
        setBlastLoadError("Emergency blast is not enabled");
        return;
      }
      if (err instanceof ApiError && !isNetworkError(err)) {
        setBlastLoadError(err.message);
      }
    }
  }

  function openBlastConfirm() {
    if (!canBlast) {
      showToast("Emergency blast is Admin or Security Head only", "warning");
      return;
    }
    setBlastStage("confirm");
    setBlastConfirmed(false);
    setBlastInstruction("");
    setBlastTemplateId("tpl_evac_assembly");
    setResultBlast(null);
    setBlastOpen(true);
    void loadBlastConfirm("tpl_evac_assembly");
  }

  function openBlastResults(blast: EmergencyBlast) {
    rememberLastBlastId(blastIdOf(blast));
    setResultBlast(blast);
    setLastBlast(blast);
    setBlastStage("results");
    setBlastOpen(true);
  }

  async function changeBlastTemplate(id: string) {
    setBlastTemplateId(id);
    setBlastConfirmed(false);
    await loadBlastConfirm(id);
  }

  async function confirmBlastSend() {
    if (!canBlast) {
      showToast("Emergency blast is Admin or Security Head only", "warning");
      return;
    }
    if (!blastConfirmed) {
      showToast("Confirm is required — blast is not one-click", "warning");
      return;
    }
    const template =
      blastTemplates.find((t) => t.id === blastTemplateId) || blastTemplates[0] || null;
    if (!template) {
      showToast("Select a blast template", "warning");
      return;
    }
    const instruction = blastInstruction.trim().slice(0, BLAST_INSTRUCTION_MAX);
    setBlastBusy(true);
    try {
      let confirmed: EmergencyBlast;
      if (!useLocalBlast && token && !token.startsWith("fixture:")) {
        const pending = await postBlast(token, {
          templateId: template.id,
          mode: "pending_confirm",
          instruction: instruction || null,
        });
        const pendingId = blastIdOf(pending as EmergencyBlast);
        if (!pendingId) {
          throw new ApiError(400, "VALIDATION", "pending_confirm did not return a blast id");
        }
        confirmed = await confirmBlastApi(token, pendingId);
      } else {
        confirmed = confirmBlastLocal(
          rows,
          template,
          instruction || template.instruction,
          user?.id || "U-ADMIN",
        );
      }
      rememberLastBlastId(blastIdOf(confirmed));
      setResultBlast(confirmed);
      setLastBlast(confirmed);
      setBlastStage("results");
      showToast(
        `Blast ${blastIdOf(confirmed)} · ${countsLabel(confirmed)} · who’s-inside unchanged`,
        "success",
      );
      await refresh();
    } catch (err) {
      if (!useLocalBlast && token && !token.startsWith("fixture:")) {
        try {
          const fallback = await postBlast(token, {
            templateId: template.id,
            confirm: true,
            instruction: instruction || null,
          });
          const confirmed = fallback as EmergencyBlast;
          if (blastIdOf(confirmed)) {
            rememberLastBlastId(blastIdOf(confirmed));
            setResultBlast(confirmed);
            setLastBlast(confirmed);
            setBlastStage("results");
            showToast(`Blast ${blastIdOf(confirmed)} · who’s-inside unchanged`, "success");
            await refresh();
            return;
          }
        } catch {
          /* use local below */
        }
      }
      const local = confirmBlastLocal(
        rows,
        template,
        instruction || template.instruction,
        user?.id || "U-ADMIN",
      );
      rememberLastBlastId(blastIdOf(local));
      setResultBlast(local);
      setLastBlast(local);
      setBlastStage("results");
      showToast(`Blast saved in fixtures fallback · who’s-inside unchanged`, "warning");
      if (err instanceof ApiError && !isNetworkError(err)) {
        /* already fell back */
      }
    } finally {
      setBlastBusy(false);
    }
  }

  async function retryLastFailed() {
    const target = resultBlast || lastBlast;
    const id = blastIdOf(target);
    if (!id) return;
    setBlastBusy(true);
    try {
      if (!useLocalBlast && token && !token.startsWith("fixture:")) {
        const updated = await retryFailedBlast(token, id);
        rememberLastBlastId(blastIdOf(updated));
        setResultBlast(updated);
        setLastBlast(updated);
        showToast(`Retry failed · ${countsLabel(updated)}`, "success");
      } else {
        const updated = retryFailedLocal(id) || getBlastLocal(id);
        if (updated) {
          rememberLastBlastId(blastIdOf(updated));
          setResultBlast(updated);
          setLastBlast(updated);
          showToast(`Retry failed · ${countsLabel(updated)}`, "success");
        }
      }
    } catch (err) {
      const updated = retryFailedLocal(id) || getBlastLocal(id);
      if (updated) {
        rememberLastBlastId(blastIdOf(updated));
        setResultBlast(updated);
        setLastBlast(updated);
        showToast("Retry saved in fixtures fallback", "warning");
      } else if (err instanceof Error) {
        showToast(err.message, "error");
      }
    } finally {
      setBlastBusy(false);
    }
  }

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Who’s inside</h1>
          <p>
            Live campus presence · <span className="live-dot">Live</span>
            {usingFixtures && <span className="source-inline"> · fixtures fallback</span>}
          </p>
        </div>
        <div className="topbar-actions">
          {blastEnabled && (
            <button
              type="button"
              className="btn btn-danger btn-blast-cta"
              disabled={!canBlast}
              onClick={openBlastConfirm}
            >
              <IconBlast />
              Emergency blast
            </button>
          )}
          <div className="admin-clock">{clock}</div>
        </div>
      </div>

      <div className="stats">
        <div className="stat-card">
          <div className="label">Inside now</div>
          <div className="value cyan">{stats.inside}</div>
        </div>
        <div className="stat-card">
          <div className="label">Overdue (&gt;{OVERDUE_HOURS_DEFAULT}h)</div>
          <div className="value orange">{stats.overdue}</div>
        </div>
        <div className="stat-card">
          <div className="label">Checked out today</div>
          <div className="value green">{stats.outToday}</div>
        </div>
        <div className="stat-card">
          <div className="label">Blacklist hits today</div>
          <div className="value red">{stats.blHits}</div>
        </div>
      </div>

      <div className="toolbar">
        <div className="search">
          <IconSearch />
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search name, mobile last 4, pass…"
            aria-label="Search visitors"
          />
        </div>
        <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Filter by type">
          <option value="">All types</option>
          {VISITOR_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <GateMultiSelect gates={gates} selected={gateIds} onChange={setGateIds} />
        <select value={hostId} onChange={(e) => setHostId(e.target.value)} aria-label="Filter by host">
          <option value="">All hosts</option>
          {hosts.map((h) => (
            <option key={h.id} value={h.id}>
              {h.name}
            </option>
          ))}
        </select>
        <select value={flag} onChange={(e) => setFlag(e.target.value)} aria-label="Filter flags">
          <option value="">All flags</option>
          <option value="overdue">Overdue only</option>
          <option value="blacklist">Blacklist flag</option>
          <option value="afterhours">After-hours</option>
          <option value="holiday">Holiday</option>
        </select>
        <ExportButton
          onClick={() => {
            setExportScope("inside");
            setExportOpen(true);
          }}
        />
      </div>

      {blastEnabled && lastBlast && (
        <div className="blast-last-strip">
          <div className="blast-last-copy">
            <strong>Last blast {blastIdOf(lastBlast)}</strong>
            <div className="subline">
              {lastBlast.status} · {countsLabel(lastBlast)} · template {lastBlast.templateId} · no
              auto-checkout
            </div>
          </div>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => openBlastResults(lastBlast)}
          >
            View audit
          </button>
        </div>
      )}

      {pendingHost.length > 0 && (
        <div className="pending-sh-strip">
          <div className="pending-host-head">
            Pending host approval · in-hours · Admin / Security Head may decide
          </div>
          <div className="pending-sh-list">
            {pendingHost.map((p) => (
              <div className="pending-sh-item" key={p.visitId}>
                <div>
                  <strong>{p.name}</strong>
                  <div className="subline">
                    {p.visitId}
                    {p.passId ? ` · ${p.passId}` : ""} · {p.type} · {p.host}
                  </div>
                </div>
                <span className="status-pill status-pending">Pending · host</span>
                {canDecideHost ? (
                  <div className="action-btns">
                    <button
                      type="button"
                      className="btn btn-danger btn-sm"
                      disabled={busy}
                      onClick={() => setRejectTarget(p)}
                    >
                      Reject
                    </button>
                    <button
                      type="button"
                      className="btn btn-success btn-sm"
                      disabled={busy}
                      onClick={() => void decideHostPending(p, "approve")}
                    >
                      Approve
                    </button>
                  </div>
                ) : (
                  <span className="subline">Admin / SH only</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {pendingAh.length > 0 && (
        <div className="pending-sh-strip">
          <div className="pending-sh-head">
            Pending after-hours · Admin or Security Head · Host Approve is a no-op
          </div>
          <div className="pending-sh-list">
            {pendingAh.map((p) => (
              <div className="pending-sh-item" key={p.visitId}>
                <div>
                  <strong>{p.name}</strong>
                  <div className="subline">
                    {p.visitId}
                    {p.passId ? ` · ${p.passId}` : ""} · {p.type} · {p.host}
                  </div>
                </div>
                <span className="flag flag-ah">{policyTriggerLabel(p.policyTrigger)}</span>
                <span className="status-pill status-pending">Pending · Admin|SH</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="board">
        <table>
          <thead>
            <tr>
              <th>Visitor</th>
              <th>Type</th>
              <th>Host</th>
              <th>Purpose</th>
              <th>Gate</th>
              <th>Time-in</th>
              <th>Duration</th>
              <th>Pass</th>
              <th>Flags</th>
              <th>Escort</th>
              <th>Zones</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr className="empty-row">
                <td colSpan={12}>Loading {schoolDisplayName(user)}…</td>
              </tr>
            ) : filtered.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={12}>No visitors inside</td>
              </tr>
            ) : (
              filtered.map((v) => {
                const overdue = isOverdue(v.timeIn, v.flags, OVERDUE_HOURS_DEFAULT, nowMs);
                return (
                  <tr key={v.visitId}>
                    <td>
                      <div className="visitor-cell">
                        <div className={`avatar avatar-lg ${avatarClass(v.type)}`}>{initials(v.name)}</div>
                        <div>
                          <strong>{v.name}</strong>
                          <span>{formatMobile(v.mobile)}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span className={typeClass(v.type)}>{v.type}</span>
                    </td>
                    <td>{v.host || "—"}</td>
                    <td>
                      <div
                        className={`purpose-cell${expanded[v.visitId] ? " expanded" : ""}`}
                        title="Click to expand"
                        onClick={() =>
                          setExpanded((prev) => ({ ...prev, [v.visitId]: !prev[v.visitId] }))
                        }
                      >
                        {v.purpose || "—"}
                      </div>
                    </td>
                    <td>{v.gate}</td>
                    <td>{formatTime(v.timeIn)}</td>
                    <td className={overdue ? "dur-overdue" : ""}>{formatDuration(v.timeIn, nowMs)}</td>
                    <td>
                      <code className="pass-id">{v.passId || "—"}</code>
                    </td>
                    <td>
                      {overdue && <span className="flag flag-overdue">overdue</span>}
                      {v.blacklistHit && <span className="flag flag-bl">BL alert</span>}
                      {v.afterHours && (
                        <span className="flag flag-ah">{policyTriggerLabel(v.policyTrigger)}</span>
                      )}
                      {!overdue && !v.blacklistHit && !v.afterHours && <span className="dim">—</span>}
                    </td>
                    <td>{escortCell(v.escortRequired, v.escortName, v.escortWaived)}</td>
                    <td className="zones-cell">{formatAllowedZones(v.allowedZones)}</td>
                    <td>
                      <div className="action-btns">
                        <button
                          type="button"
                          className="btn btn-danger btn-force"
                          disabled={!canForceCheckout(user?.role)}
                          onClick={() => setForceTarget(v)}
                        >
                          Force
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <EmergencyBlastModal
        open={blastOpen}
        stage={blastStage}
        templates={blastTemplates}
        preview={blastPreview}
        blast={resultBlast || lastBlast}
        selectedTemplateId={blastTemplateId}
        instruction={blastInstruction}
        confirmed={blastConfirmed}
        busy={blastBusy}
        loadError={blastLoadError}
        onTemplateChange={(id) => void changeBlastTemplate(id)}
        onInstructionChange={setBlastInstruction}
        onConfirmedChange={setBlastConfirmed}
        onCancel={() => setBlastOpen(false)}
        onConfirmSend={() => void confirmBlastSend()}
        onRetryFailed={() => void retryLastFailed()}
      />
      <PendingVisitDecisionModal
        visit={rejectTarget}
        busy={busy}
        onCancel={() => setRejectTarget(null)}
        onReject={(reason) => {
          if (rejectTarget) void decideHostPending(rejectTarget, "reject", reason);
        }}
      />
      <ForceCheckoutModal
        visitor={forceTarget}
        busy={busy}
        onCancel={() => setForceTarget(null)}
        onConfirm={(reason) => void confirmForce(reason)}
      />
      <ExportModal
        open={exportOpen}
        initialScope={exportScope}
        onClose={() => setExportOpen(false)}
        onAudit={pushAudit}
        bundles={{
          inside: {
            headers: [
              "visitId",
              "name",
              "type",
              "mobile",
              "host",
              "purpose",
              "gate",
              "timeIn",
              "passId",
              "blacklistHit",
              "afterHours",
              "policyTrigger",
              "escort",
              "allowedZones",
              "demo_watermark",
            ],
            rows: filtered.map((v) => [
              v.visitId,
              v.name,
              v.type,
              v.mobile,
              v.host,
              v.purpose,
              v.gate,
              v.timeIn,
              v.passId,
              v.blacklistHit ? "Y" : "N",
              v.afterHours ? "Y" : "N",
              v.policyTrigger || "",
              escortCell(v.escortRequired, v.escortName, v.escortWaived),
              (v.allowedZones || []).join("|"),
              "DEMO",
            ]),
            filter: [type && `type=${type}`, hostId && `host=${hostId}`, flag && `flag=${flag}`, q && `q=${q}`]
              .filter(Boolean)
              .join(", ") || "all currently inside",
          },
        }}
      />
    </section>
  );
}
