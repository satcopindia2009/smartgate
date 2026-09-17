"""Zone labels — fixed keys, school-renamable display (Hub B4)."""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.auth import CurrentUser, require_roles
from app.config import WATERMARK
from app.escort import ZONE_KEYS, seed_school_defaults
from app.errors import AppError
from app.models import Role, ZoneLabelPatch
from app.util import now_iso
from app import store

router = APIRouter(prefix="/zones", tags=["zones"])

_WRITE = (Role.admin, Role.security_head)


def _public(row: dict) -> dict:
    out = {
        "key": row["key"],
        "label": row["label"],
        "schoolId": row["schoolId"],
        "updatedByUserId": row.get("updatedByUserId"),
        "updatedAt": row.get("updatedAt"),
    }
    out["meta"] = {"watermark": WATERMARK}
    return out


@router.get("")
def list_zones(user: CurrentUser):
    seed_school_defaults(user["schoolId"])
    rows = [_public(r) for r in store.list_zones(user["schoolId"])]
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.patch("/{key}")
def patch_zone(
    key: str,
    body: ZoneLabelPatch,
    user: dict = Depends(require_roles(*_WRITE)),
):
    if key not in ZONE_KEYS:
        raise AppError(
            "NOT_FOUND",
            f"Zone key '{key}' is not in the starter enum",
            404,
            {"allowed": list(ZONE_KEYS)},
        )
    seed_school_defaults(user["schoolId"])
    row = store.get_zone(user["schoolId"], key)
    if not row:
        raise AppError("NOT_FOUND", f"Zone {key} not found", 404)
    row["label"] = body.label
    row["updatedByUserId"] = user["id"]
    row["updatedAt"] = now_iso()
    store.put_zone(row)
    return _public(row)
