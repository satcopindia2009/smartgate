"""Campus hours + holiday calendar (Priority P2 after-hours A1–A2). No zones/escort."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.after_hours import CAMPUS_TZ, format_hhmm, parse_date, validate_hours_week
from app.auth import CurrentUser, require_roles
from app.config import WATERMARK
from app.errors import AppError
from app.models import CampusHoursRow, HolidayCreate, Role
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
