import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { canEditPickupList, canSetCourtOrder, useAuth } from "../auth/AuthContext";
import { useToast } from "../components/Toast";
import {
  ApiError,
  createAuthorizedPickup,
  getCustodyFlag,
  isNetworkError,
  listAuthorizedPickup,
  listStudents,
  patchAuthorizedPickup,
  putCustodyFlag,
} from "../lib/api";
import { PICKUP_CONSENT_VERSION, PICKUP_RELATIONS } from "../lib/constants";
import { avatarClass, formatMobile, initials } from "../lib/format";
import { custodyBadgeClass, custodyBadgeLabel, studentClassLabel } from "../lib/pickup";
import {
  emptyCustody,
  getPickupFixtureSession,
  saveCustodyLocal,
  upsertPickupPersonLocal,
} from "../lib/pickupFixtures";
import type {
  AuthorizedPickupPerson,
  CustodyFlagRecord,
  CustodyFlagValue,
  PickupRelation,
  Student,
} from "../lib/types";

const emptyPersonForm = {
  name: "",
  relation: "parent" as PickupRelation,
  mobile: "",
  idLast4: "",
  effectiveFrom: "",
  effectiveTo: "",
};

function studentAvatar(student: Student) {
  return student.id === "STU-KABIR" || /singh/i.test(student.name) ? "avatar-orange" : "avatar-cyan";
}

