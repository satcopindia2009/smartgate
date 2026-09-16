import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { useAudit } from "../components/AuditContext";
import { ExportButton } from "../components/ExportModal";
import { PickupExportModal } from "../components/PickupExportModal";
import { useToast } from "../components/Toast";
import { isNetworkError, listGates, listPickups, listStudents } from "../lib/api";
import { PICKUP_STATUSES } from "../lib/constants";
import { fixtureGates } from "../lib/fixtures";
import { formatDateTime, formatMobile } from "../lib/format";
import { pickupGateLabel, pickupStatusClass, studentClassLabel } from "../lib/pickup";
import { getPickupFixtureSession } from "../lib/pickupFixtures";
import type { Gate, PickupEvent, Student } from "../lib/types";

export function PickupHistoryPage() {
  const { token, source } = useAuth();
  const { pushAudit } = useAudit();
  const { showToast } = useToast();
  const [events, setEvents] = useState<PickupEvent[]>([]);
  const [students, setStudents] = useState<Student[]>([]);
  const [gates, setGates] = useState<Gate[]>([]);
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [usingFixtures, setUsingFixtures] = useState(source === "fixtures");
  const [exportOpen, setExportOpen] = useState(false);

  const load = useCallback(async () => {
    if (source === "fixtures" || !token || token.startsWith("fixture:")) {
      const sess = getPickupFixtureSession();
      setStudents(sess.students);
      setEvents(sess.events);
      setGates(fixtureGates());
      setUsingFixtures(true);
      return;
    }
    try {
      const [plist, slist, glist] = await Promise.all([
        listPickups(token),
        listStudents(token),
        listGates(token).catch(() => ({ data: fixtureGates() })),
      ]);
      setEvents(plist.data);
      setStudents(slist.data);
      setGates(glist.data);
      setUsingFixtures(false);
    } catch (err) {
      const sess = getPickupFixtureSession();
      setStudents(sess.students);
      setEvents(sess.events);
      setGates(fixtureGates());
      setUsingFixtures(true);
      if (!isNetworkError(err)) {
        showToast("Pickup history fell back to fixtures", "warning");
      }
    }
  }, [source, token, showToast]);

  useEffect(() => {
    void load();
  }, [load]);

  const studentById = useMemo(() => {
    const map = new Map<string, Student>();
    students.forEach((s) => map.set(s.id, s));
    return map;
  }, [students]);

  const filtered = useMemo(() => {
    const query = q.trim().toLowerCase();
    return [...events]
      .filter((ev) => {
        if (status && ev.status !== status) return false;
        if (!query) return true;
        const student = studentById.get(ev.studentId);
        const hay = [
          student?.name,
          ev.collectorName,
          ev.collectorRelation,
          ev.collectorMobile,
          pickupGateLabel(ev.gateId, gates),
          ev.status,
          ev.id,
          ev.overrideReason,
        ]
          .join(" ")
          .toLowerCase();
        return hay.includes(query);
      })
      .sort((a, b) => String(b.attemptedAt || "").localeCompare(String(a.attemptedAt || "")));
  }, [events, status, q, studentById, gates]);

  const exportRows = filtered.map((ev) => {
    const student = studentById.get(ev.studentId);
    return [
      ev.id,
      student?.name || ev.studentId,
      studentClassLabel(student),
      ev.collectorName,
      ev.collectorRelation || "",
      formatMobile(ev.collectorMobile),
      pickupGateLabel(ev.gateId, gates),
      ev.status,
      ev.attemptedAt,
      ev.releasedAt || "",
      ev.override ? "Y" : "N",
      ev.overrideReason || "",
      ev.custodyFlagSnapshot || "",
      ev.matchMethod || "",
      ev.collectorLivePhotoRef || "",
      "DEMO",
    ];
  });

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Pickup history</h1>
          <p>
            Date · student · collector · relation · gate · status · override
            {usingFixtures && <span className="source-inline"> · fixtures fallback</span>}
          </p>
        </div>
      </div>
      <div className="toolbar">
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search student, collector, gate…"
          aria-label="Search pickup history"
        />
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Pickup status">
          <option value="">All statuses</option>
          {PICKUP_STATUSES.map((st) => (
            <option key={st} value={st}>
              {st}
            </option>
          ))}
        </select>
        <ExportButton onClick={() => setExportOpen(true)} title="Export pickup history CSV" />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Student</th>
              <th>Collector</th>
              <th>Relation</th>
              <th>Gate</th>
              <th>Status</th>
              <th>Time (IST)</th>
              <th>Override</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={7}>No matching events</td>
              </tr>
            ) : (
              filtered.map((ev) => {
                const student = studentById.get(ev.studentId);
                return (
                  <tr key={ev.id}>
                    <td>
                      <strong>{student?.name || ev.studentId}</strong>
                      <div className="subline">{studentClassLabel(student)}</div>
                    </td>
                    <td>
                      {ev.collectorName || "—"}
                      <div className="subline">{formatMobile(ev.collectorMobile)}</div>
                    </td>
                    <td>{ev.collectorRelation || "—"}</td>
                    <td>{pickupGateLabel(ev.gateId, gates)}</td>
                    <td>
                      <span className={`status-pill ${pickupStatusClass(ev.status)}`}>{ev.status}</span>
                    </td>
                    <td>{formatDateTime(ev.releasedAt || ev.attemptedAt)}</td>
                    <td>
                      {ev.override ? (
                        <>
                          <span className="status-pill chip-override">Yes</span>
                          {ev.overrideReason && <div className="subline">{ev.overrideReason}</div>}
                        </>
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      <PickupExportModal
        open={exportOpen}
        filter={[status && `status=${status}`, q && `q=${q}`].filter(Boolean).join(", ") || "all pickup events"}
        headers={[
          "id",
          "student",
          "class",
          "collector",
          "relation",
          "mobile",
          "gate",
          "status",
          "attemptedAt",
          "releasedAt",
          "override",
          "overrideReason",
          "custodyFlagSnapshot",
          "matchMethod",
          "livePhotoRef",
          "demo_watermark",
        ]}
        rows={exportRows}
        onClose={() => setExportOpen(false)}
        onAudit={pushAudit}
      />
    </section>
  );
}
