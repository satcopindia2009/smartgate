from __future__ import annotations

import csv
import io
from datetime import date, timedelta
from typing import Optional

from fastapi import APIRouter, Depends
from fastapi.responses import PlainTextResponse

from app.auth import require_roles
from app.config import WATERMARK
from app.errors import AppError
from app.models import ExportRequest, Role
from app.util import last4, now_iso
from app import store

router = APIRouter(prefix="/exports", tags=["exports"])


def _mask_id_number(value: Optional[str]) -> str:
    if not value:
        return ""
    masked = last4(value)
    return masked or ""


def _history_span_days(filters: dict) -> Optional[int]:
    """Return requested history window in days if filters imply a range."""
    if not filters:
        return None
    if "days" in filters:
        try:
            return int(filters["days"])
        except (TypeError, ValueError):
            return None
    start = filters.get("from") or filters.get("fromDate") or filters.get("start")
    end = filters.get("to") or filters.get("toDate") or filters.get("end")
    if start and end:
        try:
            d0 = date.fromisoformat(str(start)[:10])
            d1 = date.fromisoformat(str(end)[:10])
            return abs((d1 - d0).days) + 1
        except ValueError:
            return None
    if start:
        try:
            d0 = date.fromisoformat(str(start)[:10])
            return (date.today() - d0).days + 1
        except ValueError:
            return None
    return None


def _visit_in_history_filter(v: dict, filters: dict) -> bool:
    if not filters:
        return True
    start = filters.get("from") or filters.get("fromDate") or filters.get("start")
    end = filters.get("to") or filters.get("toDate") or filters.get("end")
    days = filters.get("days")
    created = (v.get("createdAt") or v.get("timeIn") or "")[:10]
    if not created:
        return True
    try:
        d = date.fromisoformat(created)
    except ValueError:
        return True
    if days is not None:
        try:
            n = int(days)
            return d >= date.today() - timedelta(days=n)
        except (TypeError, ValueError):
            pass
    if start:
        try:
            if d < date.fromisoformat(str(start)[:10]):
                return False
        except ValueError:
            pass
    if end:
        try:
            if d > date.fromisoformat(str(end)[:10]):
                return False
        except ValueError:
            pass
    return True


