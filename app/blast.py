"""Emergency visitor blast — Hub B1–B6.

Visitor audience is GET /v1/visits/inside only (B1). Admin|SH confirm-only
(B2). SMS mock / WhatsApp skipped_hold (B3). Template-only (B4). Full audit
(B5). Does not change visit status (B6). No live SMS/WA providers.
"""
from __future__ import annotations

import secrets
from typing import Any, Optional

from app.config import (
    BLAST_DEFAULT_STAFF_CHANNELS,
    BLAST_DEFAULT_VISITOR_CHANNELS,
    WA_HOLD,
    WATERMARK,
)
from app.errors import AppError
from app.inside import list_inside_visits
from app.util import mask_mobile, normalize_mobile, now_iso
from app import store

VISITOR_CHANNELS = ("sms", "whatsapp")
STAFF_CHANNELS = ("in_app", "push")
SKIPPED_OK = ("sent", "skipped_no_mobile", "skipped_hold")


def school_blast_config(school: Optional[dict]) -> dict:
    school = school or {}
    visitor = school.get("blastChannelsVisitor") or list(BLAST_DEFAULT_VISITOR_CHANNELS)
    staff = school.get("blastChannelsStaff") or list(BLAST_DEFAULT_STAFF_CHANNELS)
    return {
        "schoolId": school.get("id"),
        "emergencyBlastEnabled": bool(school.get("emergencyBlastEnabled", False)),
        "blastStaffLaneEnabled": bool(school.get("blastStaffLaneEnabled", False)),
        "blastChannelsVisitor": list(visitor),
        "blastChannelsStaff": list(staff),
        "updatedByUserId": school.get("blastConfigUpdatedByUserId"),
        "updatedAt": school.get("blastConfigUpdatedAt"),
    }


def require_blast_enabled(school: Optional[dict]) -> dict:
    cfg = school_blast_config(school)
    if not cfg["emergencyBlastEnabled"]:
        raise AppError("NOT_FOUND", "Emergency blast is not enabled", 404)
    return cfg


def _meta(row: dict) -> dict:
    out = dict(row)
    out["meta"] = {"watermark": WATERMARK}
    return out


def template_public(row: dict) -> dict:
    return _meta(
        {
            "id": row["id"],
            "schoolId": row["schoolId"],
            "name": row["name"],
            "instruction": row["instruction"],
            "channel": row["channel"],
            "active": bool(row.get("active", True)),
            "updatedByUserId": row.get("updatedByUserId"),
            "updatedAt": row.get("updatedAt"),
        }
    )


def recipient_public(row: dict) -> dict:
    return {
        "blastId": row["blastId"],
        "blast_id": row["blastId"],
        "visitId": row.get("visitId"),
        "mobileMasked": row["mobileMasked"],
        "channel": row["channel"],
        "status": row["status"],
        "providerMessageId": row.get("providerMessageId"),
        "attemptedAt": row.get("attemptedAt"),
        "errorCode": row.get("errorCode"),
    }


def counts_for(recipients: list[dict]) -> dict[str, int]:
    counts = {
        "queued": 0,
        "sent": 0,
        "failed": 0,
        "skipped_no_mobile": 0,
        "skipped_hold": 0,
    }
    for row in recipients:
        status = row.get("status")
        if status in counts:
            counts[status] += 1
    return counts


def derive_blast_status(recipients: list[dict]) -> str:
    if not recipients:
        return "completed"
    statuses = [r.get("status") for r in recipients]
    if all(s in SKIPPED_OK for s in statuses):
        return "completed"
    if all(s == "failed" for s in statuses):
        return "failed"
    if any(s == "failed" for s in statuses):
        return "partial"
    if all(s == "queued" for s in statuses):
        return "queued"
    return "sending"


def blast_public(blast: dict) -> dict:
    recipients = [recipient_public(r) for r in store.list_blast_recipients(blast["blastId"])]
    counts = counts_for(recipients)
    return _meta(
        {
            "blastId": blast["blastId"],
            "blast_id": blast["blastId"],
            "schoolId": blast["schoolId"],
            "triggeredByUserId": blast["triggeredByUserId"],
            "triggeredAt": blast["triggeredAt"],
            "templateId": blast["templateId"],
            "instruction": blast["instruction"],
            "insideCount": blast["insideCount"],
            "recipientCount": blast["recipientCount"],
            "status": blast["status"],
            "confirmAt": blast.get("confirmAt"),
            "recipients": recipients,
            "counts": counts,
        }
    )


