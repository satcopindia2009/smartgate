from __future__ import annotations

from fastapi import APIRouter, Depends

from app.auth import require_roles
from app.config import WATERMARK
from app.errors import AppError
from app.models import PassScanBody, Role
from app import store
from app.routers import visits as visits_router

router = APIRouter(prefix="/passes", tags=["passes"])


@router.get("/{pass_id}")
def get_pass(pass_id: str, user=Depends(require_roles(Role.gate, Role.host, Role.admin, Role.security_head))):
    p = store.get_pass(pass_id)
    if not p or p["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Pass {pass_id} not found", 404)
    v = store.get_visit(p["visitId"])
    if not v:
        raise AppError("NOT_FOUND", "Visit for pass missing", 404)
    host = store.get_staff(v["hostId"])
    gate = store.get_gate(v.get("gateInId") or v["gateId"])
    photo_url = f"/v1/media/{v['livePhotoKey']}" if v.get("livePhotoKey") else None
    return {
        "passId": p["passId"],
        "visitId": v["id"],
        "schoolId": v["schoolId"],
        "visitorName": v["visitorName"],
        "photoUrl": photo_url,
        "hostId": v["hostId"],
        "hostName": host["name"] if host else None,
        "gateId": gate["id"] if gate else v["gateId"],
        "gateName": gate["name"] if gate else None,
        "status": v["status"],
        "qrToken": p.get("token"),
        "issuedAt": p.get("issuedAt"),
        "expiresAt": p.get("expiresAt"),
        "revoked": p.get("revoked", False),
        "meta": {"watermark": WATERMARK},
    }


@router.post("/scan")
def scan_pass(
    body: PassScanBody,
    user: dict = Depends(require_roles(Role.gate)),
):
    if not body.token and not body.passId:
        raise AppError("VALIDATION", "token or passId required", 400)
    p = None
    if body.passId:
        p = store.get_pass(body.passId)
    elif body.token:
        p = store.get_pass_by_token(body.token)
    if not p or p["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", "Pass not found", 404)
    if p.get("revoked"):
        raise AppError("PASS_REVOKED", "Pass has been revoked", 410)
    visit_id = p["visitId"]
    if body.action == "check_in":
        return visits_router.do_check_in(visit_id, user, body.gateId)
    return visits_router.do_check_out(visit_id, user, body.gateId)
