"""Create a new school tenant without touching SCH-DEMO-01 demo seed."""
from __future__ import annotations

import re
import secrets
from typing import Optional

from app.config import (
    BLAST_DEFAULT_STAFF_CHANNELS,
    BLAST_DEFAULT_VISITOR_CHANNELS,
    CAMPUS_TZ,
    DEFAULT_GATES,
    DEFAULT_NEW_SCHOOL_TZ,
    RESERVED_SCHOOL_ID,
)
from app.errors import AppError
from app.escort import seed_school_defaults
from app.util import now_iso
from app import store

_SLUG_RE = re.compile(r"[^a-z0-9-]+")
_RESERVED = RESERVED_SCHOOL_ID.upper()

DEFAULT_WEEK_HOURS = (
    ("mon", "08:00", "18:00", False),
    ("tue", "08:00", "18:00", False),
    ("wed", "08:00", "18:00", False),
    ("thu", "08:00", "18:00", False),
    ("fri", "08:00", "18:00", False),
    ("sat", None, None, True),
    ("sun", None, None, True),
)


def normalize_slug(raw: Optional[str]) -> Optional[str]:
    if raw is None:
        return None
    s = str(raw).strip().lower()
    if not s:
        return None
    s = _SLUG_RE.sub("-", s)
    s = re.sub(r"-{2,}", "-", s).strip("-")
    if len(s) < 2:
        raise AppError("VALIDATION", "slug must be at least 2 alphanumeric characters", 400)
    if len(s) > 32:
        raise AppError("VALIDATION", "slug must be at most 32 characters", 400)
    return s


def school_id_from_slug(slug: str) -> str:
    return "SCH-" + slug.upper()


def is_reserved_school_id(school_id: str) -> bool:
    return (school_id or "").strip().upper() == _RESERVED


def next_school_id() -> str:
    while True:
        sid = f"SCH-{store.next_seq('school_seq'):04d}"
        if is_reserved_school_id(sid):
            continue
        if not store.get_school(sid):
            return sid


def generate_password() -> str:
    return secrets.token_urlsafe(9)


def unique_username(prefix: str, school_id: str) -> str:
    base = f"{prefix}-{school_id.lower()}"
    if not store.get_user_by_username(base):
        return base
    n = 2
    while store.get_user_by_username(f"{base}-{n}"):
        n += 1
    return f"{base}-{n}"


def public_school(school: dict, *, include_hours: bool = True) -> dict:
    sid = school["id"]
    gates = store.list_gates(sid)
    gates.sort(key=lambda g: g["id"])
    out = {
        "schoolId": sid,
        "id": sid,
        "name": school["name"],
        "timezone": school.get("timezone") or DEFAULT_NEW_SCHOOL_TZ,
        "slug": school.get("slug"),
        "overdueHoursDefault": school.get("overdueHoursDefault", 4),
        "emergencyBlastEnabled": bool(school.get("emergencyBlastEnabled", False)),
        "blastStaffLaneEnabled": bool(school.get("blastStaffLaneEnabled", False)),
        "gates": [
            {
                "id": g["id"],
                "schoolId": g["schoolId"],
                "name": g["name"],
                "active": bool(g.get("active", True)),
            }
            for g in gates
        ],
    }
    if include_hours:
        out["hours"] = store.list_hours(sid)
        out["holidayCount"] = len(store.list_holidays(sid))
    return out