def mock_send(channel: str, mobile: Optional[str]) -> tuple[str, Optional[str], Optional[str]]:
    """Stub provider. WhatsApp HOLD → skipped_hold. SMS mock → sent."""
    if channel == "whatsapp":
        return "skipped_hold", None, "WA_HOLD"
    if channel == "sms":
        if not normalize_mobile(mobile):
            return "skipped_no_mobile", None, "NO_MOBILE"
        return "sent", f"mock-sms-{secrets.token_hex(4)}", None
    if channel in STAFF_CHANNELS:
        return "sent", f"mock-{channel}-{secrets.token_hex(4)}", None
    return "failed", None, "UNKNOWN_CHANNEL"


def visitor_channels_for(cfg: dict, template_channel: str) -> list[str]:
    """Template channel is the selected send path (B4).

    WhatsApp template → WA rows only (`skipped_hold`). SMS template uses the
    school visitor channels (default `sms`); extra `whatsapp` in config still
    logs HOLD rows and never live-sends.
    """
    if template_channel == "whatsapp":
        return ["whatsapp"]
    planned: list[str] = []
    for ch in cfg.get("blastChannelsVisitor") or list(BLAST_DEFAULT_VISITOR_CHANNELS):
        if ch in VISITOR_CHANNELS and ch not in planned:
            planned.append(ch)
    if "sms" not in planned:
        planned.insert(0, "sms")
    if not planned:
        planned = ["sms"]
    return planned


def staff_channels_for(cfg: dict) -> list[str]:
    if not cfg.get("blastStaffLaneEnabled"):
        return []
    planned: list[str] = []
    for ch in cfg.get("blastChannelsStaff") or list(BLAST_DEFAULT_STAFF_CHANNELS):
        if ch in STAFF_CHANNELS and ch not in planned:
            planned.append(ch)
    return planned


def channels_summary(cfg: dict, inside: list[dict], template_channel: Optional[str] = None) -> dict[str, Any]:
    channels = visitor_channels_for(cfg, template_channel or "sms")
    with_mobile = sum(1 for v in inside if normalize_mobile(v.get("mobile")))
    without = len(inside) - with_mobile
    summary: dict[str, Any] = {
        "sms": {
            "planned": with_mobile if "sms" in channels else 0,
            "skipped_no_mobile": without if "sms" in channels else 0,
        },
        "whatsapp": {
            "planned": 0,
            "hold": True,
            "skipped_hold": len(inside) if "whatsapp" in channels else 0,
        },
    }
    staff_ch = staff_channels_for(cfg)
    if staff_ch:
        summary["staff"] = {"channels": staff_ch, "enabled": True}
    else:
        summary["staff"] = {"enabled": False}
    summary["visitor"] = channels
    summary["staffLaneEnabled"] = bool(cfg.get("blastStaffLaneEnabled"))
    summary["whatsappHold"] = True
    return summary


def staff_lane_users(school_id: str) -> list[dict]:
    """Optional staff lane: Admin + Security Head only. Escort/gate staff out (B1)."""
    out = []
    for user in store.list_users(school_id):
        if user.get("role") in ("admin", "security_head") and user.get("active", True):
            out.append(user)
    out.sort(key=lambda u: u["id"])
    return out


def _new_recipient(
    *,
    blast_id: str,
    school_id: str,
    visit_id: Optional[str],
    mobile: Optional[str],
    channel: str,
) -> dict:
    status, provider_id, error = mock_send(channel, mobile)
    row = {
        "id": f"BR-{store.next_seq('recipient_seq'):04d}",
        "blastId": blast_id,
        "schoolId": school_id,
        "visitId": visit_id,
        "mobileMasked": mask_mobile(mobile),
        "channel": channel,
        "status": status,
        "providerMessageId": provider_id,
        "attemptedAt": now_iso(),
        "errorCode": error,
    }
    store.put_blast_recipient(row)
    return row


def create_pending_blast(
    *,
    school: dict,
    user: dict,
    template: dict,
    instruction: str,
) -> dict:
    """Draft mode=pending_confirm — row only; snapshot/enqueue happens on confirm (B1)."""
    inside = list_inside_visits(user["schoolId"])
    ts = now_iso()
    blast = {
        "blastId": store.next_blast_id(),
        "schoolId": user["schoolId"],
        "triggeredByUserId": user["id"],
        "triggeredAt": ts,
        "templateId": template["id"],
        "instruction": instruction,
        "insideCount": len(inside),
        "recipientCount": 0,
        "status": "pending_confirm",
        "confirmAt": None,
        "createdAt": ts,
        "updatedAt": ts,
    }
    store.put_blast(blast)
    return blast


