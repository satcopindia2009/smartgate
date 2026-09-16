from __future__ import annotations

import csv
import io
from typing import Any

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse, PlainTextResponse

from app.auth import require_roles
from app.config import MAX_EXPORT_ROWS, WATERMARK
from app.errors import AppError
from app.models import ExportJobOut, ExportRequest, Role
from app.reporting import GATE_REPORT_FIELDS, agg_by_gate, demo_today, visit_local_date
from app.util import now_iso
from app import store

router = APIRouter(prefix="/exports", tags=["exports"])


def _filter_date(visit: dict, filters: dict[str, Any]) -> bool:
    start = (filters.get("from") or filters.get("dateFrom") or "")[:10] or None
    end = (filters.get("to") or filters.get("dateTo") or "")[:10] or None
    if not start and not end:
        return True
    day = visit_local_date(visit)
    if not day:
        return False
    if start and day < start:
        return False
    if end and day > end:
        return False
    return True


def _filter_visit(visit: dict, filters: dict[str, Any]) -> bool:
    if not _filter_date(visit, filters):
        return False
    gate_id = filters.get("gateId")
    if gate_id and (visit.get("gateInId") or visit.get("gateId")) != gate_id:
        return False
    visitor_type = filters.get("visitorType")
    if visitor_type and visit.get("visitorType") != visitor_type:
        return False
    status = filters.get("status")
    if status and visit.get("status") != status:
        return False
    q = (filters.get("q") or "").strip().lower()
    if q:
        blob = " ".join(
            str(visit.get(k) or "")
            for k in ("id", "visitorName", "mobile", "passId", "hostId")
        ).lower()
        if q not in blob:
            return False
    return True


def _build_rows(school_id: str, scope: str, filters: dict[str, Any]) -> tuple[list[str], list[list[Any]]]:
    if scope == "blacklist":
        header = ["id", "name", "mobile", "severity", "active", "reason"]
        rows = []
        active = filters.get("active")
        for b in store.list_blacklist(school_id):
            if active is not None and bool(b["active"]) != bool(active):
                continue
            rows.append(
                [b["id"], b["name"], b.get("mobile"), b["severity"], b["active"], b["reason"]]
            )
        return header, rows

    if scope == "inside":
        header = ["visitId", "visitorName", "mobile", "passId", "gateId", "timeIn", "hostId"]
        rows = []
        for v in store.list_visits(school_id):
            if v["status"] != "inside":
                continue
            if not _filter_visit(v, filters):
                continue
            rows.append(
                [
                    v["id"],
                    v["visitorName"],
                    v["mobile"],
                    v.get("passId"),
                    v.get("gateInId") or v.get("gateId"),
                    v.get("timeIn"),
                    v["hostId"],
                ]
            )
        return header, rows

    if scope == "daily_gate_summary":
        start = (filters.get("from") or filters.get("dateFrom") or demo_today())[:10]
        end = (filters.get("to") or filters.get("dateTo") or start)[:10]
        header = list(GATE_REPORT_FIELDS)
        rows = []
        for row in agg_by_gate(school_id, start, end):
            rows.append([row[k] for k in GATE_REPORT_FIELDS])
        return header, rows

    if scope == "pickup_events":
        header = [
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
        rows = []
        for p in store.list_pickups(school_id):
            student = store.get_student(p.get("studentId") or "")
            person = (
                store.get_authorized_person(p["collectorPickupPersonId"])
                if p.get("collectorPickupPersonId")
                else None
            )
            rows.append(
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
        return header, rows

    if scope == "pickup_lists":
        header = [
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
        rows = []
        for student in store.list_students(school_id):
            for person in store.list_authorized_people(school_id, student["id"]):
                rows.append(
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
        return header, rows

    # history
    header = [
        "visitId",
        "visitorName",
        "mobile",
        "visitorType",
        "status",
        "passId",
        "gateId",
        "hostId",
        "timeIn",
        "timeOut",
        "checkoutType",
        "blacklistHit",
    ]
    rows = []
    for v in store.list_visits(school_id):
        if not _filter_visit(v, filters):
            continue
        rows.append(
            [
                v["id"],
                v["visitorName"],
                v["mobile"],
                v.get("visitorType"),
                v["status"],
                v.get("passId"),
                v.get("gateInId") or v.get("gateId"),
                v.get("hostId"),
                v.get("timeIn"),
                v.get("timeOut"),
                v.get("checkoutType"),
                v.get("blacklistHit"),
            ]
        )
    return header, rows


def _csv_text(header: list[str], rows: list[list[Any]]) -> str:
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(header)
    writer.writerows(rows)
    return buf.getvalue()


@router.post(
    "",
    responses={
        200: {
            "description": "CSV download (≤10k data rows)",
            "content": {"text/csv": {"schema": {"type": "string"}}},
        },
        202: {
            "description": "Async job when row count exceeds 10k",
            "model": ExportJobOut,
        },
    },
)
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

    header, rows = _build_rows(user["schoolId"], body.scope, body.filters or {})
    ts = now_iso()
    delivery = "job" if len(rows) > MAX_EXPORT_ROWS else "csv"
    audit = store.add_export(
        {
            "who": user["id"],
            "displayName": user["displayName"],
            "when": ts,
            "scope": body.scope,
            "filters": body.filters,
            "purpose": purpose,
            "schoolId": user["schoolId"],
            "rowCount": len(rows),
            "delivery": delivery,
        }
    )

    if delivery == "job":
        job = ExportJobOut(
            jobId=audit["id"],
            status="queued",
            scope=body.scope,
            rowCount=len(rows),
            message="Export exceeds 10k rows — demo job queued (no worker; CSV not generated)",
            meta={"watermark": WATERMARK, "auditId": audit["id"]},
        )
        return JSONResponse(status_code=202, content=job.model_dump())

    csv_text = _csv_text(header, rows)
    return PlainTextResponse(
        content=csv_text,
        media_type="text/csv",
        headers={
            "X-Export-Watermark": WATERMARK,
            "X-Export-Scope": body.scope,
            "X-Export-Audit-Id": audit["id"],
            "X-Export-Row-Count": str(len(rows)),
            "Content-Disposition": f'attachment; filename="export-{body.scope}.csv"',
        },
    )
