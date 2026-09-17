import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { useAudit } from "../components/AuditContext";
import { ExportButton, ExportModal } from "../components/ExportModal";
import { listGates, todayByGate } from "../lib/api";
import { GATE_ENUMS } from "../lib/constants";
import { fixtureGates, fixtureReports, loadFixtures } from "../lib/fixtures";
import type { Gate, GateReport } from "../lib/types";

export function GatesPage() {
  const { token, source } = useAuth();
  const { pushAudit } = useAudit();
  const [gates, setGates] = useState<Gate[]>([]);
  const [reports, setReports] = useState<GateReport[]>([]);
  const [exportOpen, setExportOpen] = useState(false);

  useEffect(() => {
    void (async () => {
      const fx = await loadFixtures();
      if (source === "fixtures" || !token || token.startsWith("fixture:")) {
        setGates(fixtureGates());
        setReports(fixtureReports(fx));
        return;
      }
      try {
        const [g, r] = await Promise.all([listGates(token), todayByGate(token)]);
        setGates(g.data);
        setReports(r.data.length ? r.data : fixtureReports(fx));
      } catch {
        setGates(fixtureGates());
        setReports(fixtureReports(fx));
      }
    })();
  }, [source, token]);

  const names = gates.length ? gates.map((g) => g.name) : [...GATE_ENUMS];

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Gates</h1>
          <p>Locked enums: Main · Pedestrian · Staff · Bus Bay</p>
        </div>
        <ExportButton onClick={() => setExportOpen(true)} title="Export daily gate summary CSV" />
      </div>
      <div className="report-grid">
        {names.map((name) => {
          const r = reports.find((x) => x.gate === name);
          return (
            <div className="report-card" key={name}>
              <h3>{name}</h3>
              <div className="metric">
                <span>Still inside</span>
                <strong>{r?.stillInside ?? 0}</strong>
              </div>
              <div className="metric">
                <span>Check-ins</span>
                <strong>{r?.checkIns ?? 0}</strong>
              </div>
              <div className="metric">
                <span>Check-outs</span>
                <strong>{r?.checkOuts ?? 0}</strong>
              </div>
              <div className="metric">
                <span>BL hits</span>
                <strong>{r?.blacklistHits ?? 0}</strong>
              </div>
            </div>
          );
        })}
      </div>
      <div className="board">
        <table>
          <thead>
            <tr>
              <th>Gate</th>
              <th>Check-ins</th>
              <th>Check-outs</th>
              <th>Still inside</th>
              <th>Rejects</th>
              <th>BL hits</th>
              <th>Force CO</th>
              <th>Unique mobiles</th>
              <th>Median approve</th>
              <th>Peak inside</th>
            </tr>
          </thead>
          <tbody>
            {reports.map((g) => (
              <tr key={g.gate}>
                <td>
                  <strong>{g.gate}</strong>
                </td>
                <td>{g.checkIns}</td>
                <td>{g.checkOuts}</td>
                <td>{g.stillInside}</td>
                <td>{g.rejects}</td>
                <td>{g.blacklistHits}</td>
                <td>{g.forceCheckouts}</td>
                <td>{g.uniqueMobiles}</td>
                <td>{g.medianApprovalSec != null ? `${g.medianApprovalSec}s` : "—"}</td>
                <td>{g.peakInside ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
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
              "timezone",
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
              "Asia/Calcutta",
              "DEMO",
            ]),
            filter: "today-by-gate",
          },
        }}
      />
    </section>
  );
}
