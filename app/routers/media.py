from __future__ import annotations

from pathlib import Path

from datetime import datetime, timedelta
from typing import Optional

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import Response

from app.auth import CurrentUser
from app.config import (
    COLLECTOR_PHOTO_RETENTION_DAYS,
    ID_IMAGE_RETENTION_DAYS,
    LIVE_PHOTO_RETENTION_DAYS,
    REJECTED_VISIT_MEDIA_RETENTION_DAYS,
    SIGNATURE_RETENTION_DAYS,
    WATERMARK,
)
from app.errors import AppError
from app.models import MediaKind, MediaUploadResponse
from app.util import TZ, gen_media_key, now_iso
from app import store

MEDIA_DIR = Path(__file__).resolve().parents[2] / "data" / "media"
MEDIA_DIR.mkdir(parents=True, exist_ok=True)


def _media_path(key: str) -> Path:
    safe = key.replace("\\", "/").lstrip("/")
    return MEDIA_DIR / safe


def _save_media_bytes(key: str, content: bytes) -> None:
    path = _media_path(key)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)


def _load_media_bytes(key: str):
    path = _media_path(key)
    if path.is_file():
        return path.read_bytes()
    return None


def retention_days_for_kind(kind: str, *, rejected: bool = False) -> Optional[int]:
    """Policy: live photo 90d, signature 90d, ID image 30d, rejected 14d, collector 90d."""
    if rejected and kind in ("live_photo", "id_image", "signature", "other"):
        return REJECTED_VISIT_MEDIA_RETENTION_DAYS
    if kind == "live_photo":
        return LIVE_PHOTO_RETENTION_DAYS
    if kind == "signature":
        return SIGNATURE_RETENTION_DAYS  # same as live photo (Hub coding default)
    if kind == "id_image":
        return ID_IMAGE_RETENTION_DAYS
    if kind == "collector_live_photo":
        return COLLECTOR_PHOTO_RETENTION_DAYS
    return None


def compute_retain_until(kind: str, *, rejected: bool = False) -> tuple[Optional[str], Optional[int]]:
    days = retention_days_for_kind(kind, rejected=rejected)
    if days is None:
        return None, None
    until = (datetime.now(TZ) + timedelta(days=days)).date().isoformat()
    return until, days


def stamp_media_retention(media: dict, *, rejected: bool = False) -> dict:
    until, days = compute_retain_until(media.get("kind") or "other", rejected=rejected)
    media["retainUntil"] = until
    media["retainUntilDays"] = days
    return media


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
    rejected = False
    if visitId:
        visit = store.get_visit(visitId)
        if visit and visit.get("schoolId") == user["schoolId"]:
            rejected = visit.get("status") == "rejected"
    data = await file.read()
    key = gen_media_key(kind_val)
    retain_until, retain_days = compute_retain_until(kind_val, rejected=rejected)
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
            "retainUntilDays": retain_days,
        }
    )
    _save_media_bytes(key, data)
    url = f"/v1/media/{key}"
    return {"key": key, "url": url, "meta": {"watermark": WATERMARK}}


@router.get("/{key:path}")
def get_media(key: str, user: CurrentUser):
    m = store.get_media(key)
    disk = _load_media_bytes(key)
    if m and m["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", "Media not found", 404)
    if not m and not disk:
        raise AppError("NOT_FOUND", "Media not found", 404)
    # Allow disk-backed media for this school (survives process restart)
    if not m:
        m = {
            "key": key,
            "schoolId": user["schoolId"],
            "contentType": "image/jpeg",
            "bytes": disk,
        }
    content = m.get("bytes") or disk or b""
    if not content:
        content = b"DEMO_MEDIA_STUB"
    return Response(content=content, media_type=m.get("contentType") or "image/jpeg")
