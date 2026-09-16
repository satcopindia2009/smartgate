from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import CurrentUser, require_roles
from app.blacklist_match import match_blacklist
from app.config import WATERMARK
from app.errors import AppError
from app.models import BlacklistCreate, BlacklistMatchRequest, BlacklistPatch, Role
from app.util import now_iso
from app import store

router = APIRouter(prefix="/blacklist", tags=["blacklist"])


def match_blacklist_internal(school_id, mobile, id_type, id_number):
    """Backward-compatible alias used by visits router."""
    return match_blacklist(school_id, mobile, id_type, id_number)


@router.get("")
def list_blacklist(
    user: CurrentUser,
    active: Optional[bool] = Query(default=None),
):
    # All authenticated roles can view (admin view; SH write)
    rows = store.list_blacklist(user["schoolId"], active=active)
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.post("")
def create_blacklist(
    body: BlacklistCreate,
    user: dict = Depends(require_roles(Role.security_head)),
):
    if not body.mobile and not (body.idType and body.idNumber):
        raise AppError(
            "VALIDATION",
            "mobile or (idType + idNumber) required for hard match",
            400,
        )
    bid = f"BL-{store.next_seq('bl_seq'):02d}"
    row = {
        "id": bid,
        "schoolId": user["schoolId"],
        "name": body.name,
        "mobile": body.mobile,
        "idType": body.idType.value if body.idType else None,
        "idNumber": body.idNumber,
        "reason": body.reason,
        "severity": body.severity.value,
        "active": True,
        "addedBy": user["displayName"],
        "addedAt": now_iso(),
        "expiresOn": body.expiresOn,
        "notes": body.notes,
        "photoUrl": body.photoUrl,
    }
    store.put_blacklist(row)
    return {**row, "meta": {"watermark": WATERMARK}}


@router.patch("/{bl_id}")
def patch_blacklist(
    bl_id: str,
    body: BlacklistPatch,
    user: dict = Depends(require_roles(Role.security_head)),
):
    row = store.get_blacklist(bl_id)
    if not row or row["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Blacklist {bl_id} not found", 404)
    data = body.model_dump(exclude_unset=True)
    if "idType" in data and data["idType"] is not None:
        data["idType"] = data["idType"].value if hasattr(data["idType"], "value") else data["idType"]
    if "severity" in data and data["severity"] is not None:
        data["severity"] = data["severity"].value if hasattr(data["severity"], "value") else data["severity"]
    row.update(data)
    store.put_blacklist(row)
    return {**row, "meta": {"watermark": WATERMARK}}


@router.post("/match")
def match_endpoint(body: BlacklistMatchRequest, user: CurrentUser):
    hit = match_blacklist(
        user["schoolId"],
        body.mobile,
        body.idType.value if body.idType else None,
        body.idNumber,
    )
    return {"hit": hit, "meta": {"watermark": WATERMARK}}