def confirm_blast(
    *,
    school: dict,
    user: dict,
    template: dict,
    instruction: str,
    existing: Optional[dict] = None,
) -> dict:
    """Snapshot inside set at confirm (B1). Never mutates visit rows (B6)."""
    cfg = school_blast_config(school)
    inside = list_inside_visits(user["schoolId"])
    ts = now_iso()
    visitor_ch = visitor_channels_for(cfg, template["channel"])
    staff_ch = staff_channels_for(cfg)

    if existing:
        if existing.get("status") not in (None, "pending_confirm"):
            raise AppError(
                "VALIDATION",
                f"Cannot confirm blast in status {existing.get('status')}",
                400,
            )
        blast = existing
        blast["instruction"] = instruction
        blast["templateId"] = template["id"]
        blast["triggeredByUserId"] = user["id"]
        blast_id = blast["blastId"]
    else:
        blast_id = store.next_blast_id()
        blast = {
            "blastId": blast_id,
            "schoolId": user["schoolId"],
            "triggeredByUserId": user["id"],
            "triggeredAt": ts,
            "templateId": template["id"],
            "instruction": instruction,
            "createdAt": ts,
        }

    blast["insideCount"] = len(inside)
    blast["recipientCount"] = 0
    blast["status"] = "sending"
    blast["confirmAt"] = ts
    blast["updatedAt"] = ts
    store.put_blast(blast)

    recipients: list[dict] = []
    for visit in inside:
        for channel in visitor_ch:
            recipients.append(
                _new_recipient(
                    blast_id=blast_id,
                    school_id=user["schoolId"],
                    visit_id=visit["id"],
                    mobile=visit.get("mobile"),
                    channel=channel,
                )
            )
    for staff_user in staff_lane_users(user["schoolId"]) if staff_ch else []:
        for channel in staff_ch:
            recipients.append(
                _new_recipient(
                    blast_id=blast_id,
                    school_id=user["schoolId"],
                    visit_id=None,
                    mobile=staff_user.get("phone"),
                    channel=channel,
                )
            )

    blast["recipientCount"] = len(recipients)
    blast["status"] = derive_blast_status(recipients)
    blast["updatedAt"] = now_iso()
    store.put_blast(blast)

    recipient_refs = [
        {
            "visitId": r.get("visitId"),
            "mobileMasked": r["mobileMasked"],
            "channel": r["channel"],
        }
        for r in recipients
        if r.get("visitId")
    ]
    store.add_outbox(
        {
            "schoolId": user["schoolId"],
            "event": "emergency.blast",
            "visitId": None,
            "blastId": blast_id,
            "payload": {
                "blastId": blast_id,
                "insideCount": blast["insideCount"],
                "instruction": instruction,
                "templateId": template["id"],
                "schoolId": user["schoolId"],
                "triggeredByUserId": user["id"],
                "channels": visitor_ch,
                "recipientRefs": recipient_refs,
                "waHold": WA_HOLD,
            },
            "channelHints": visitor_ch,
            "status": "pending",
            "createdAt": ts,
        }
    )
    return blast


def retry_failed(blast: dict, user: dict) -> dict:
    failed = [
        r
        for r in store.list_blast_recipients(blast["blastId"])
        if r.get("status") == "failed"
    ]
    if not failed:
        return blast
    ts = now_iso()
    retried_refs = []
    for row in failed:
        visit = store.get_visit(row["visitId"]) if row.get("visitId") else None
        mobile = visit.get("mobile") if visit else None
        status, provider_id, error = mock_send(row["channel"], mobile)
        row["status"] = status
        row["providerMessageId"] = provider_id
        row["attemptedAt"] = ts
        row["errorCode"] = error
        store.put_blast_recipient(row)
        retried_refs.append(
            {
                "visitId": row.get("visitId"),
                "mobileMasked": row["mobileMasked"],
                "channel": row["channel"],
            }
        )
    recipients = store.list_blast_recipients(blast["blastId"])
    blast["status"] = derive_blast_status(recipients)
    blast["updatedAt"] = ts
    store.put_blast(blast)
    store.add_outbox(
        {
            "schoolId": blast["schoolId"],
            "event": "emergency.blast",
            "visitId": None,
            "blastId": blast["blastId"],
            "payload": {
                "blastId": blast["blastId"],
                "insideCount": blast["insideCount"],
                "instruction": blast["instruction"],
                "templateId": blast["templateId"],
                "schoolId": blast["schoolId"],
                "triggeredByUserId": user["id"],
                "channels": ["sms"],
                "recipientRefs": retried_refs,
                "retryFailed": True,
                "waHold": WA_HOLD,
            },
            "channelHints": ["sms"],
            "status": "pending",
            "createdAt": ts,
        }
    )
    return blast
