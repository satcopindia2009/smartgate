import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { useAudit } from "../components/AuditContext";
import { ExportButton, ExportModal } from "../components/ExportModal";
import { rangeByGate, todayByGate, visitorTypeMix } from "../lib/api";
import { VISITOR_TYPES } from "../lib/constants";
import { fixtureReports, loadFixtures } from "../lib/fixtures";
import { todayIso } from "../lib/format";
import type { GateReport } from "../lib/types";

export function ReportsPage() {
  const { token, source } = useAuth();
  const { pushAudit } = useAudit();
  const [reports, setReports] = useState<GateReport[]>([]);
  const [mix, setMix] = useState<Record<string, number>>({});
  const [dateFrom, setDateFrom] = useState(todayIso());
  const [dateTo, setDateTo] = useState(todayIso());
  const [exportOpen, setExportOpen] = useState(false);

  useEffect(() => {
    void (async () => {
      const fx = await loadFixtures();
      const fallbackMix: Record<string, number> = {};
      VISITOR_TYPES.forEach((t) => {
        fallbackMix[t] = 0;
      });
      fx.history.forEach((h) => {
        if (h.type) fallbackMix[h.type] = (fallbackMix[h.type] || 0) + 1;
      });
      if (source === "fixtures" || !token || token.startsWith("fixture:")) {
        setReports(fixtureReports(fx));
        setMix(fallbackMix);
        return;
      }
      try {
        const today = todayIso();
        const useRange = dateFrom !== today || dateTo !== today;
        const [r, m] = await Promise.all([
          useRange ? rangeByGate(token, dateFrom, dateTo) : todayByGate(token),
          visitorTypeMix(token, dateFrom, dateTo),
        ]);
        setReports(r.data.length ? r.data : fixtureReports(fx));
        if (m.data.length) {
          const next: Record<string, number> = {};
          VISITOR_TYPES.forEach((t) => {
            next[t] = 0;
          });
          m.data.forEach((row) => {
            next[row.visitorType] = row.count;
          });
          setMix(next);
        } else {
          setMix(fallbackMix);
        }
      } catch {
        setReports(fixtureReports(fx));
        setMix(fallbackMix);
      }
    })();
  }, [source, token, dateFrom, dateTo]);

  const total = Object.values(mix).reduce((s, n) => s + n, 0) || 1;
  const today = todayIso();
  const heading = dateFrom === today && dateTo === today ? "Today by gate" : "Range by gate";

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Reports</h1>
          <p>Today-by-gate + range-by-gate + type mix · simple aggregates (not BI)</p>
        </div>
        <ExportButton onClick={() => setExportOpen(true)} />
      </div>
      <div className="toolbar">
        <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} aria-label="From" />
        <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} aria-label="To" />
      </div>
      <h2 className="section-h">{heading}</h2>
      <div className="report-grid">
        {reports.map((g) => (
          <div className="report-card" key={g.gate}>
            <h3>{g.gate}</h3>
            <div className="metric">
              <span>Visits in</span>
              <strong>{g.checkIns}</strong>
            </div>
            <div className="metric">
              <span>Unique mobiles</span>
              <strong>{g.uniqueMobiles}</strong>
            </div>
            <div className="metric">
              <span>Median approve</span>
              <strong>{g.medianApprovalSec != null ? `${g.medianApprovalSec}s` : "—"}</strong>
            </div>
            <div className="metric">
              <span>Force CO</span>
              <strong>{g.forceCheckouts}</strong>
            </div>
          </div>
        ))}
      </div>
      <h2 className="section-h">Type mix</h2>
      <div className="type-mix">
        {Object.entries(mix).map(([t, n]) => (
          <div className="bar-wrap" key={t}>
            <span>{t}</span>
            <strong>{n}</strong>
            <div className="pct">{Math.round((100 * n) / total)}%</div>
          </div>
        ))}
      </div>
      <ExportModal
        open={exportOpen}
        initialScope="gates"
        onClose={() => setExportOpen(false)}
        onAudit={pushAudit}
        bundles={{
          gates: {
            headers: [
              "gate",
              "checkIns",
              "checkOuts",
              "stillInside",
              "rejects",
              "blacklistHits",
              "forceCheckouts",
              "uniqueMobiles",
              "medianApprovalSec",
              "peakInside",
              "from",
              "to",
              "demo_watermark",
            ],
            rows: reports.map((g) => [
              g.gate,
              g.checkIns,
              g.checkOuts,
              g.stillInside,
              g.rejects,
              g.blacklistHits,
              g.forceCheckouts,
              g.uniqueMobiles,
              g.medianApprovalSec,
              g.peakInside,
              dateFrom,
              dateTo,
              "DEMO",
            ]),
            filter: `${dateFrom}…${dateTo}`,
          },
        }}
      />
    </section>
  );
}
