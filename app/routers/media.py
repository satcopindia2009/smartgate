from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import Response

from app.auth import CurrentUser
from app.config import WATERMARK
from app.errors import AppError
from app.models import MediaKind, MediaUploadResponse
from app.util import gen_media_key, now_iso
from app import store

router = APIRouter(prefix="/media", tags=["media"])


@router.post("/upload", response_model=MediaUploadResponse)
async def upload_media(
    user: CurrentUser,
    file: UploadFile = File(...),
    kind: MediaKind = Form(default=MediaKind.live_photo),
    visitId: Optional[str] = Form(default=None),
):
    data = await file.read()
    kind_val = kind.value
    key = gen_media_key(kind_val)
    store.put_media(
        {
            "key": key,
            "schoolId": user["schoolId"],
            "kind": kind_val,
            "visitId": visitId,
            "contentType": file.content_type or "application/octet-stream",
            "createdAt": now_iso(),
            "createdByUserId": user["id"],
            "bytes": data,
            "filename": file.filename,
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
