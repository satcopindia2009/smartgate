"""Internal DSR (data subject request) stubs — Admin / Security Head."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends

from app.auth import require_roles
from app.blacklist_match import match_blacklist
from app.config import WATERMARK
from app.errors import AppError
from app.models import DsrRequestCreate, Role
from app.util import normalize_mobile, now_iso
from app import store

router = APIRouter(prefix="/internal/dsr", tags=["dsr"])


def _public(rec: dict) -> dict:
    out = dict(rec)
    out["meta"] = {"watermark": WATERMARK}
    return out


@router.post("/requests")
def create_dsr(
    body: DsrRequestCreate,
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    if not body.subjectMobile and not body.visitId:
        raise AppError(
            "VALIDATION",
            "subjectMobile or visitId required",
            400,
        )
    if body.visitId:
        visit = store.get_visit(body.visitId)
        if not visit or visit.get("schoolId") != user["schoolId"]:
            raise AppError("NOT_FOUND", f"Visit {body.visitId} not found", 404)
    mobile = normalize_mobile(body.subjectMobile) if body.subjectMobile else None
    ts = now_iso()
    seq = store.next_seq("dsr_seq")
    rec = {
        "id": f"DSR-{seq:04d}",
        "schoolId": user["schoolId"],
        "type": body.type,
        "subjectMobile": mobile,
        "visitId": body.visitId,
        "notes": (body.notes or "").strip() or None,
        "status": "open",
        "exceptionCode": None,
        "exceptionDetail": None,
        "createdAt": ts,
        "createdByUserId": user["id"],
        "fulfilledAt": None,
        "fulfilledByUserId": None,
    }
    store.put_dsr(rec)
    store.add_outbox(
        {
            "schoolId": user["schoolId"],
            "event": "dsr.created",
            "visitId": body.visitId,
            "payload": {
                "dsrId": rec["id"],
                "type": body.type,
                "subjectMobile": mobile,
                "byUserId": user["id"],
            },
            "channelHints": ["audit"],
            "status": "pending",
            "createdAt": ts,
        }
    )
    return _public(rec)


@router.get("/requests")
def list_dsr_requests(
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    items = store.list_dsr(user["schoolId"])
    return {"items": [_public(i) for i in items], "meta": {"watermark": WATERMARK}}


@router.post("/requests/{dsr_id}/fulfil")
def fulfil_dsr(
    dsr_id: str,
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    """Honour DSR; refuse erasure when legal hold or active blacklist match."""
    rec = store.get_dsr(dsr_id)
    if not rec or rec.get("schoolId") != user["schoolId"]:
        raise AppError("NOT_FOUND", f"DSR {dsr_id} not found", 404)
    if rec.get("status") == "fulfilled":
        return _public(rec)

    ts = now_iso()
    if rec.get("type") == "erasure":
        visit = store.get_visit(rec["visitId"]) if rec.get("visitId") else None
        # Resolve visit by mobile if needed
        if not visit and rec.get("subjectMobile"):
            for v in store.list_visits(user["schoolId"]):
                if normalize_mobile(v.get("mobile")) == rec["subjectMobile"]:
                    if v.get("legalHold"):
                        visit = v
                        break
                    visit = visit or v

        if visit and visit.get("legalHold"):
            rec["status"] = "refused"
            rec["exceptionCode"] = "LEGAL_HOLD"
            rec["exceptionDetail"] = (
                f"Erasure refused: visit {visit['id']} has legalHold=true"
            )
            rec["fulfilledAt"] = ts
            rec["fulfilledByUserId"] = user["id"]
            store.put_dsr(rec)
            store.add_outbox(
                {
                    "schoolId": user["schoolId"],
                    "event": "dsr.erasure_refused",
                    "visitId": visit["id"],
                    "payload": {
                        "dsrId": rec["id"],
                        "exceptionCode": "LEGAL_HOLD",
                        "detail": rec["exceptionDetail"],
                    },
                    "channelHints": ["audit"],
                    "status": "pending",
                    "createdAt": ts,
                }
            )
            raise AppError(
                "LEGAL_HOLD",
                rec["exceptionDetail"],
                409,
                {"dsrId": rec["id"], "visitId": visit["id"], "exceptionCode": "LEGAL_HOLD"},
            )

        mobile = rec.get("subjectMobile")
        id_type = visit.get("idType") if visit else None
        id_number = visit.get("idNumber") if visit else None
        if not mobile and visit:
            mobile = visit.get("mobile")
        hit = match_blacklist(user["schoolId"], mobile, id_type, id_number)
        if hit:
            rec["status"] = "refused"
            rec["exceptionCode"] = "ACTIVE_BLACKLIST"
            rec["exceptionDetail"] = (
                f"Erasure refused: active blacklist match {hit['id']}"
            )
            rec["fulfilledAt"] = ts
            rec["fulfilledByUserId"] = user["id"]
            store.put_dsr(rec)
            store.add_outbox(
                {
                    "schoolId": user["schoolId"],
                    "event": "dsr.erasure_refused",
                    "visitId": visit["id"] if visit else None,
                    "payload": {
                        "dsrId": rec["id"],
                        "exceptionCode": "ACTIVE_BLACKLIST",
                        "blacklistId": hit["id"],
                        "detail": rec["exceptionDetail"],
                    },
                    "channelHints": ["audit"],
                    "status": "pending",
                    "createdAt": ts,
                }
            )
            raise AppError(
                "ACTIVE_BLACKLIST",
                rec["exceptionDetail"],
                409,
                {
                    "dsrId": rec["id"],
                    "blacklistId": hit["id"],
                    "exceptionCode": "ACTIVE_BLACKLIST",
                },
            )

        # Stub fulfil: clear media keys on linked visit(s); keep metadata row
        targets = []
        if visit:
            targets = [visit]
        elif rec.get("subjectMobile"):
            targets = [
                v
                for v in store.list_visits(user["schoolId"])
                if normalize_mobile(v.get("mobile")) == rec["subjectMobile"]
            ]
        for v in targets:
            for field in ("livePhotoKey", "idImageKey", "signatureKey"):
                key = v.get(field)
                if key:
                    store.delete_media(key)
                    v[field] = None
            v["updatedAt"] = ts
            store.put_visit(v)

    rec["status"] = "fulfilled"
    rec["exceptionCode"] = None
    rec["exceptionDetail"] = None
    rec["fulfilledAt"] = ts
    rec["fulfilledByUserId"] = user["id"]
    store.put_dsr(rec)
    store.add_outbox(
        {
            "schoolId": user["schoolId"],
            "event": "dsr.fulfilled",
            "visitId": rec.get("visitId"),
            "payload": {"dsrId": rec["id"], "type": rec["type"]},
            "channelHints": ["audit"],
            "status": "pending",
            "createdAt": ts,
        }
    )
    return _public(rec)
