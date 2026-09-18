"""School-scoped config — blast toggle (P2 E3) + ops school bootstrap."""
from __future__ import annotations

import re
import secrets
import string

from fastapi import APIRouter, Depends

from app.auth import require_roles
from app.blast import school_blast_config
from app.config import (
    BLAST_DEFAULT_STAFF_CHANNELS,
    BLAST_DEFAULT_VISITOR_CHANNELS,
    CAMPUS_TZ,
    WATERMARK,
)
from app.errors import AppError
from app.models import BlastChannel, BlastConfigPatch, Role, SchoolCreate
from app.util import now_iso
from app import store

router = APIRouter(prefix="/schools", tags=["schools"])

_BLAST_ROLES = (Role.admin, Role.security_head)
_ALLOWED_VISITOR = {BlastChannel.sms.value, BlastChannel.whatsapp.value}
_ALLOWED_STAFF = {BlastChannel.in_app.value, BlastChannel.push.value}

# Demo tenant must never be overwritten via POST /v1/schools
_PROTECTED_SCHOOL_IDS = {"SCH-DEMO-01"}
_PROTECTED_CODES = {"DEMO", "SCH-DEMO-01", "SCH_DEMO_01"}


def _config_public(school: dict) -> dict:
    out = school_blast_config(school)
    out["meta"] = {"watermark": WATERMARK}
    return out


def _validate_channels(values: list[str] | None, allowed: set[str], field: str) -> list[str] | None:
    if values is None:
        return None
    cleaned: list[str] = []
    for raw in values:
        ch = (raw or "").strip()
        if ch not in allowed:
            raise AppError(
                "VALIDATION",
                f"Unsupported {field} channel '{ch}'",
                400,
                {"allowed": sorted(allowed)},
            )
        if ch not in cleaned:
            cleaned.append(ch)
    if not cleaned:
        raise AppError("VALIDATION", f"{field} must not be empty", 400)
    return cleaned


def _slugify(raw: str) -> str:
    s = re.sub(r"[^A-Za-z0-9]+", "-", (raw or "").strip().upper()).strip("-")
    s = re.sub(r"-+", "-", s)
    if not s:
        raise AppError("VALIDATION", "schoolCode/name must yield a non-empty slug", 400)
    # Keep mint IDs readable: SCH-<SLUG>-01
    return s[:24]


def _mint_temp_password() -> str:
    alphabet = string.ascii_letters + string.digits
    return "Tmp@" + "".join(secrets.choice(alphabet) for _ in range(10))


def _school_public(school: dict) -> dict:
    return {
        "id": school["id"],
        "schoolCode": school.get("schoolCode"),
        "name": school["name"],
        "timezone": school.get("timezone") or CAMPUS_TZ,
        "overdueHoursDefault": school.get("overdueHoursDefault", 4),
        "emergencyBlastEnabled": bool(school.get("emergencyBlastEnabled", False)),
        "blastStaffLaneEnabled": bool(school.get("blastStaffLaneEnabled", False)),
        "meta": {"watermark": WATERMARK},
    }


def _default_hours(school_id: str, timezone: str, updated_by: str, ts: str) -> list[dict]:
    week = [
        ("mon", "08:00", "18:00", False, False),
        ("tue", "08:00", "18:00", False, False),
        ("wed", "08:00", "18:00", False, False),
        ("thu", "08:00", "18:00", False, False),
        ("fri", "08:00", "18:00", False, False),
        ("sat", None, None, True, False),
        ("sun", None, None, True, False),
    ]
    rows = []
    for weekday, open_t, close_t, closed, overnight in week:
        row = {
            "schoolId": school_id,
            "timezone": timezone or CAMPUS_TZ,
            "weekday": weekday,
            "openTime": open_t,
            "closeTime": close_t,
            "closed": closed,
            "overnight": overnight,
            "updatedByUserId": updated_by,
            "updatedAt": ts,
        }
        store.put_hours_row(row)
        rows.append(row)
    return rows


