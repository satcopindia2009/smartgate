from __future__ import annotations

from fastapi import APIRouter

from app.auth import CurrentUser
from app.config import WATERMARK
from app import store

router = APIRouter(prefix="/gates", tags=["gates"])


@router.get("")
def list_gates(user: CurrentUser):
    gates = store.list_gates(user["schoolId"])
    return {"data": gates, "meta": {"watermark": WATERMARK}}
