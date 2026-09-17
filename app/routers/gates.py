from __future__ import annotations

from fastapi import APIRouter

from app.auth import CurrentUser
from app.config import WATERMARK
from app import store

router = APIRouter(prefix="/gates", tags=["gates"])


@router.get("")
def list_gates(user: CurrentUser):
    gates = store.list_gates(user["schoolId"])
    if user.get("role") == "gate" and user.get("gateIds") is not None:
        allowed = set(user["gateIds"])
        gates = [g for g in gates if g["id"] in allowed]
    return {"data": gates, "meta": {"watermark": WATERMARK}}