def _bootstrap_gates(school_id: str, prefix: str) -> list[dict]:
    gates = [
        (f"{prefix}-G-MAIN", "Main Gate"),
        (f"{prefix}-G-PED", "Pedestrian Gate"),
        (f"{prefix}-G-STAFF", "Staff Gate"),
        (f"{prefix}-G-BUS", "Bus Bay"),
    ]
    out = []
    for gid, name in gates:
        g = {"id": gid, "schoolId": school_id, "name": name, "active": True}
        store.put_gate(g)
        out.append(g)
    return out


@router.get("")
@router.get("/")
def list_schools_admin(user: dict = Depends(require_roles(Role.admin))):
    """List all schools — admin of any school (ops bootstrap helper)."""
    schools = store.list_schools() if hasattr(store, "list_schools") else []
    return {
        "items": [_school_public(s) for s in schools],
        "count": len(schools),
        "meta": {"watermark": WATERMARK},
    }


@router.post("", status_code=201)
@router.post("/", status_code=201)
def create_school(
    body: SchoolCreate,
    user: dict = Depends(require_roles(Role.admin)),
):
    """Create a school tenant + admin bootstrap.

    Prefer admin of any school (role check only). Mints SCH-<SLUG>-01,
    default 4 gates, Mon–Fri 08–18 hours, escort/zone defaults.
    Never overwrites SCH-DEMO-01.
    """
    name = (body.name or "").strip()
    if not name:
        raise AppError("VALIDATION", "name is required", 400)

    code_raw = (body.schoolCode or "").strip() or name
    slug = _slugify(code_raw)
    school_code = slug

    if school_code.upper() in _PROTECTED_CODES or slug in {"DEMO"}:
        raise AppError(
            "FORBIDDEN",
            "Cannot create or overwrite the protected demo school",
            403,
            {"schoolCode": school_code},
        )

    school_id = (body.schoolId or "").strip() or f"SCH-{slug}-01"
    if school_id in _PROTECTED_SCHOOL_IDS or school_id == "SCH-DEMO-01":
        raise AppError(
            "FORBIDDEN",
            "Cannot create or overwrite SCH-DEMO-01",
            403,
            {"schoolId": school_id},
        )

    existing = store.get_school(school_id) if hasattr(store, "get_school") else None
    if existing:
        raise AppError(
            "CONFLICT",
            f"School {school_id} already exists",
            409,
            {"schoolId": school_id, "name": existing.get("name")},
        )

    # Gate / staff id prefix — short uppercase slug (Pranay uses PS-)
    prefix = slug[:8] if len(slug) > 2 else slug

    admin_username = (body.adminUsername or "").strip() or f"{slug.lower()}.admin"
    if store.get_user_by_username(admin_username):
        raise AppError(
            "CONFLICT",
            f"Username '{admin_username}' already taken",
            409,
            {"adminUsername": admin_username},
        )

    temp_password = (body.adminPassword or "").strip() or _mint_temp_password()
    admin_display = (body.adminDisplayName or "").strip() or f"{name} Admin"
    tz = (body.timezone or CAMPUS_TZ).strip() or CAMPUS_TZ
    ts = now_iso()

    admin_staff_id = f"{prefix}-H02"
    admin_user_id = f"U-{prefix}-ADMIN"

    school_row = {
        "id": school_id,
        "schoolCode": school_code,
        "name": name,
        "timezone": tz,
        "overdueHoursDefault": 4,
        "config": {
            "hostNotifyChannels": ["in_app"],
            "defaultHostStaffId": admin_staff_id,
        },
        # New schools default OFF (AC-E3e) — demo stays ON via seed only
        "emergencyBlastEnabled": False,
        "blastStaffLaneEnabled": False,
        "blastChannelsVisitor": list(BLAST_DEFAULT_VISITOR_CHANNELS),
        "blastChannelsStaff": list(BLAST_DEFAULT_STAFF_CHANNELS),
        "createdAt": ts,
        "createdByUserId": user["id"],
    }
    store.put_school(school_row)

    gates = _bootstrap_gates(school_id, prefix)

    store.put_staff(
        {
            "id": admin_staff_id,
            "schoolId": school_id,
            "name": admin_display,
            "roleTitle": "Office Admin",
            "mobile": None,
            "userId": admin_user_id,
            "active": True,
        }
    )

    admin_user = {
        "id": admin_user_id,
        "username": admin_username,
        "password": temp_password,
        "schoolId": school_id,
        "role": "admin",
        "staffId": admin_staff_id,
        "gateIds": None,
        "displayName": admin_display,
        "phone": None,
        "email": f"{admin_username}@school.local",
        "active": True,
        "mustChangePassword": True,
        "createdAt": ts,
    }
    store.put_user(admin_user)

    hours = _default_hours(school_id, tz, admin_user_id, ts)

    try:
        from app.escort import seed_school_defaults

        seed_school_defaults(school_id, updated_by=admin_user_id)
        zones_seeded = True
    except Exception:
        zones_seeded = False

    # Register school_code → schoolId for roster import when map is mutable
    try:
        from app import roster_sot_import as roster

        if hasattr(roster, "SCHOOL_CODE_MAP") and isinstance(roster.SCHOOL_CODE_MAP, dict):
            roster.SCHOOL_CODE_MAP[school_code.upper()] = school_id
    except Exception:
        pass

    return {
        "school": _school_public(school_row),
        "admin": {
            "userId": admin_user_id,
            "username": admin_username,
            "tempPassword": temp_password,
            "displayName": admin_display,
            "staffId": admin_staff_id,
            "mustChangePassword": True,
        },
        "gates": [{"id": g["id"], "name": g["name"]} for g in gates],
        "hours": {
            "timezone": tz,
            "weekdays": "Mon-Fri 08:00-18:00",
            "rowsSeeded": len(hours),
        },
        "zonesSeeded": zones_seeded,
        "meta": {"watermark": WATERMARK},
    }


