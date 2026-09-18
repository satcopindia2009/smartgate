"""In-memory multi-tenant store (single demo school)."""
from __future__ import annotations

from copy import deepcopy
from typing import Any, Optional

_state: dict[str, Any] = {
    "school": None,
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
    "notifications": [],
    "notification_seq": 1,
    "exports": [],
    "dsr_requests": {},
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
        "notification_seq": 1,
        "dsr_seq": 0,
    },
}


def reset() -> None:
    global _state
    _state = {
        "school": None,
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
        "notifications": [],
        "notification_seq": 1,
        "exports": [],
        "dsr_requests": {},
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
            "notification_seq": 1,
            "dsr_seq": 0,
        },
    }


def export_state() -> dict:
    """Deep-ish snapshot for persistence (dicts/lists copied shallowly)."""
    from copy import deepcopy
    return deepcopy(_state)


def import_state(state: dict) -> None:
    """Replace in-memory store from a deserialized snapshot."""
    global _state
    from copy import deepcopy

    incoming = deepcopy(state)
    defaults = {
        "school": None,
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
        "notifications": [],
        "notification_seq": 1,
        "exports": [],
        "dsr_requests": {},
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
        "counters": {},
    }
    for k, v in defaults.items():
        if k not in incoming or incoming[k] is None:
            incoming[k] = deepcopy(v)
    if not incoming.get("users_by_username") and incoming.get("users"):
        incoming["users_by_username"] = {
            (u.get("username") or "").lower(): uid
            for uid, u in incoming["users"].items()
            if isinstance(u, dict) and u.get("username")
        }
    counters = incoming.setdefault("counters", {})
    if "notification_seq" not in counters:
        counters["notification_seq"] = int(incoming.get("notification_seq") or 1)
    _state = incoming


def school() -> dict:
    return _state["school"]


def set_school(s: dict) -> None:
    _state["school"] = s
    schools = _state.setdefault("schools", {})
    schools[s["id"]] = s


def put_school(s: dict) -> None:
    schools = _state.setdefault("schools", {})
    schools[s["id"]] = s
    # do not clobber primary demo school pointer
    if _state.get("school") is None:
        _state["school"] = s


def get_school(school_id: str):
    schools = _state.get("schools") or {}
    if school_id in schools:
        return schools[school_id]
    primary = _state.get("school")
    if primary and primary.get("id") == school_id:
        return primary
    return None


def list_schools() -> list[dict]:
    return list((_state.get("schools") or {}).values())


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
    _state["gates"][g["id"]] = g


def list_gates(school_id: str) -> list[dict]:
    return [g for g in _state["gates"].values() if g["schoolId"] == school_id]


def get_gate(gate_id: str) -> Optional[dict]:
    return _state["gates"].get(gate_id)


def gate_by_name(name: str) -> Optional[dict]:
    for g in _state["gates"].values():
        if g["name"] == name:
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
    v.setdefault("legalHold", False)
    v.setdefault("legalHoldReason", None)
    v.setdefault("legalHoldAt", None)
    v.setdefault("legalHoldByUserId", None)
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


def list_media(school_id: Optional[str] = None) -> list[dict]:
    items = list(_state.get("media", {}).values())
    if school_id:
        items = [m for m in items if m.get("schoolId") == school_id]
    return items


def delete_media(key: str) -> Optional[dict]:
    return _state["media"].pop(key, None)


def clear_visit_media_keys(keys: set[str]) -> int:
    """Clear visit livePhotoKey/idImageKey/signatureKey when media purged."""
    cleared = 0
    for v in _state.get("visits", {}).values():
        for field in ("livePhotoKey", "idImageKey", "signatureKey"):
            if v.get(field) in keys:
                v[field] = None
                cleared += 1
    return cleared


def _unlink_media_file(key: str) -> None:
    from pathlib import Path

    media_dir = Path(__file__).resolve().parents[1] / "data" / "media"
    safe = key.replace("\\", "/").lstrip("/")
    for path in (media_dir / safe, media_dir / key):
        if path.is_file():
            try:
                path.unlink()
            except OSError:
                pass
            return



def purge_expired_media(
    school_id: str,
    *,
    dry_run: bool = False,
    as_of_date: Optional[str] = None,
) -> dict:
    """Delete media with retainUntil < as_of_date (ISO date). Clears visit keys.

    Skips media belonging to visits with legalHold=true (audited/counted).
    """
    from datetime import date as date_cls

    from app.util import now_iso

    today = as_of_date or date_cls.today().isoformat()
    expired = []
    skipped_legal_hold: list[str] = []
    for m in list_media(school_id):
        until = m.get("retainUntil")
        if not until:
            continue
        if until[:10] >= today:
            continue
        visit_id = m.get("visitId")
        visit = get_visit(visit_id) if visit_id else None
        if visit and visit.get("legalHold"):
            skipped_legal_hold.append(m["key"])
            continue
        # Also skip if media key is linked on a legal-hold visit even without visitId stamp
        if not visit:
            for v in list_visits(school_id):
                if not v.get("legalHold"):
                    continue
                for field in ("livePhotoKey", "idImageKey", "signatureKey"):
                    if v.get(field) == m["key"]:
                        skipped_legal_hold.append(m["key"])
                        visit = v
                        break
                if visit:
                    break
            if visit and visit.get("legalHold"):
                continue
        expired.append(m)

    if skipped_legal_hold:
        print(
            f"[retention] skipped {len(skipped_legal_hold)} media key(s) "
            f"due to visit legalHold: {skipped_legal_hold[:20]}"
        )

    purged_keys: list[str] = []
    for m in expired:
        key = m["key"]
        purged_keys.append(key)
        if dry_run:
            continue
        delete_media(key)
        _unlink_media_file(key)

    cleared = 0 if dry_run else clear_visit_media_keys(set(purged_keys))
    return {
        "asOf": today,
        "dryRun": dry_run,
        "expiredCount": len(expired),
        "purgedKeys": purged_keys,
        "skippedLegalHoldCount": len(skipped_legal_hold),
        "skippedLegalHoldKeys": skipped_legal_hold,
        "visitKeysCleared": cleared,
        "processedAt": now_iso(),
    }


