from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends

from app.auth import assert_gate_allowed, require_roles
from app.config import WATERMARK
from app.escort import allowed_zone_labels, escort_name
from app.errors import AppError
from app.models import PassOut, PassScanBody, Role
from app.util import parse_iso
from app import store
from app.routers import visits as visits_router

router = APIRouter(prefix="/passes", tags=["passes"])


def _resolve_pass(body: PassScanBody, school_id: str) -> dict:
    p = None
    if body.passId:
        p = store.get_pass(body.passId)
    elif body.token:
        p = store.get_pass_by_token(body.token)
    if not p or p["schoolId"] != school_id:
        raise AppError("PASS_NOT_FOUND", "Pass not found", 404)
    if p.get("revoked"):
        raise AppError("PASS_REVOKED", "Pass has been revoked", 410)
    expires = parse_iso(p.get("expiresAt"))
    if expires and expires < datetime.now(expires.tzinfo):
        raise AppError("PASS_EXPIRED", "Pass has expired", 410)
    return p


@router.get("/{pass_id}", response_model=PassOut)
def get_pass(pass_id: str, user=Depends(require_roles(Role.gate, Role.host, Role.admin, Role.security_head))):
    p = store.get_pass(pass_id)
    if not p or p["schoolId"] != user["schoolId"]:
        raise AppError("PASS_NOT_FOUND", f"Pass {pass_id} not found", 404)
    v = store.get_visit(p["visitId"])
    if not v:
        raise AppError("NOT_FOUND", "Visit for pass missing", 404)
    if user["role"] == "host" and user.get("staffId") != v.get("hostId"):
        raise AppError("FORBIDDEN", "Host may only view own visit passes", 403)
    host = store.get_staff(v["hostId"])
    gate = store.get_gate(v.get("gateInId") or v["gateId"], v.get("schoolId"))
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
        "escortRequired": bool(v.get("escortRequired")),
        "escortName": escort_name(v),
        "allowedZoneLabels": allowed_zone_labels(
            v["schoolId"], list(v.get("allowedZones") or [])
        ),
        "afterHours": bool(v.get("afterHours")),
        "policyTrigger": v.get("policyTrigger"),
        "meta": {"watermark": WATERMARK},
    }


@router.post("/scan")
def scan_pass(
    body: PassScanBody,
    user: dict = Depends(require_roles(Role.gate)),
):
    p = _resolve_pass(body, user["schoolId"])
    v = store.get_visit(p["visitId"])
    if not v:
        raise AppError("NOT_FOUND", "Visit for pass missing", 404)
    gid = body.gateId or (v.get("gateInId") if body.action == "check_out" else v.get("gateId"))
    if gid:
        assert_gate_allowed(user, gid)
    if body.action == "check_in":
        return visits_router.do_check_in(p["visitId"], user, body.gateId)
    return visits_router.do_check_out(p["visitId"], user, body.gateId)
