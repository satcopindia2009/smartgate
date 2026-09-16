import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { todayByGate, visitorTypeMix } from "../lib/api";
import { VISITOR_TYPES } from "../lib/constants";
import { fixtureReports, loadFixtures } from "../lib/fixtures";
import { todayIso } from "../lib/format";
import type { GateReport } from "../lib/types";

export function ReportsPage() {
  const { token, source } = useAuth();
  const [reports, setReports] = useState<GateReport[]>([]);
  const [mix, setMix] = useState<Record<string, number>>({});

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
        const [r, m] = await Promise.all([
          todayByGate(token),
          visitorTypeMix(token, today, today),
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
  }, [source, token]);

  const total = Object.values(mix).reduce((s, n) => s + n, 0) || 1;

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Reports</h1>
          <p>Today-by-gate + type mix · simple aggregates (not BI)</p>
        </div>
      </div>
      <h2 className="section-h">Today by gate</h2>
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
    </section>
  );
}