def create_school(
    *,
    name: str,
    timezone: Optional[str] = None,
    slug: Optional[str] = None,
    admin_password: Optional[str] = None,
    security_head_password: Optional[str] = None,
    created_by_user_id: Optional[str] = None,
) -> dict:
    name = (name or "").strip()
    if not name:
        raise AppError("VALIDATION", "name is required", 400)
    tz = (timezone or DEFAULT_NEW_SCHOOL_TZ).strip() or DEFAULT_NEW_SCHOOL_TZ
    slug_n = normalize_slug(slug)
    if slug_n:
        school_id = school_id_from_slug(slug_n)
    else:
        school_id = next_school_id()

    if is_reserved_school_id(school_id):
        raise AppError(
            "VALIDATION",
            f"schoolId {RESERVED_SCHOOL_ID} is reserved for the demo seed and cannot be created or overwritten",
            400,
            {"reservedSchoolId": RESERVED_SCHOOL_ID},
        )
    if store.get_school(school_id):
        raise AppError(
            "VALIDATION",
            f"schoolId {school_id} already exists",
            409,
            {"schoolId": school_id},
        )

    ts = now_iso()
    admin_pw = (admin_password or "").strip() or generate_password()
    sh_pw = (security_head_password or "").strip() or generate_password()
    admin_user = unique_username("admin", school_id)
    sh_user = unique_username("security", school_id)
    admin_uid = f"{school_id}-U-ADMIN"
    sh_uid = f"{school_id}-U-SH"
    admin_staff_id = f"{school_id}-ADM"
    sh_staff_id = f"{school_id}-SH"

    school = {
        "id": school_id,
        "name": name,
        "timezone": tz,
        "slug": slug_n,
        "overdueHoursDefault": 4,
        "config": {"hostNotifyChannels": ["in_app"]},
        "emergencyBlastEnabled": False,
        "blastStaffLaneEnabled": False,
        "blastChannelsVisitor": list(BLAST_DEFAULT_VISITOR_CHANNELS),
        "blastChannelsStaff": list(BLAST_DEFAULT_STAFF_CHANNELS),
        "blastConfigUpdatedByUserId": created_by_user_id,
        "blastConfigUpdatedAt": ts,
        "createdAt": ts,
        "createdByUserId": created_by_user_id,
    }
    store.put_school(school)

    for gid, gname in DEFAULT_GATES:
        store.put_gate(
            {"id": gid, "schoolId": school_id, "name": gname, "active": True}
        )

    hours_tz = CAMPUS_TZ if tz in (CAMPUS_TZ, "Asia/Calcutta") else tz
    for weekday, open_t, close_t, closed in DEFAULT_WEEK_HOURS:
        store.put_hours_row(
            {
                "schoolId": school_id,
                "timezone": hours_tz,
                "weekday": weekday,
                "openTime": open_t,
                "closeTime": close_t,
                "closed": closed,
                "overnight": False,
                "updatedByUserId": created_by_user_id or admin_uid,
                "updatedAt": ts,
            }
        )

    seed_school_defaults(school_id, updated_by=created_by_user_id or admin_uid)

    store.put_staff(
        {
            "id": admin_staff_id,
            "schoolId": school_id,
            "name": "Office Admin",
            "roleTitle": "Admin Officer",
            "mobile": None,
            "userId": admin_uid,
            "active": True,
        }
    )
    store.put_staff(
        {
            "id": sh_staff_id,
            "schoolId": school_id,
            "name": "Security Head",
            "roleTitle": "Security Head",
            "mobile": None,
            "userId": sh_uid,
            "active": True,
        }
    )
    store.put_user(
        {
            "id": admin_uid,
            "username": admin_user,
            "password": admin_pw,
            "schoolId": school_id,
            "role": "admin",
            "staffId": admin_staff_id,
            "gateIds": None,
            "displayName": "Office Admin",
            "phone": None,
            "email": f"{admin_user}@school.local",
            "active": True,
        }
    )
    store.put_user(
        {
            "id": sh_uid,
            "username": sh_user,
            "password": sh_pw,
            "schoolId": school_id,
            "role": "security_head",
            "staffId": sh_staff_id,
            "gateIds": None,
            "displayName": "Security Head",
            "phone": None,
            "email": f"{sh_user}@school.local",
            "active": True,
        }
    )

    out = public_school(school)
    out["credentials"] = {
        "admin": {
            "username": admin_user,
            "password": admin_pw,
            "userId": admin_uid,
            "role": "admin",
        },
        "security_head": {
            "username": sh_user,
            "password": sh_pw,
            "userId": sh_uid,
            "role": "security_head",
        },
    }
    return out
