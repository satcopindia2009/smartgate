"""Pickup list match (P5) + custody decision rules (P4 + H1)."""
from __future__ import annotations

from datetime import date
from typing import Optional

from app.util import last4, normalize_id, normalize_mobile
from app import store


def is_person_in_date(person: dict, today: Optional[date] = None) -> bool:
    today = today or date.today()
    start = person.get("effectiveFrom")
    end = person.get("effectiveTo")
    try:
        if start and date.fromisoformat(start) > today:
            return False
        if end and date.fromisoformat(end) < today:
            return False
    except ValueError:
        return False
    return True


def is_list_eligible(person: dict, today: Optional[date] = None) -> bool:
    return bool(person.get("active")) and is_person_in_date(person, today)


def _id_matches(
    person: dict,
    id_type: Optional[str],
    id_number: Optional[str],
    id_last4: Optional[str],
) -> bool:
    if not id_type or person.get("idType") != id_type:
        return False
    claimed = normalize_id(id_number)
    claimed_last4 = last4(id_last4) or last4(id_number)
    person_id = normalize_id(person.get("idNumber"))
    person_last4 = last4(person.get("idLast4")) or last4(person.get("idNumber"))
    if claimed and person_id and claimed == person_id:
        return True
    if claimed_last4 and person_last4 and claimed_last4 == person_last4:
        return True
    return False


def match_authorized_person(
    school_id: str,
    student_id: str,
    *,
    person_id: Optional[str] = None,
    mobile: Optional[str] = None,
    id_type: Optional[str] = None,
    id_number: Optional[str] = None,
    id_last4: Optional[str] = None,
) -> tuple[Optional[dict], Optional[str]]:
    """Match active + in-date list row. Name-only never auto-matches (P5)."""
    people = store.list_authorized_people(school_id, student_id)

    if person_id:
        person = store.get_authorized_person(person_id)
        if (
            person
            and person.get("schoolId") == school_id
            and person.get("studentId") == student_id
            and is_list_eligible(person)
        ):
            return person, "manual_list_select"
        return None, None

    nm = normalize_mobile(mobile)
    mobile_hit = None
    id_hit = None
    for person in people:
        if not is_list_eligible(person):
            continue
        if nm and normalize_mobile(person.get("mobile")) == nm:
            mobile_hit = person
        if _id_matches(person, id_type, id_number, id_last4):
            id_hit = person

    if mobile_hit:
        return mobile_hit, "mobile"
    if id_hit:
        return id_hit, "id"
    return None, None


def default_custody_flag(student_id: str, school_id: str) -> dict:
    return {
        "studentId": student_id,
        "schoolId": school_id,
        "flag": "none",
        "gateInstruction": "",
        "blockedPersonIds": [],
        "allowedPersonIds": None,
        "updatedByUserId": None,
        "updatedAt": None,
    }


def get_or_default_flag(student: dict) -> dict:
    flag = store.get_custody_flag(student["id"])
    if flag:
        return flag
    return default_custody_flag(student["id"], student["schoolId"])


def evaluate_release(person: Optional[dict], flag: dict) -> str:
    """Return PickupEvent status after list + custody rules. Consent/photo checked by router."""
    if not person or not is_list_eligible(person):
        return "BlockedNotAuthorized"

    flag_val = flag.get("flag") or "none"
    instruction = (flag.get("gateInstruction") or "").strip()
    if flag_val == "court_order" and not instruction:
        return "BlockedCustody"

    blocked = flag.get("blockedPersonIds") or []
    if flag_val in ("restricted", "court_order") and person["id"] in blocked:
        return "BlockedCustody"

    allowed = flag.get("allowedPersonIds")
    if allowed is not None and person["id"] not in allowed:
        return "BlockedCustody"

    return "Released"


def denormalize_blocked_by_custody(student_id: str, school_id: str) -> None:
    flag = store.get_custody_flag(student_id) or default_custody_flag(student_id, school_id)
    blocked = set(flag.get("blockedPersonIds") or [])
    allowed = flag.get("allowedPersonIds")
    flag_val = flag.get("flag") or "none"
    fail_closed = flag_val == "court_order" and not (flag.get("gateInstruction") or "").strip()
    for person in store.list_authorized_people(school_id, student_id):
        by_block = flag_val in ("restricted", "court_order") and person["id"] in blocked
        by_allow = allowed is not None and person["id"] not in allowed
        person["blockedByCustody"] = bool(fail_closed or by_block or by_allow)
        store.put_authorized_person(person)


def gate_prompt(status: str, flag: dict, student_name: str, collector_name: str, relation: str | None) -> str:
    if status == "BlockedNotAuthorized":
        return (
            "This person is not on the authorized pickup list. "
            "Do not release. Ask Security Head."
        )
    if status == "BlockedCustody":
        instruction = (flag.get("gateInstruction") or "").strip() or "restriction in force"
        return f"Custody restriction: {instruction}. Release blocked. Security Head alerted."
    if status in ("Released", "ReleasedWithOverride"):
        rel = f" ({relation})" if relation else ""
        return f"Release logged for {student_name} → {collector_name}{rel}."
    if status == "Matching":
        return "Waiting for Security Head override…"
    return ""
