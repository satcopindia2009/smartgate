from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import require_roles
from app.config import WATERMARK
from app.models import Role
from app import store

router = APIRouter(prefix="/internal", tags=["internal"])


@router.get("/notify-outbox")
def notify_outbox(
    status: Optional[str] = Query(default="pending"),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    items = store.list_outbox(status=status)
    school_items = [o for o in items if o.get("schoolId") == user["schoolId"]]
    return {"data": school_items, "meta": {"watermark": WATERMARK}}