@router.post("")
def create_export(
    body: ExportRequest,
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    purpose = (body.purpose or "").strip() or None
    if body.scope == "pickup_lists" and not purpose:
        raise AppError(
            "VALIDATION",
            "purpose is required for bulk pickup list export (AC-D9)",
            400,
        )

    unmask = bool(body.unmask)
    history_days = _history_span_days(body.filters) if body.scope == "history" else None

    if unmask:
        if user["role"] not in (Role.security_head.value, "security_head"):
            raise AppError(
                "FORBIDDEN",
                "unmask=true requires security_head role",
                403,
            )
        if not purpose:
            raise AppError(
                "VALIDATION",
                "purpose is required when unmask=true",
                400,
            )

    if body.scope == "history" and history_days is not None and history_days > 30:
        if not purpose:
            raise AppError(
                "VALIDATION",
                "purpose is required for history export spanning more than 30 days",
                400,
            )

    ts = now_iso()
    audit = {
        "who": user["id"],
        "displayName": user["displayName"],
        "when": ts,
        "scope": body.scope,
        "filters": body.filters,
        "purpose": purpose,
        "unmask": unmask,
        "schoolId": user["schoolId"],
    }
    store.add_export(audit)

    buf = io.StringIO()
    writer = csv.writer(buf)
    row_count = 0

    if body.scope == "blacklist":
        writer.writerow(["id", "name", "mobile", "severity", "active", "reason"])
        for b in store.list_blacklist(user["schoolId"]):
            writer.writerow(
                [b["id"], b["name"], b.get("mobile"), b["severity"], b["active"], b["reason"]]
            )
            row_count += 1
    elif body.scope == "inside":
        writer.writerow(
            ["visitId", "visitorName", "mobile", "passId", "gateId", "timeIn", "hostId"]
        )
        for v in store.list_visits(user["schoolId"]):
            if v["status"] == "inside":
                writer.writerow(
                    [
                        v["id"],
                        v["visitorName"],
                        v["mobile"],
                        v.get("passId"),
                        v.get("gateInId"),
                        v.get("timeIn"),
                        v["hostId"],
                    ]
                )
                row_count += 1
    elif body.scope == "daily_gate_summary":
        writer.writerow(["gate", "note"])
        for g in store.list_gates(user["schoolId"]):
            writer.writerow([g["name"], "see /v1/reports/today-by-gate"])
            row_count += 1
    elif body.scope == "pickup_events":
        writer.writerow(
            [
                "pickupId",
                "studentId",
                "studentName",
                "collectorName",
                "collectorMobile",
                "relation",
                "matchMethod",
                "gateId",
                "status",
                "override",
                "overrideByUserId",
                "overrideReason",
                "attemptedAt",
                "releasedAt",
                "collectorLivePhotoRef",
            ]
        )
        for p in store.list_pickups(user["schoolId"]):
            student = store.get_student(p.get("studentId") or "")
            person = (
                store.get_authorized_person(p["collectorPickupPersonId"])
                if p.get("collectorPickupPersonId")
                else None
            )
            writer.writerow(
                [
                    p["id"],
                    p.get("studentId"),
                    student["name"] if student else "",
                    p.get("collectorName"),
                    p.get("collectorMobile"),
                    person.get("relation") if person else "",
                    p.get("matchMethod"),
                    p.get("gateId"),
                    p.get("status"),
                    p.get("override"),
                    p.get("overrideByUserId"),
                    p.get("overrideReason"),
                    p.get("attemptedAt"),
                    p.get("releasedAt"),
                    p.get("collectorLivePhotoRef"),
                ]
            )
            row_count += 1
    elif body.scope == "pickup_lists":
        writer.writerow(
            [
                "studentId",
                "studentName",
                "class",
                "section",
                "personId",
                "name",
                "relation",
                "mobile",
                "active",
                "effectiveFrom",
                "effectiveTo",
                "blockedByCustody",
            ]
        )
        for student in store.list_students(user["schoolId"]):
            for person in store.list_authorized_people(user["schoolId"], student["id"]):
                writer.writerow(
                    [
                        student["id"],
                        student["name"],
                        student.get("class"),
                        student.get("section"),
                        person["id"],
                        person["name"],
                        person.get("relation"),
                        person.get("mobile"),
                        person.get("active"),
                        person.get("effectiveFrom"),
                        person.get("effectiveTo"),
                        person.get("blockedByCustody"),
                    ]
                )
                row_count += 1
    else:  # history
        writer.writerow(
            [
                "visitId",
                "visitorName",
                "mobile",
                "status",
                "passId",
                "timeIn",
                "timeOut",
                "checkoutType",
                "idNumber",
                "livePhotoRef",
                "idImageRef",
                "signatureRef",
            ]
        )
        for v in store.list_visits(user["schoolId"]):
            if not _visit_in_history_filter(v, body.filters or {}):
                continue
            id_val = v.get("idNumber") or ""
            if unmask and purpose and user["role"] in (
                Role.security_head.value,
                "security_head",
            ):
                id_out = id_val
            else:
                id_out = _mask_id_number(id_val)
            live_key = v.get("livePhotoKey") or ""
            id_key = v.get("idImageKey") or ""
            sig_key = v.get("signatureKey") or ""
            writer.writerow(
                [
                    v["id"],
                    v["visitorName"],
                    v["mobile"],
                    v["status"],
                    v.get("passId"),
                    v.get("timeIn"),
                    v.get("timeOut"),
                    v.get("checkoutType"),
                    id_out,
                    f"/v1/media/{live_key}" if live_key else "",
                    f"/v1/media/{id_key}" if id_key else "",
                    f"/v1/media/{sig_key}" if sig_key else "",
                ]
            )
            row_count += 1

    audit["rowCount"] = row_count
    store.add_outbox(
        {
            "schoolId": user["schoolId"],
            "event": "export.created",
            "visitId": None,
            "payload": {
                "scope": body.scope,
                "purpose": purpose,
                "unmask": unmask,
                "rowCount": row_count,
                "who": user["id"],
            },
            "channelHints": ["audit"],
            "status": "pending",
            "createdAt": ts,
        }
    )

    csv_text = buf.getvalue()
    return PlainTextResponse(
        content=csv_text,
        media_type="text/csv",
        headers={
            "X-Export-Watermark": WATERMARK,
            "X-Export-Scope": body.scope,
            "X-Export-Unmask": "1" if unmask else "0",
            "X-Export-Row-Count": str(row_count),
            "Content-Disposition": f'attachment; filename="export-{body.scope}.csv"',
        },
    )
