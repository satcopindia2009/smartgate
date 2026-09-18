from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import CurrentUser, require_roles
from app.config import WATERMARK
from app.models import Role
from app import store

router = APIRouter(tags=["notifications"])


@router.get("/internal/notify-outbox")
def notify_outbox(
    status: Optional[str] = Query(default="pending"),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    items = store.list_outbox(status=status)
    school_items = [o for o in items if o.get("schoolId") == user["schoolId"]]
    return {"data": school_items, "meta": {"watermark": WATERMARK}}


@router.post("/internal/notify-outbox/process")
def process_outbox(user: dict = Depends(require_roles(Role.admin, Role.security_head))):
    """Durable consumer tick: pending → sent/failed (in_app). SMS/WA remain stub/HOLD."""
    result = store.process_pending_outbox(school_id=user["schoolId"])
    return {**result, "meta": {"watermark": WATERMARK}}


@router.get("/notifications")
def list_host_notifications(
    user: CurrentUser,
    limit: int = Query(default=50, ge=1, le=200),
):
    """Host-visible in-app notifications (visit.pending and other outbox events)."""
    staff_id = user.get("staffId")
    items = store.list_notifications(user["schoolId"], host_staff_id=staff_id, limit=limit)
    # hosts only see own; admin/SH see school
    if user.get("role") in (Role.admin.value, Role.security_head.value, "admin", "security_head"):
        items = store.list_notifications(user["schoolId"], host_staff_id=None, limit=limit)
    return {"data": items, "meta": {"watermark": WATERMARK}}