export function PickupListsPage() {
  const { token, user, source } = useAuth();
  const { showToast } = useToast();
  const canEdit = canEditPickupList(user?.role);
  const canCourt = canSetCourtOrder(user?.role);
  const [students, setStudents] = useState<Student[]>([]);
  const [people, setPeople] = useState<AuthorizedPickupPerson[]>([]);
  const [custodyById, setCustodyById] = useState<Record<string, CustodyFlagRecord>>({});
  const [custody, setCustody] = useState<CustodyFlagRecord | null>(null);
  const [selectedId, setSelectedId] = useState("STU-AARAV");
  const [q, setQ] = useState("");
  const [usingFixtures, setUsingFixtures] = useState(source === "fixtures");
  const [loading, setLoading] = useState(true);
  const [savingCustody, setSavingCustody] = useState(false);
  const [flagDraft, setFlagDraft] = useState<CustodyFlagValue>("none");
  const [instructionDraft, setInstructionDraft] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(emptyPersonForm);

  const useLocal = usingFixtures || source === "fixtures" || !token || Boolean(token?.startsWith("fixture:"));

  const loadStudents = useCallback(async () => {
    if (source === "fixtures" || !token || token.startsWith("fixture:")) {
      const sess = getPickupFixtureSession();
      setStudents(sess.students);
      setCustodyById(sess.custody);
      setUsingFixtures(true);
      setLoading(false);
      return;
    }
    try {
      const res = await listStudents(token, { active: true });
      setStudents(res.data);
      const flags = await Promise.all(
        res.data.map(async (s) => {
          try {
            return [s.id, await getCustodyFlag(token, s.id)] as const;
          } catch {
            return [s.id, emptyCustody(s.id)] as const;
          }
        }),
      );
      setCustodyById(Object.fromEntries(flags));
      setUsingFixtures(false);
    } catch {
      const sess = getPickupFixtureSession();
      setStudents(sess.students);
      setCustodyById(sess.custody);
      setUsingFixtures(true);
    } finally {
      setLoading(false);
    }
  }, [source, token]);

  const loadDetail = useCallback(
    async (studentId: string, preferLocal = false) => {
      if (!studentId) return;
      if (preferLocal || source === "fixtures" || !token || token.startsWith("fixture:")) {
        const sess = getPickupFixtureSession();
        setPeople(sess.people[studentId] || []);
        setCustody(sess.custody[studentId] || emptyCustody(studentId));
        setUsingFixtures(true);
        return;
      }
      try {
        const [plist, flag] = await Promise.all([
          listAuthorizedPickup(token, studentId),
          getCustodyFlag(token, studentId),
        ]);
        setPeople(plist.data);
        setCustody(flag);
        setUsingFixtures(false);
      } catch (err) {
        const sess = getPickupFixtureSession();
        setPeople(sess.people[studentId] || []);
        setCustody(sess.custody[studentId] || emptyCustody(studentId));
        setUsingFixtures(true);
        if (!isNetworkError(err)) {
          /* fixtures fallback */
        }
      }
    },
    [source, token],
  );

  useEffect(() => {
    void loadStudents();
  }, [loadStudents]);

  useEffect(() => {
    if (!students.length) return;
    const exists = students.some((s) => s.id === selectedId);
    if (!exists) {
      const aarav = students.find((s) => /aarav/i.test(s.name)) || students[0];
      setSelectedId(aarav.id);
      return;
    }
    void loadDetail(selectedId);
  }, [students, selectedId, loadDetail]);

  useEffect(() => {
    if (!custody) return;
    setFlagDraft((custody.flag as CustodyFlagValue) || "none");
    setInstructionDraft(custody.gateInstruction || "");
  }, [custody]);

  const filteredStudents = useMemo(() => {
    const query = q.trim().toLowerCase();
    return students
      .filter((s) => {
        if (!query) return true;
        return (
          s.name.toLowerCase().includes(query) ||
          studentClassLabel(s).toLowerCase().includes(query) ||
          String(s.studentId || "").toLowerCase().includes(query)
        );
      })
      .sort((a, b) => {
        const seed = (s: Student) => (/aarav/i.test(s.name) ? 0 : 1);
        return seed(a) - seed(b) || a.name.localeCompare(b.name);
      });
  }, [students, q]);

  const selected = students.find((s) => s.id === selectedId) || null;
  const courtLocked = Boolean(custody?.flag === "court_order" && !canCourt);
  const custodyReadOnly = !canEdit || courtLocked;

  function openAdd() {
    if (!canEdit) return;
    if (!selected) {
      showToast("Select a student first", "warning");
      return;
    }
    setEditId(null);
    setForm(emptyPersonForm);
    setModalOpen(true);
  }

  function openEdit(person: AuthorizedPickupPerson) {
    if (!canEdit) return;
    setEditId(person.id);
    setForm({
      name: person.name,
      relation: (person.relation as PickupRelation) || "other",
      mobile: person.mobile || "",
      idLast4: person.idLast4 || "",
      effectiveFrom: person.effectiveFrom || "",
      effectiveTo: person.effectiveTo || "",
    });
    setModalOpen(true);
  }

  async function savePerson(e: FormEvent) {
    e.preventDefault();
    if (!selected || !canEdit) return;
    const name = form.name.trim();
    const mobile = form.mobile.replace(/\D/g, "").slice(-10);
    if (!name) {
      showToast("Name required", "warning");
      return;
    }
    if (!mobile) {
      showToast("Mobile required", "warning");
      return;
    }
    const now = new Date().toISOString();
    const body = {
      name,
      relation: form.relation,
      mobile,
      idLast4: form.idLast4.trim() || null,
      effectiveFrom: form.effectiveFrom || null,
      effectiveTo: form.effectiveTo || null,
      pickupConsentVersion: PICKUP_CONSENT_VERSION,
      pickupConsentAt: now,
    };
    try {
      if (!useLocal && token && !token.startsWith("fixture:")) {
        if (editId) {
          await patchAuthorizedPickup(token, selected.id, editId, {
            name: body.name,
            relation: body.relation,
            mobile: body.mobile,
            idLast4: body.idLast4,
            effectiveFrom: body.effectiveFrom,
            effectiveTo: body.effectiveTo,
          });
        } else {
          await createAuthorizedPickup(token, selected.id, body);
        }
        showToast(editId ? "Person updated" : "Person added", "success");
        setModalOpen(false);
        await loadDetail(selected.id);
        return;
      }
    } catch (err) {
      if (err instanceof ApiError && !isNetworkError(err)) {
        showToast(err.message || "Save failed", "error");
        return;
      }
    }
    const sess = getPickupFixtureSession();
    const existing = editId ? (sess.people[selected.id] || []).find((p) => p.id === editId) : null;
    const person: AuthorizedPickupPerson = {
      id: existing?.id || `APP-${Date.now()}`,
      schoolId: "SCH-DEMO-01",
      studentId: selected.id,
      name,
      relation: form.relation,
      mobile,
      idLast4: form.idLast4.trim() || null,
      active: existing?.active ?? true,
      effectiveFrom: form.effectiveFrom || null,
      effectiveTo: form.effectiveTo || null,
      blockedByCustody: existing?.blockedByCustody ?? false,
      pickupConsentVersion: existing?.pickupConsentVersion || PICKUP_CONSENT_VERSION,
      pickupConsentAt: existing?.pickupConsentAt || now,
    };
    upsertPickupPersonLocal(person, !existing);
    setPeople(getPickupFixtureSession().people[selected.id] || []);
    showToast(existing ? "Person updated" : "Person added", "success");
    setModalOpen(false);
  }

  async function toggleActive(person: AuthorizedPickupPerson) {
    if (!selected || !canEdit) return;
    const nextActive = !person.active;
    try {
      if (!useLocal && token && !token.startsWith("fixture:")) {
        await patchAuthorizedPickup(token, selected.id, person.id, { active: nextActive });
        showToast(nextActive ? "Person reactivated" : "Soft-deactivated (audit retained)", "success");
        await loadDetail(selected.id);
        return;
      }
    } catch (err) {
      if (err instanceof ApiError && !isNetworkError(err)) {
        showToast(err.message || "Update failed", "error");
        return;
      }
    }
    upsertPickupPersonLocal({ ...person, active: nextActive }, false);
    setPeople(getPickupFixtureSession().people[selected.id] || []);
    showToast(nextActive ? "Person reactivated" : "Soft-deactivated (audit retained)", "success");
  }

  async function saveCustody() {
    if (!selected || custodyReadOnly) return;
    if (!canCourt && flagDraft === "court_order") {
      showToast("Office Admin cannot set court_order", "error");
      return;
    }
    if (flagDraft === "court_order" && !instructionDraft.trim()) {
      showToast("court_order requires gate_instruction (fail closed)", "warning");
      return;
    }
    const next: CustodyFlagRecord = {
      ...(custody || emptyCustody(selected.id)),
      studentId: selected.id,
      flag: flagDraft,
      gateInstruction: instructionDraft.slice(0, 280),
    };
    setSavingCustody(true);
    try {
      if (!useLocal && token && !token.startsWith("fixture:")) {
        const saved = await putCustodyFlag(token, selected.id, {
          flag: next.flag,
          gateInstruction: next.gateInstruction,
          blockedPersonIds: next.blockedPersonIds || [],
          allowedPersonIds: next.allowedPersonIds ?? null,
        });
        setCustody(saved);
        setCustodyById((prev) => ({ ...prev, [selected.id]: saved }));
        showToast(`Custody saved for ${selected.name}`, "success");
        return;
      }
    } catch (err) {
      if (err instanceof ApiError && !isNetworkError(err)) {
        showToast(err.message || "Custody save failed", "error");
        return;
      }
    } finally {
      setSavingCustody(false);
    }
    saveCustodyLocal(next);
    setCustody(next);
    setCustodyById((prev) => ({ ...prev, [selected.id]: next }));
    showToast(`Custody saved for ${selected.name}`, "success");
  }

  return (
    <section className="view active">
      <div className="topbar">
        <div>
          <h1>Students & authorized pickup</h1>
          <p>
            Manual CRUD · custody flag + gate_instruction only · no court PDF
            {usingFixtures && <span className="source-inline"> · fixtures fallback</span>}
          </p>
        </div>
        <span className="tag type-parent">Priority P2</span>
      </div>

      <div className="pickup-split">
        <div className="student-list">
          <div className="student-list-search">
            <input
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Filter students…"
              aria-label="Filter students"
            />
          </div>
          {loading ? (
            <p className="pickup-empty">Loading students…</p>
          ) : filteredStudents.length === 0 ? (
            <p className="pickup-empty">No matching students</p>
          ) : (
            filteredStudents.map((s) => (
              <button
                key={s.id}
                type="button"
                className={`stu-item${s.id === selectedId ? " active" : ""}`}
                onClick={() => setSelectedId(s.id)}
              >
                <div className={`avatar avatar-md ${studentAvatar(s)}`}>{initials(s.name)}</div>
                <div>
                  <strong>{s.name}</strong>
                  <span>
                    {studentClassLabel(s)}
                    {custodyById[s.id]?.flag && custodyById[s.id].flag !== "none"
                      ? ` · ${custodyById[s.id].flag}`
                      : ""}
                  </span>
                </div>
              </button>
            ))
          )}
        </div>

        <div className="detail-card">
          {!selected ? (
            <p className="pickup-empty">Select a student to maintain the authorized list.</p>
          ) : (
            <>
              <div className="detail-head">
                <div className="detail-identity">
                  <div className={`avatar avatar-xl ${studentAvatar(selected)}`}>{initials(selected.name)}</div>
                  <div>
                    <h2>{selected.name}</h2>
                    <div className="detail-class">{studentClassLabel(selected)}</div>
                    {custodyBadgeLabel(custody?.flag) && (
                      <div className="detail-flag">
                        <span className={custodyBadgeClass(custody?.flag)}>{custodyBadgeLabel(custody?.flag)}</span>
                      </div>
                    )}
                  </div>
                </div>
                <button type="button" className="btn btn-primary btn-sm" onClick={openAdd} disabled={!canEdit}>
                  + Add authorized person
                </button>
              </div>

              <div className="section-title">Authorized pickup list</div>
              {people.length === 0 ? (
                <p className="pickup-empty">No authorized people yet.</p>
              ) : (
                people.map((p) => (
                  <div key={p.id} className={`person-row${p.active ? "" : " inactive"}`}>
                    <div className={`avatar avatar-md ${avatarClass("Parent")}`}>{initials(p.name)}</div>
                    <div className="info">
                      <strong>{p.name}</strong>
                      <span>
                        {p.relation}
                        {" · "}
                        {formatMobile(p.mobile)}
                        {p.idLast4 ? ` · ID ····${p.idLast4}` : ""}
                        {p.effectiveTo ? ` · to ${p.effectiveTo}` : ""}
                        {p.blockedByCustody ? " · custody-blocked" : ""}
                        {p.active ? "" : " · inactive"}
                      </span>
                    </div>
                    <div className="person-actions">
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => openEdit(p)}>
                        Edit
                      </button>
                      <button
                        type="button"
                        className={`btn btn-sm ${p.active ? "btn-danger" : "btn-ghost"}`}
                        onClick={() => void toggleActive(p)}
                      >
                        {p.active ? "Deactivate" : "Reactivate"}
                      </button>
                    </div>
                  </div>
                ))
              )}

              <div className="section-title">Custody flag</div>
              <div className="custody-box">
                {courtLocked && (
                  <div className="oa-lock show">
                    Office Admin cannot change a <strong>court_order</strong> record. Security Head only.
                  </div>
                )}
                <div className="radio-row" role="radiogroup" aria-label="Custody flag">
                  {(["none", "restricted", "court_order"] as CustodyFlagValue[]).map((flag) => {
                    const lockedOption = flag === "court_order" && !canCourt;
                    return (
                      <label
                        key={flag}
                        className={`radio-pill${flagDraft === flag ? ` active-${flag === "court_order" ? "court" : flag}` : ""}${lockedOption ? " locked" : ""}`}
                      >
                        <input
                          type="radio"
                          name="custody"
                          value={flag}
                          checked={flagDraft === flag}
                          disabled={custodyReadOnly || lockedOption}
                          onChange={() => {
                            if (lockedOption) {
                              showToast("Office Admin cannot set court_order", "warning");
                              return;
                            }
                            setFlagDraft(flag);
                          }}
                        />
                        {flag === "court_order" ? "court_order" : flag}
                      </label>
                    );
                  })}
                </div>
                <div className="field" style={{ marginBottom: 0 }}>
                  <label htmlFor="gate-instruction">Gate instruction (≤280 · shown on tablet)</label>
                  <textarea
                    id="gate-instruction"
                    maxLength={280}
                    rows={3}
                    value={instructionDraft}
                    disabled={custodyReadOnly}
                    onChange={(e) => setInstructionDraft(e.target.value.slice(0, 280))}
                    placeholder="e.g. Release only to Neha Mehta (Mother). Block all others."
                  />
                  <div className={`char-count${instructionDraft.length > 260 ? " warn" : ""}`}>
                    {instructionDraft.length} / 280
                  </div>
                </div>
                <div className="note-no-pdf">
                  No court PDF / image upload in product (PRD P4 / AC-D5). School keeps docs offline.
                </div>
                <div className="custody-save">
                  <button
                    type="button"
                    className="btn btn-cyan btn-sm"
                    onClick={() => void saveCustody()}
                    disabled={custodyReadOnly || savingCustody}
                  >
                    Save custody
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      {modalOpen && (
        <div
          className="modal-overlay open"
          role="dialog"
          aria-modal="true"
          onClick={(e) => {
            if (e.target === e.currentTarget) setModalOpen(false);
          }}
        >
          <div className="modal modal-form">
            <h3>{editId ? "Edit authorized person" : "Add authorized person"}</h3>
            <p className="modal-sub">Admin / Security Head only · soft-deactivate keeps audit rows</p>
            <form onSubmit={(e) => void savePerson(e)}>
              <div className="field">
                <label htmlFor="m-name">Name</label>
                <input
                  id="m-name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="Full name"
                  required
                />
              </div>
              <div className="field-row">
                <div className="field">
                  <label htmlFor="m-relation">Relation</label>
                  <select
                    id="m-relation"
                    value={form.relation}
                    onChange={(e) => setForm({ ...form, relation: e.target.value as PickupRelation })}
                  >
                    {PICKUP_RELATIONS.map((rel) => (
                      <option key={rel} value={rel}>
                        {rel}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="m-mobile">Mobile</label>
                  <input
                    id="m-mobile"
                    type="tel"
                    value={form.mobile}
                    onChange={(e) => setForm({ ...form, mobile: e.target.value })}
                    placeholder="+91 …"
                    required
                  />
                </div>
              </div>
              <div className="field">
                <label htmlFor="m-id4">ID last-4 (optional)</label>
                <input
                  id="m-id4"
                  value={form.idLast4}
                  maxLength={4}
                  onChange={(e) => setForm({ ...form, idLast4: e.target.value.replace(/\D/g, "").slice(0, 4) })}
                  placeholder="masked last-4 only"
                />
              </div>
              <div className="field-row">
                <div className="field">
                  <label htmlFor="m-from">Effective from</label>
                  <input
                    id="m-from"
                    type="date"
                    value={form.effectiveFrom}
                    onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })}
                  />
                </div>
                <div className="field">
                  <label htmlFor="m-to">Effective to (optional)</label>
                  <input
                    id="m-to"
                    type="date"
                    value={form.effectiveTo}
                    onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })}
                  />
                </div>
              </div>
              <div className="modal-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setModalOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Save
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  );
}
