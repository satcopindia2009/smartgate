"""Emergency blast REST — Hub B1–B6 LOCKED."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import require_roles
from app.blast import (
    blast_public,
    channels_summary,
    confirm_blast,
    require_blast_enabled,
    retry_failed,
    template_public,
)
from app.config import WATERMARK
from app.errors import AppError
from app.inside import list_inside_visits
from app.models import BlastConfirmBody, BlastTemplateCreate, BlastTemplatePatch, Role
from app.util import now_iso
from app import store

router = APIRouter(prefix="/emergency", tags=["emergency"])

_BLAST_ROLES = (Role.admin, Role.security_head)


def _require_template(template_id: str, school_id: str) -> dict:
    template = store.get_blast_template(template_id)
    if not template or template.get("schoolId") != school_id:
        raise AppError("NOT_FOUND", f"Blast template {template_id} not found", 404)
    return template


@router.get("/blasts/preview")
def preview_blast(
    templateId: Optional[str] = Query(default=None),
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    school = store.school()
    cfg = require_blast_enabled(school)
    inside = list_inside_visits(user["schoolId"])
    template = None
    if templateId:
        template = _require_template(templateId, user["schoolId"])
        if not template.get("active", True):
            raise AppError("VALIDATION", "Blast template is inactive", 400)
    channel = template["channel"] if template else "sms"
    return {
        "insideCount": len(inside),
        "channelsSummary": channels_summary(cfg, inside, channel),
        "templateId": template["id"] if template else None,
        "instructionPreview": template["instruction"] if template else None,
        "emergencyBlastEnabled": True,
        "meta": {"watermark": WATERMARK},
    }


@router.post("/blasts")
def create_blast(
    body: BlastConfirmBody,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    school = store.school()
    require_blast_enabled(school)
    if body.confirm is not True:
        raise AppError(
            "VALIDATION",
            "confirm must be true — Admin/SH confirm-only (not L8 dual-control)",
            400,
        )
    template = _require_template(body.templateId, user["schoolId"])
    if not template.get("active", True):
        raise AppError("VALIDATION", "Blast template is inactive", 400)
    instruction = body.instruction or template["instruction"]
    blast = confirm_blast(
        school=school,
        user=user,
        template=template,
        instruction=instruction,
    )
    return blast_public(blast)


@router.get("/blasts/{blast_id}")
def get_blast(
    blast_id: str,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    blast = store.get_blast(blast_id)
    if not blast or blast.get("schoolId") != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Blast {blast_id} not found", 404)
    return blast_public(blast)


@router.post("/blasts/{blast_id}/retry-failed")
def retry_failed_recipients(
    blast_id: str,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    require_blast_enabled(store.school())
    blast = store.get_blast(blast_id)
    if not blast or blast.get("schoolId") != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Blast {blast_id} not found", 404)
    return blast_public(retry_failed(blast, user))


@router.get("/blast-templates")
def list_templates(user: dict = Depends(require_roles(*_BLAST_ROLES))):
    rows = [template_public(t) for t in store.list_blast_templates(user["schoolId"])]
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.post("/blast-templates")
def create_template(
    body: BlastTemplateCreate,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    from app.util import gen_blast_template_id

    ts = now_iso()
    row = {
        "id": gen_blast_template_id(store.next_seq("template_seq")),
        "schoolId": user["schoolId"],
        "name": body.name,
        "instruction": body.instruction,
        "channel": body.channel.value,
        "active": body.active,
        "updatedByUserId": user["id"],
        "updatedAt": ts,
        "createdAt": ts,
    }
    store.put_blast_template(row)
    return template_public(row)


@router.patch("/blast-templates/{template_id}")
def patch_template(
    template_id: str,
    body: BlastTemplatePatch,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    row = _require_template(template_id, user["schoolId"])
    data = body.model_dump(exclude_unset=True)
    if "name" in data and data["name"] is None:
        raise AppError("VALIDATION", "name must not be empty", 400)
    if "instruction" in data and data["instruction"] is None:
        raise AppError("VALIDATION", "instruction must not be empty", 400)
    if "name" in data:
        row["name"] = data["name"]
    if "instruction" in data:
        row["instruction"] = data["instruction"]
    if "channel" in data and data["channel"] is not None:
        row["channel"] = data["channel"].value if hasattr(data["channel"], "value") else data["channel"]
    if "active" in data and data["active"] is not None:
        row["active"] = data["active"]
    row["updatedByUserId"] = user["id"]
    row["updatedAt"] = now_iso()
    store.put_blast_template(row)
    return template_public(row)
