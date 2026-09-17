"""In-memory multi-tenant store (demo school + additional tenants)."""
from __future__ import annotations

from copy import deepcopy
from typing import Any, Optional

from app.config import SCHOOL_ID

_state: dict[str, Any] = {
    "schools": {},
    "gates": {},
    "staff": {},
    "users": {},  # id -> user
    "users_by_username": {},  # username -> user id
    "visits": {},
    "passes": {},  # passId -> pass record
    "passes_by_token": {},
    "blacklist": {},
    "media": {},
    "outbox": [],
    "exports": [],
    "students": {},
    "authorized_pickup": {},
    "custody_flags": {},  # studentId -> flag
    "pickups": {},
    "campus_hours": {},  # (schoolId, weekday) -> row
    "holidays": {},
    "zone_labels": {},  # (schoolId, key) -> row
    "escort_rules": {},  # (schoolId, visitorType) -> row
    "blast_templates": {},
    "blasts": {},
    "blast_recipients": {},
    "roster_imports": [],
    "counters": {
        "visit_seq": 40,
        "staff_seq": 10,
        "bl_seq": 10,
        "outbox_seq": 1,
        "student_seq": 10,
        "person_seq": 10,
        "pickup_seq": 10,
        "holiday_seq": 10,
        "template_seq": 10,
        "blast_seq": 10,
        "recipient_seq": 10,
        "school_seq": 1,
        "import_seq": 1,
    },
}


def reset() -> None:
    global _state
    _state = {
        "schools": {},
        "gates": {},
        "staff": {},
        "users": {},
        "users_by_username": {},
        "visits": {},
        "passes": {},
        "passes_by_token": {},
        "blacklist": {},
        "media": {},
        "outbox": [],
        "exports": [],
        "students": {},
        "authorized_pickup": {},
        "custody_flags": {},
        "pickups": {},
        "campus_hours": {},
        "holidays": {},
        "zone_labels": {},
        "escort_rules": {},
        "blast_templates": {},
        "blasts": {},
        "blast_recipients": {},
        "roster_imports": [],
        "counters": {
            "visit_seq": 40,
            "staff_seq": 10,
            "bl_seq": 10,
            "outbox_seq": 1,
            "student_seq": 10,
            "person_seq": 10,
            "pickup_seq": 10,
            "holiday_seq": 10,
            "template_seq": 10,
            "blast_seq": 10,
            "recipient_seq": 10,
            "school_seq": 1,
            "import_seq": 1,
        },
    }


def school() -> Optional[dict]:
    """Demo school (SCH-DEMO-01) for existing callers. Prefer get_school(id)."""
    return get_school(SCHOOL_ID) or next(iter(_state["schools"].values()), None)


def get_school(school_id: str) -> Optional[dict]:
    return _state["schools"].get(school_id)


def get_school_by_code(school_code: str) -> Optional[dict]:
    want = (school_code or "").strip().upper()
    if not want:
        return None
    for s in _state["schools"].values():
        code = (s.get("schoolCode") or "").strip().upper()
        if code == want or s.get("id", "").upper() == want:
            return s
    return None


def list_schools() -> list[dict]:
    return list(_state["schools"].values())


def put_school(s: dict) -> None:
    _state["schools"][s["id"]] = s


def set_school(s: dict) -> None:
    put_school(s)


def next_seq(name: str) -> int:
    _state["counters"][name] = _state["counters"].get(name, 0) + 1
    return _state["counters"][name]


# --- users ---


def put_user(user: dict) -> None:
    _state["users"][user["id"]] = user
    _state["users_by_username"][user["username"].lower()] = user["id"]


def get_user(user_id: str) -> Optional[dict]:
    return _state["users"].get(user_id)


def get_user_by_username(username: str) -> Optional[dict]:
    uid = _state["users_by_username"].get(username.lower())
    return _state["users"].get(uid) if uid else None


def list_users(school_id: str) -> list[dict]:
    return [u for u in _state["users"].values() if u.get("schoolId") == school_id]


# --- gates / staff ---


def put_gate(g: dict) -> None:
    _state["gates"][(g["schoolId"], g["id"])] = g


def list_gates(school_id: str) -> list[dict]:
    return [g for g in _state["gates"].values() if g["schoolId"] == school_id]


def get_gate(gate_id: str, school_id: Optional[str] = None) -> Optional[dict]:
    if school_id:
        return _state["gates"].get((school_id, gate_id))
    matches = [g for (_sid, gid), g in _state["gates"].items() if gid == gate_id]
    if not matches:
        return None
    if len(matches) == 1:
        return matches[0]
    for g in matches:
        if g.get("schoolId") == SCHOOL_ID:
            return g
    return matches[0]


def gate_by_name(name: str, school_id: Optional[str] = None) -> Optional[dict]:
    for g in _state["gates"].values():
        if g["name"] == name and (school_id is None or g.get("schoolId") == school_id):
            return g
    return None


def put_staff(s: dict) -> None:
    _state["staff"][s["id"]] = s


