"""Escort / zone rules (Hub B4, AC-B4). Stamp at visit create; no geo-fence."""
from __future__ import annotations

from typing import Optional

from app.errors import AppError
from app import store

ZONE_KEYS = (
    "reception",
    "admin",
    "classroom",
    "sports",
    "lab",
    "restricted",
    "parking",
)

DEFAULT_ZONE_LABELS = {
    "reception": "Reception / Lobby",
    "admin": "Admin block",
    "classroom": "Classroom wing",
    "sports": "Sports / playground",
    "lab": "Labs / IT",
    "restricted": "Restricted (principal / accounts / stores / server)",
    "parking": "Bus bay / parking",
}

VISITOR_TYPES = ("Parent", "Vendor", "Guest", "Official", "Alumni")

# Hub-LOCKED new-school defaults
DEFAULT_ESCORT_RULES: dict[str, dict] = {
    "Vendor": {"escortRequired": True, "allowedZones": ["reception", "admin"]},
    "Parent": {"escortRequired": False, "allowedZones": ["reception"]},
    "Guest": {"escortRequired": False, "allowedZones": ["reception"]},
    "Official": {"escortRequired": False, "allowedZones": ["reception", "admin"]},
    "Alumni": {"escortRequired": False, "allowedZones": ["reception"]},
}

CONTRACTOR_ALIASES = frozenset({"contractor", "Contractor"})


def map_visitor_type(raw: Optional[str]) -> str:
    """Contractor maps to Vendor rules (B4-E6)."""
    if raw is None:
        return "Guest"
    text = str(raw).strip()
    if text.lower() == "contractor":
        return "Vendor"
    return text


def normalize_zones(keys: list) -> list[str]:
    seen: list[str] = []
    unknown: list[str] = []
    for item in keys or []:
        key = item.value if hasattr(item, "value") else str(item).strip()
        if key not in ZONE_KEYS:
            unknown.append(key)
            continue
        if key not in seen:
            seen.append(key)
    if unknown:
        raise AppError(
            "VALIDATION",
            f"Unknown zone key(s): {', '.join(unknown)} — starter enum is fixed",
            400,
            {"unknown": unknown, "allowed": list(ZONE_KEYS)},
        )
    return seen


def apply_restricted_force(escort_required: bool, allowed_zones: list[str]) -> bool:
    if "restricted" in allowed_zones:
        return True
    return bool(escort_required)


def default_rule(visitor_type: str) -> dict:
    vt = map_visitor_type(visitor_type)
    base = DEFAULT_ESCORT_RULES.get(vt) or DEFAULT_ESCORT_RULES["Guest"]
    zones = list(base["allowedZones"])
    return {
        "visitorType": vt,
        "escortRequired": apply_restricted_force(base["escortRequired"], zones),
        "allowedZones": zones,
    }


def get_rule(school_id: str, visitor_type: str) -> dict:
    vt = map_visitor_type(visitor_type)
    row = store.get_escort_rule(school_id, vt)
    if not row:
        rule = default_rule(vt)
        rule["schoolId"] = school_id
        return rule
    zones = normalize_zones(row.get("allowedZones") or [])
    return {
        "schoolId": school_id,
        "visitorType": vt,
        "escortRequired": apply_restricted_force(row.get("escortRequired"), zones),
        "allowedZones": zones,
        "updatedByUserId": row.get("updatedByUserId"),
        "updatedAt": row.get("updatedAt"),
    }


def stamp_escort_zones(visit: dict) -> dict:
    """Stamp sticky escortRequired + allowedZones from EscortZoneRule (by visitorType)."""
    vt = map_visitor_type(visit.get("visitorType"))
    visit["visitorType"] = vt
    rule = get_rule(visit["schoolId"], vt)
    visit["escortRequired"] = bool(rule["escortRequired"])
    visit["allowedZones"] = list(rule["allowedZones"])
    visit.setdefault("escortStaffId", None)
    visit.setdefault("escortSuggestedByHost", None)
    visit.setdefault("escortWaived", False)
    visit.setdefault("escortWaiveReason", None)
    visit.setdefault("escortClearedAt", None)
    return visit


def escort_name(visit: dict) -> Optional[str]:
    sid = visit.get("escortStaffId")
    if not sid:
        return None
    staff = store.get_staff(sid)
    return staff["name"] if staff else None


def allowed_zone_labels(school_id: str, keys: list[str]) -> list[str]:
    labels: list[str] = []
    for key in keys or []:
        row = store.get_zone(school_id, key)
        labels.append(row["label"] if row else DEFAULT_ZONE_LABELS.get(key, key))
    return labels


def escort_blocks_pass(visit: dict) -> bool:
    """AC-B4e: escortRequired && !escortStaffId && !escortWaived."""
    return bool(
        visit.get("escortRequired")
        and not visit.get("escortStaffId")
        and not visit.get("escortWaived")
    )


def assert_escort_ready(visit: dict, action: str = "check-in") -> None:
    if escort_blocks_pass(visit):
        raise AppError(
            "ESCORT_REQUIRED",
            f"Escort required — assign escort staff before {action}",
            403,
            {
                "escortRequired": True,
                "escortStaffId": visit.get("escortStaffId"),
                "escortWaived": bool(visit.get("escortWaived")),
                "allowedZones": list(visit.get("allowedZones") or []),
            },
        )


def seed_school_defaults(school_id: str, updated_by: Optional[str] = None) -> None:
    """Idempotent starter zones + escort rules for a school."""
    ts = None
    for key, label in DEFAULT_ZONE_LABELS.items():
        existing = store.get_zone(school_id, key)
        if existing:
            continue
        store.put_zone(
            {
                "key": key,
                "label": label,
                "schoolId": school_id,
                "updatedByUserId": updated_by,
                "updatedAt": ts,
            }
        )
    for vt in VISITOR_TYPES:
        existing = store.get_escort_rule(school_id, vt)
        if existing:
            continue
        rule = default_rule(vt)
        store.put_escort_rule(
            {
                "schoolId": school_id,
                "visitorType": vt,
                "escortRequired": rule["escortRequired"],
                "allowedZones": rule["allowedZones"],
                "updatedByUserId": updated_by,
                "updatedAt": ts,
            }
        )
