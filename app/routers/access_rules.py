"""Campus hours + holiday calendar (A1–A2) and escort rules (B4)."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.after_hours import CAMPUS_TZ, format_hhmm, parse_date, validate_hours_week
from app.auth import CurrentUser, require_roles
from app.config import WATERMARK
from app.escort import (
    VISITOR_TYPES,
    apply_restricted_force,
    normalize_zones,
    seed_school_defaults,
)
from app.errors import AppError
from app.models import CampusHoursRow, EscortZoneRuleIn, HolidayCreate, Role
from app.util import now_iso
from app import store

router = APIRouter(prefix="/access-rules", tags=["access-rules"])

_WRITE = (Role.admin, Role.security_head)


def _meta_row(row: dict) -> dict:
    out = dict(row)
    out["meta"] = {"watermark": WATERMARK}
    return out


def _hours_public(row: dict) -> dict:
    return {
        "schoolId": row["schoolId"],
        "timezone": row.get("timezone") or CAMPUS_TZ,
        "weekday": row["weekday"],
        "openTime": row.get("openTime"),
        "closeTime": row.get("closeTime"),
        "closed": bool(row.get("closed")),
        "overnight": bool(row.get("overnight")),
        "updatedByUserId": row.get("updatedByUserId"),
        "updatedAt": row.get("updatedAt"),
    }


@router.get("/hours")
def get_hours(user: CurrentUser):
    rows = [_hours_public(r) for r in store.list_hours(user["schoolId"])]
    return {"data": rows, "meta": {"watermark": WATERMARK, "timezone": CAMPUS_TZ}}


@router.put("/hours")
def put_hours(
    body: list[CampusHoursRow],
    user: dict = Depends(require_roles(*_WRITE)),
):
    raw = []
    for item in body:
        raw.append(
            {
                "schoolId": user["schoolId"],
                "timezone": CAMPUS_TZ,
                "weekday": item.weekday.value,
                "openTime": format_hhmm(item.openTime),
                "closeTime": format_hhmm(item.closeTime),
                "closed": bool(item.closed),
                "overnight": bool(item.overnight),
                "updatedByUserId": user["id"],
                "updatedAt": now_iso(),
            }
        )
    errors = validate_hours_week(raw)
    if errors:
        raise AppError("VALIDATION", errors[0], 400, {"errors": errors})
    stored = store.replace_hours(user["schoolId"], raw)
    return {
        "data": [_hours_public(r) for r in stored],
        "meta": {"watermark": WATERMARK, "timezone": CAMPUS_TZ},
    }


@router.get("/holidays")
def list_holidays(
    user: CurrentUser,
    dateFrom: Optional[str] = Query(default=None, alias="from"),
    dateTo: Optional[str] = Query(default=None, alias="to"),
):
    for label, value in (("from", dateFrom), ("to", dateTo)):
        if value:
            try:
                parse_date(value)
            except ValueError as e:
                raise AppError("VALIDATION", f"Invalid {label} date", 400) from e
    rows = store.list_holidays(user["schoolId"], dateFrom, dateTo)
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.post("/holidays")
def create_holiday(
    body: HolidayCreate,
    user: dict = Depends(require_roles(*_WRITE)),
):
    existing = store.holiday_on_date(user["schoolId"], body.date)
    if existing:
        raise AppError(
            "VALIDATION",
            f"Holiday already exists on {body.date}",
            400,
            {"id": existing["id"]},
        )
    ts = now_iso()
    hid = f"HOL-{store.next_seq('holiday_seq'):04d}"
    row = {
        "id": hid,
        "schoolId": user["schoolId"],
        "date": body.date,
        "label": body.label,
        "createdByUserId": user["id"],
        "updatedByUserId": user["id"],
        "createdAt": ts,
        "updatedAt": ts,
    }
    store.put_holiday(row)
    return _meta_row(row)


@router.delete("/holidays/{holiday_id}")
def delete_holiday(
    holiday_id: str,
    user: dict = Depends(require_roles(*_WRITE)),
):
    existing = store.get_holiday(holiday_id)
    if not existing or existing.get("schoolId") != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Holiday {holiday_id} not found", 404)
    store.delete_holiday(holiday_id)
    return {"deleted": True, "id": holiday_id, "meta": {"watermark": WATERMARK}}


def _escort_public(row: dict) -> dict:
    return {
        "schoolId": row["schoolId"],
        "visitorType": row["visitorType"],
        "escortRequired": bool(row.get("escortRequired")),
        "allowedZones": list(row.get("allowedZones") or []),
        "updatedByUserId": row.get("updatedByUserId"),
        "updatedAt": row.get("updatedAt"),
    }


@router.get("/escort")
def get_escort_rules(user: CurrentUser):
    seed_school_defaults(user["schoolId"])
    rows = [_escort_public(r) for r in store.list_escort_rules(user["schoolId"])]
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.put("/escort")
def put_escort_rules(
    body: list[EscortZoneRuleIn],
    user: dict = Depends(require_roles(*_WRITE)),
):
    if not body:
        raise AppError("VALIDATION", "At least one escort rule is required", 400)
    seed_school_defaults(user["schoolId"])
    ts = now_iso()
    seen: list[str] = []
    for item in body:
        vt = item.visitorType.value
        if vt in seen:
            raise AppError("VALIDATION", f"duplicate visitorType {vt}", 400)
        if vt not in VISITOR_TYPES:
            raise AppError("VALIDATION", f"Unknown visitorType {vt}", 400)
        seen.append(vt)
        zones = normalize_zones([z.value for z in item.allowedZones])
        required = apply_restricted_force(item.escortRequired, zones)
        store.put_escort_rule(
            {
                "schoolId": user["schoolId"],
                "visitorType": vt,
                "escortRequired": required,
                "allowedZones": zones,
                "updatedByUserId": user["id"],
                "updatedAt": ts,
            }
        )
    rows = [_escort_public(r) for r in store.list_escort_rules(user["schoolId"])]
    return {"data": rows, "meta": {"watermark": WATERMARK}}