def list_staff(school_id: str, active: Optional[bool] = None) -> list[dict]:
    out = [s for s in _state["staff"].values() if s["schoolId"] == school_id]
    if active is not None:
        out = [s for s in out if s["active"] == active]
    return out


def get_staff(staff_id: str) -> Optional[dict]:
    return _state["staff"].get(staff_id)


# --- visits ---


def put_visit(v: dict) -> None:
    v.setdefault("afterHours", False)
    v.setdefault("policyTrigger", None)
    v.setdefault("afterHoursEvaluatedAt", v.get("createdAt"))
    v.setdefault("afterHoursApproveReason", None)
    if "escortRequired" not in v:
        from app.escort import stamp_escort_zones

        stamp_escort_zones(v)
    v.setdefault("escortRequired", False)
    v.setdefault("allowedZones", [])
    v.setdefault("escortStaffId", None)
    v.setdefault("escortSuggestedByHost", None)
    v.setdefault("escortWaived", False)
    v.setdefault("escortWaiveReason", None)
    v.setdefault("escortClearedAt", None)
    _state["visits"][v["id"]] = v


def get_visit(visit_id: str) -> Optional[dict]:
    return _state["visits"].get(visit_id)


def list_visits(school_id: str) -> list[dict]:
    return [v for v in _state["visits"].values() if v["schoolId"] == school_id]


# --- passes ---


def put_pass(p: dict) -> None:
    _state["passes"][p["passId"]] = p
    if p.get("token"):
        _state["passes_by_token"][p["token"]] = p["passId"]


def get_pass(pass_id: str) -> Optional[dict]:
    return _state["passes"].get(pass_id)


def get_pass_by_token(token: str) -> Optional[dict]:
    pid = _state["passes_by_token"].get(token)
    return _state["passes"].get(pid) if pid else None


# --- blacklist ---


def put_blacklist(b: dict) -> None:
    _state["blacklist"][b["id"]] = b


def get_blacklist(bl_id: str) -> Optional[dict]:
    return _state["blacklist"].get(bl_id)


def list_blacklist(school_id: str, active: Optional[bool] = None) -> list[dict]:
    out = [b for b in _state["blacklist"].values() if b["schoolId"] == school_id]
    if active is not None:
        out = [b for b in out if b["active"] == active]
    return out


# --- media ---


def put_media(m: dict) -> None:
    _state["media"][m["key"]] = m


def get_media(key: str) -> Optional[dict]:
    return _state["media"].get(key)


# --- outbox / exports ---


def add_outbox(event: dict) -> dict:
    event = deepcopy(event)
    event["id"] = f"OB-{next_seq('outbox_seq'):04d}"
    _state["outbox"].append(event)
    return event


def list_outbox(status: Optional[str] = None) -> list[dict]:
    items = list(_state["outbox"])
    if status:
        items = [o for o in items if o["status"] == status]
    return items


def add_export(rec: dict) -> dict:
    _state["exports"].append(rec)
    return rec


def list_exports() -> list[dict]:
    return list(_state["exports"])


# --- students / authorized pickup / custody / pickups ---


def put_student(s: dict) -> None:
    _state["students"][s["id"]] = s


def get_student(student_id: str) -> Optional[dict]:
    return _state["students"].get(student_id)


def list_students(school_id: str) -> list[dict]:
    return [s for s in _state["students"].values() if s["schoolId"] == school_id]


def get_student_by_roster_id(school_id: str, roster_student_id: str) -> Optional[dict]:
    key = (roster_student_id or "").strip()
    if not key:
        return None
    for s in _state["students"].values():
        if s.get("schoolId") == school_id and (s.get("studentId") or "").strip() == key:
            return s
    return None


def put_authorized_person(p: dict) -> None:
    _state["authorized_pickup"][p["id"]] = p


def get_authorized_person(person_id: str) -> Optional[dict]:
    return _state["authorized_pickup"].get(person_id)


def list_authorized_people(school_id: str, student_id: Optional[str] = None) -> list[dict]:
    out = [p for p in _state["authorized_pickup"].values() if p["schoolId"] == school_id]
    if student_id:
        out = [p for p in out if p["studentId"] == student_id]
    return out


def get_authorized_person_by_mobile(
    school_id: str, student_id: str, mobile: str
) -> Optional[dict]:
    from app.util import normalize_mobile

    want = normalize_mobile(mobile)
    if not want:
        return None
    for p in list_authorized_people(school_id, student_id):
        if normalize_mobile(p.get("mobile")) == want:
            return p
    return None


def put_custody_flag(flag: dict) -> None:
    _state["custody_flags"][flag["studentId"]] = flag


def get_custody_flag(student_id: str) -> Optional[dict]:
    return _state["custody_flags"].get(student_id)


def put_pickup(p: dict) -> None:
    _state["pickups"][p["id"]] = p


def get_pickup(pickup_id: str) -> Optional[dict]:
    return _state["pickups"].get(pickup_id)