def retention_status(school_id: str, *, as_of_date: Optional[str] = None) -> dict:
    from datetime import date as date_cls

    today = as_of_date or date_cls.today().isoformat()
    by_kind: dict[str, int] = {}
    with_retain = 0
    expired = 0
    for m in list_media(school_id):
        kind = m.get("kind") or "other"
        by_kind[kind] = by_kind.get(kind, 0) + 1
        until = m.get("retainUntil")
        if until:
            with_retain += 1
            if until[:10] < today:
                expired += 1
    return {
        "asOf": today,
        "total": sum(by_kind.values()),
        "byKind": by_kind,
        "withRetainUntil": with_retain,
        "expired": expired,
    }


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


# --- DSR (data subject requests) ---


def put_dsr(rec: dict) -> dict:
    bucket = _state.setdefault("dsr_requests", {})
    bucket[rec["id"]] = rec
    return rec


def get_dsr(dsr_id: str) -> Optional[dict]:
    return (_state.get("dsr_requests") or {}).get(dsr_id)


def list_dsr(school_id: Optional[str] = None) -> list[dict]:
    items = list((_state.get("dsr_requests") or {}).values())
    if school_id:
        items = [d for d in items if d.get("schoolId") == school_id]
    items.sort(key=lambda d: d.get("createdAt") or "", reverse=True)
    return items



# --- students / authorized pickup / custody / pickups ---


def put_student(s: dict) -> None:
    _state["students"][s["id"]] = s


def get_student(student_id: str) -> Optional[dict]:
    return _state["students"].get(student_id)


def list_students(school_id: str) -> list[dict]:
    return [s for s in _state["students"].values() if s["schoolId"] == school_id]


def get_student_by_roster_id(school_id: str, roster_student_id: str):
    key = (roster_student_id or "").strip()
    if not key:
        return None
    for s in _state["students"].values():
        if s.get("schoolId") == school_id and s.get("studentId") == key:
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


def get_authorized_person_by_mobile(school_id: str, student_id: str, mobile: str):
    from app.util import normalize_mobile
    want = normalize_mobile(mobile)
    for p in _state["authorized_pickup"].values():
        if p.get("schoolId") != school_id or p.get("studentId") != student_id:
            continue
        try:
            if normalize_mobile(p.get("mobile") or "") == want:
                return p
        except Exception:
            continue
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


def add_notification(n: dict) -> dict:
    from app.util import now_iso
    n = dict(n)
    n.setdefault("id", f"NTF-{next_seq('notification_seq'):04d}")
    n.setdefault("createdAt", now_iso())
    n.setdefault("readAt", None)
    _state.setdefault("notifications", []).append(n)
    return n


def list_notifications(school_id: str, host_staff_id: Optional[str] = None, limit: int = 50) -> list[dict]:
    items = list(_state.get("notifications") or [])
    out = []
    for n in reversed(items):
        if n.get("schoolId") != school_id:
            continue
        if host_staff_id and n.get("hostId") and n.get("hostId") != host_staff_id:
            continue
        out.append(n)
        if len(out) >= limit:
            break
    return out


def mark_outbox(outbox_id: str, *, status: str, error: Optional[str] = None) -> None:
    from app.util import now_iso
    for o in _state.get("outbox") or []:
        if o.get("id") == outbox_id:
            o["status"] = status
            o["processedAt"] = now_iso()
            if error:
                o["error"] = error
            return


def process_pending_outbox(school_id: Optional[str] = None) -> dict:
    """Durable-ish consumer: pending outbox → in_app notifications; SMS/WA stub/HOLD."""
    from app.util import now_iso
    processed = sent = skipped = failed = 0
    for o in list(_state.get("outbox") or []):
        if o.get("status") != "pending":
            continue
        if school_id and o.get("schoolId") != school_id:
            continue
        processed += 1
        hints = o.get("channelHints") or ["in_app"]
        payload = o.get("payload") or {}
        visit_id = o.get("visitId")
        visit = get_visit(visit_id) if visit_id else None
        host_id = (visit or {}).get("hostId") or payload.get("hostId")
        try:
            if "in_app" in hints:
                add_notification(
                    {
                        "schoolId": o.get("schoolId"),
                        "hostId": host_id,
                        "visitId": visit_id,
                        "event": o.get("event"),
                        "channel": "in_app",
                        "title": o.get("event") or "visit",
                        "body": payload.get("visitorName") or "",
                        "payload": payload,
                        "status": "sent",
                    }
                )
                sent += 1
            # SMS / WA stubs — never live
            for ch in hints:
                if ch in ("sms", "whatsapp"):
                    skipped += 1
            mark_outbox(o["id"], status="sent")
        except Exception as e:
            failed += 1
            mark_outbox(o["id"], status="failed", error=str(e))
    return {"processed": processed, "sent": sent, "skipped": skipped, "failed": failed}
