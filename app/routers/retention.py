"""Internal retention purge jobs (Admin / Security Head)."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import require_roles
from app.config import WATERMARK
from app.models import RetentionPurgeBody, Role
from app import store

router = APIRouter(prefix="/internal/retention", tags=["retention"])


@router.get("/status")
def retention_status(
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
    asOf: Optional[str] = Query(default=None, description="ISO date YYYY-MM-DD"),
):
    result = store.retention_status(user["schoolId"], as_of_date=asOf)
    return {**result, "meta": {"watermark": WATERMARK}}


@router.post("/purge")
def retention_purge(
    body: RetentionPurgeBody = RetentionPurgeBody(),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    school_id = body.schoolId or user["schoolId"]
    if school_id != user["schoolId"] and user["role"] not in (
        Role.admin.value,
        Role.security_head.value,
        "admin",
        "security_head",
    ):
        school_id = user["schoolId"]
    # Tenant isolation: always purge caller's school only
    school_id = user["schoolId"]
    result = store.purge_expired_media(school_id, dry_run=body.dryRun, as_of_date=body.asOf)
    return {**result, "meta": {"watermark": WATERMARK}}
