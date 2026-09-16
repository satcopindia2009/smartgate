"""School-scoped config — blast toggle (P2 E3)."""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.auth import require_roles
from app.blast import school_blast_config
from app.config import (
    BLAST_DEFAULT_STAFF_CHANNELS,
    BLAST_DEFAULT_VISITOR_CHANNELS,
    WATERMARK,
)
from app.errors import AppError
from app.models import BlastChannel, BlastConfigPatch, Role
from app.util import now_iso
from app import store

router = APIRouter(prefix="/schools", tags=["schools"])

_BLAST_ROLES = (Role.admin, Role.security_head)
_ALLOWED_VISITOR = {BlastChannel.sms.value, BlastChannel.whatsapp.value}
_ALLOWED_STAFF = {BlastChannel.in_app.value, BlastChannel.push.value}


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


@router.get("/me/blast-config")
def get_blast_config(user: dict = Depends(require_roles(*_BLAST_ROLES))):
    school = store.school()
    if not school or school.get("id") != user["schoolId"]:
        raise AppError("NOT_FOUND", "School not found", 404)
    return _config_public(school)


@router.patch("/me/blast-config")
def patch_blast_config(
    body: BlastConfigPatch,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    school = store.school()
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
    store.set_school(school)
    return _config_public(school)