@router.get("/me/blast-config")
def get_blast_config(user: dict = Depends(require_roles(*_BLAST_ROLES))):
    school = store.get_school(user["schoolId"]) if hasattr(store, "get_school") else store.school()
    if not school or school.get("id") != user["schoolId"]:
        raise AppError("NOT_FOUND", "School not found", 404)
    return _config_public(school)


@router.put("/me/blast-config")
def put_blast_config(
    body: BlastConfigPatch,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    return patch_blast_config(body, user)


@router.patch("/me/blast-config")
def patch_blast_config(
    body: BlastConfigPatch,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    school = store.get_school(user["schoolId"]) if hasattr(store, "get_school") else store.school()
    if not school or school.get("id") != user["schoolId"]:
        raise AppError("NOT_FOUND", "School not found", 404)
    visitor = _validate_channels(
        body.blastChannelsVisitor, _ALLOWED_VISITOR, "blastChannelsVisitor"
    )
    staff = _validate_channels(
        body.blastChannelsStaff, _ALLOWED_STAFF, "blastChannelsStaff"
    )
    if body.emergencyBlastEnabled is not None:
        school["emergencyBlastEnabled"] = bool(body.emergencyBlastEnabled)
    if body.blastStaffLaneEnabled is not None:
        school["blastStaffLaneEnabled"] = bool(body.blastStaffLaneEnabled)
    if visitor is not None:
        school["blastChannelsVisitor"] = visitor
    elif "blastChannelsVisitor" not in school:
        school["blastChannelsVisitor"] = list(BLAST_DEFAULT_VISITOR_CHANNELS)
    if staff is not None:
        school["blastChannelsStaff"] = staff
    elif "blastChannelsStaff" not in school:
        school["blastChannelsStaff"] = list(BLAST_DEFAULT_STAFF_CHANNELS)
    school["blastConfigUpdatedByUserId"] = user["id"]
    school["blastConfigUpdatedAt"] = now_iso()
    if hasattr(store, "put_school"):
        store.put_school(school)
    else:
        store.set_school(school)
    return _config_public(school)
