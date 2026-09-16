from __future__ import annotations

from datetime import datetime, timedelta
from typing import Optional

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import Response

from app.auth import CurrentUser
from app.config import COLLECTOR_PHOTO_RETENTION_DAYS, WATERMARK
from app.errors import AppError
from app.models import MediaKind, MediaUploadResponse
from app.util import TZ, gen_media_key, now_iso
from app import store

router = APIRouter(prefix="/media", tags=["media"])


@router.post("/upload", response_model=MediaUploadResponse)
async def upload_media(
    user: CurrentUser,
    file: UploadFile = File(...),
    kind: MediaKind = Form(default=MediaKind.live_photo),
    visitId: Optional[str] = Form(default=None),
    pickupId: Optional[str] = Form(default=None),
):
    kind_val = kind.value
    if kind_val == "collector_live_photo" and pickupId:
        pickup = store.get_pickup(pickupId)
        if not pickup or pickup.get("schoolId") != user["schoolId"]:
            raise AppError("NOT_FOUND", f"Pickup {pickupId} not found", 404)
        if not pickup.get("pickupConsentAt") or not pickup.get("pickupConsentVersion"):
            raise AppError(
                "CONSENT_REQUIRED",
                "Pickup consent must be recorded before collector live photo",
                400,
            )
    data = await file.read()
    key = gen_media_key(kind_val)
    retain_until = None
    if kind_val == "collector_live_photo":
        retain_until = (
            datetime.now(TZ) + timedelta(days=COLLECTOR_PHOTO_RETENTION_DAYS)
        ).date().isoformat()
    store.put_media(
        {
            "key": key,
            "schoolId": user["schoolId"],
            "kind": kind_val,
            "visitId": visitId,
            "pickupId": pickupId,
            "contentType": file.content_type or "application/octet-stream",
            "createdAt": now_iso(),
            "createdByUserId": user["id"],
            "bytes": data,
            "filename": file.filename,
            "retainUntil": retain_until,
            "retainUntilDays": COLLECTOR_PHOTO_RETENTION_DAYS
            if kind_val == "collector_live_photo"
            else None,
        }
    )
    url = f"/v1/media/{key}"
    return {"key": key, "url": url, "meta": {"watermark": WATERMARK}}


@router.get("/{key:path}")
def get_media(key: str, user: CurrentUser):
    m = store.get_media(key)
    if not m or m["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", "Media not found", 404)
    content = m.get("bytes") or b""
    if not content:
        # Stub placeholder JPEG-ish text for demo keys without bytes
        content = b"DEMO_MEDIA_STUB"
    return Response(content=content, media_type=m.get("contentType", "application/octet-stream"))
