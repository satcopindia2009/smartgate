from __future__ import annotations

import csv
import io

from fastapi import APIRouter, Depends
from fastapi.responses import PlainTextResponse

from app.auth import require_roles
from app.config import WATERMARK
from app.errors import AppError
from app.models import ExportRequest, Role
from app.util import now_iso
from app import store

router = APIRouter(prefix="/exports", tags=["exports"])


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
    ts = now_iso()
    audit = {
        "who": user["id"],
        "displayName": user["displayName"],
        "when": ts,
        "scope": body.scope,
        "filters": body.filters,
        "purpose": purpose,
        "schoolId": user["schoolId"],
    }
    store.add_export(audit)

    buf = io.StringIO()
    writer = csv.writer(buf)

    if body.scope == "blacklist":
        writer.writerow(["id", "name", "mobile", "severity", "active", "reason"])
        for b in store.list_blacklist(user["schoolId"]):
            writer.writerow([b["id"], b["name"], b.get("mobile"), b["severity"], b["active"], b["reason"]])
    elif body.scope == "inside":
        writer.writerow(["visitId", "visitorName", "mobile", "passId", "gateId", "timeIn", "hostId"])
        for v in store.list_visits(user["schoolId"]):
            if v["status"] == "inside":
                writer.writerow(
                    [v["id"], v["visitorName"], v["mobile"], v.get("passId"), v.get("gateInId"), v.get("timeIn"), v["hostId"]]
                )
    elif body.scope == "daily_gate_summary":
        writer.writerow(["gate", "note"])
        for g in store.list_gates(user["schoolId"]):
            writer.writerow([g["name"], "see /v1/reports/today-by-gate"])
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
    else:  # history
        writer.writerow(["visitId", "visitorName", "mobile", "status", "passId", "timeIn", "timeOut", "checkoutType"])
        for v in store.list_visits(user["schoolId"]):
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
                ]
            )

    csv_text = buf.getvalue()
    return PlainTextResponse(
        content=csv_text,
        media_type="text/csv",
        headers={
            "X-Export-Watermark": WATERMARK,
            "X-Export-Scope": body.scope,
            "Content-Disposition": f'attachment; filename="export-{body.scope}.csv"',
        },
    )
