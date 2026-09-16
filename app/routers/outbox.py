from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import require_roles
from app.config import WATERMARK
from app.errors import AppError
from app.models import NotifyOutboxItem, NotifyOutboxResponse, Role
from app import store

router = APIRouter(prefix="/internal", tags=["internal"])

_ALLOWED_STATUS = {"pending", "sent", "failed", "all"}


@router.get("/notify-outbox", response_model=NotifyOutboxResponse)
def notify_outbox(
    status: Optional[str] = Query(default="pending"),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    raw = (status or "pending").strip().lower()
    if raw not in _ALLOWED_STATUS:
        raise AppError(
            "VALIDATION",
            "status must be pending, sent, failed, or all",
            400,
            {"allowed": sorted(_ALLOWED_STATUS)},
        )
    filter_status = None if raw == "all" else raw
    items = store.list_outbox(status=filter_status)
    school_items = [o for o in items if o.get("schoolId") == user["schoolId"]]
    school_items.sort(key=lambda o: o.get("createdAt") or "", reverse=True)
    data = [NotifyOutboxItem.model_validate(o) for o in school_items]
    return NotifyOutboxResponse(
        data=data,
        meta={
            "watermark": WATERMARK,
            "schoolId": user["schoolId"],
            "status": raw,
            "count": len(data),
        },
    )