def list_pickups(school_id: str) -> list[dict]:
    return [p for p in _state["pickups"].values() if p["schoolId"] == school_id]


# --- campus hours / holidays (P2 after-hours A1–A2) ---


def put_hours_row(row: dict) -> None:
    _state["campus_hours"][(row["schoolId"], row["weekday"])] = row


def get_hours_row(school_id: str, weekday: str) -> Optional[dict]:
    return _state["campus_hours"].get((school_id, weekday))


def list_hours(school_id: str) -> list[dict]:
    order = ("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    rows = [
        r
        for (sid, _wd), r in _state["campus_hours"].items()
        if sid == school_id
    ]
    index = {r["weekday"]: r for r in rows}
    return [index[wd] for wd in order if wd in index]


def replace_hours(school_id: str, rows: list[dict]) -> list[dict]:
    for key in [k for k in _state["campus_hours"] if k[0] == school_id]:
        del _state["campus_hours"][key]
    for row in rows:
        put_hours_row(row)
    return list_hours(school_id)


def put_holiday(h: dict) -> None:
    _state["holidays"][h["id"]] = h


def get_holiday(holiday_id: str) -> Optional[dict]:
    return _state["holidays"].get(holiday_id)


def delete_holiday(holiday_id: str) -> bool:
    return _state["holidays"].pop(holiday_id, None) is not None


def list_holidays(
    school_id: str,
    date_from: Optional[str] = None,
    date_to: Optional[str] = None,
) -> list[dict]:
    out = [h for h in _state["holidays"].values() if h["schoolId"] == school_id]
    if date_from:
        out = [h for h in out if h["date"] >= date_from]
    if date_to:
        out = [h for h in out if h["date"] <= date_to]
    out.sort(key=lambda h: h["date"])
    return out


def holiday_on_date(school_id: str, day: str) -> Optional[dict]:
    for h in _state["holidays"].values():
        if h["schoolId"] == school_id and h["date"] == day:
            return h
    return None


# --- zones / escort rules (P2 B4) ---


def put_zone(row: dict) -> None:
    _state["zone_labels"][(row["schoolId"], row["key"])] = row


def get_zone(school_id: str, key: str) -> Optional[dict]:
    return _state["zone_labels"].get((school_id, key))


def list_zones(school_id: str) -> list[dict]:
    from app.escort import ZONE_KEYS

    index = {
        key: row
        for (sid, key), row in _state["zone_labels"].items()
        if sid == school_id
    }
    return [index[key] for key in ZONE_KEYS if key in index]


def put_escort_rule(row: dict) -> None:
    _state["escort_rules"][(row["schoolId"], row["visitorType"])] = row


def get_escort_rule(school_id: str, visitor_type: str) -> Optional[dict]:
    return _state["escort_rules"].get((school_id, visitor_type))


def list_escort_rules(school_id: str) -> list[dict]:
    from app.escort import VISITOR_TYPES

    index = {
        vt: row
        for (sid, vt), row in _state["escort_rules"].items()
        if sid == school_id
    }
    return [index[vt] for vt in VISITOR_TYPES if vt in index]


# --- emergency blast (P2 E3 · B1–B6) ---


def put_blast_template(row: dict) -> None:
    _state["blast_templates"][row["id"]] = row


def get_blast_template(template_id: str) -> Optional[dict]:
    return _state["blast_templates"].get(template_id)


def list_blast_templates(school_id: str) -> list[dict]:
    rows = [t for t in _state["blast_templates"].values() if t["schoolId"] == school_id]
    rows.sort(key=lambda t: t.get("name") or t["id"])
    return rows


def put_blast(row: dict) -> None:
    _state["blasts"][row["blastId"]] = row


def get_blast(blast_id: str) -> Optional[dict]:
    return _state["blasts"].get(blast_id)


def list_blasts(school_id: str) -> list[dict]:
    rows = [b for b in _state["blasts"].values() if b["schoolId"] == school_id]
    rows.sort(key=lambda b: b.get("triggeredAt") or "", reverse=True)
    return rows


def put_blast_recipient(row: dict) -> None:
    _state["blast_recipients"][row["id"]] = row


def list_blast_recipients(blast_id: str) -> list[dict]:
    rows = [r for r in _state["blast_recipients"].values() if r["blastId"] == blast_id]
    rows.sort(key=lambda r: (r.get("visitId") or "", r.get("channel") or "", r["id"]))
    return rows


def next_blast_id() -> str:
    from app.util import gen_blast_id

    return gen_blast_id(next_seq("blast_seq"))


def add_roster_import(row: dict) -> dict:
    rec = deepcopy(row)
    rec.setdefault("id", f"IMP-{next_seq('import_seq'):04d}")
    _state["roster_imports"].append(rec)
    return rec


def list_roster_imports(school_id: str) -> list[dict]:
    rows = [r for r in _state["roster_imports"] if r.get("schoolId") == school_id]
    rows.sort(key=lambda r: r.get("importedAt") or "", reverse=True)
    return rows
