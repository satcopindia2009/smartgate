from __future__ import annotations

from datetime import datetime, timedelta
from typing import Optional
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Query

from app.after_hours import stamp_after_hours
from app.auth import CurrentUser, assert_gate_allowed, require_roles
from app.config import SCHOOL_TZ, WATERMARK
from app.escort import assert_escort_ready, escort_name, stamp_escort_zones
from app.inside import list_inside_visits
from app.errors import AppError
from app.models import (
    ApproveBody,
    AssignEscortBody,
    CheckInBody,
    ForceCheckoutBody,
    RejectBody,
    Role,
    VisitCreate,
    WaiveEscortBody,
)
from app.util import (
    gen_pass_id,
    gen_qr_token,
    gen_visit_id,
    normalize_mobile,
    now_iso,
    parse_iso,
)
from app import store
from app.blacklist_match import match_blacklist as match_blacklist_internal

router = APIRouter(prefix="/visits", tags=["visits"])
TZ = ZoneInfo(SCHOOL_TZ)


def _visit_public(v: dict) -> dict:
    out = dict(v)
    out["escortName"] = escort_name(out)
    return out


def _meta_visit(v: dict) -> dict:
    out = _visit_public(v)
    out["meta"] = {"watermark": WATERMARK}
    return out


def _emit(event: str, visit: dict, extra: Optional[dict] = None) -> None:
    host = store.get_staff(visit["hostId"])
    payload = {
        "visitorName": visit["visitorName"],
        "purpose": visit["purpose"],
        "hostId": visit["hostId"],
        "hostPhone": host.get("mobile") if host else None,
        "passId": visit.get("passId"),
        "status": visit["status"],
    }
    if visit.get("afterHours"):
        payload["afterHours"] = True
        payload["policyTrigger"] = visit.get("policyTrigger")
        payload["hostFyi"] = True
    if extra:
        payload.update(extra)
    hints = ["in_app"]
    if event == "visit.pending" and visit.get("afterHours"):
        hints = ["in_app", "security_head"]
    store.add_outbox(
        {
            "schoolId": visit["schoolId"],
            "event": event,
            "visitId": visit["id"],
            "payload": payload,
            "channelHints": hints,
            "status": "pending",
            "createdAt": now_iso(),
        }
    )


def _after_hours_sh_required(visit: dict) -> None:
    raise AppError(
        "AFTER_HOURS_SH_REQUIRED",
        "After hours / holiday — Security Head approval required",
        403,
        {
            "afterHours": True,
            "policyTrigger": visit.get("policyTrigger"),
            "afterHoursEvaluatedAt": visit.get("afterHoursEvaluatedAt"),
        },
    )


def _host_owns(user: dict, visit: dict) -> bool:
    return user["role"] == "host" and user.get("staffId") == visit["hostId"]


def _can_view_visit(user: dict, visit: dict) -> bool:
    if visit["schoolId"] != user["schoolId"]:
        return False
    if user["role"] in ("admin", "security_head", "gate"):
        return True
    if user["role"] == "host":
        return _host_owns(user, visit)
    return False


def _require_id_fields(body: VisitCreate) -> None:
    if not body.idNumber and not body.idImageKey:
        raise AppError(
            "VALIDATION",
            "One of idNumber or idImageKey is required",
            400,
        )


def _require_media_key(key: Optional[str], school_id: str, field: str) -> None:
    if not key:
        return
    media = store.get_media(key)
    if not media or media.get("schoolId") != school_id:
        raise AppError("VALIDATION", f"Unknown {field}", 400, {"key": key})


def _require_active_gate(gate_id: str, school_id: str) -> dict:
    gate = store.get_gate(gate_id, school_id)
    if not gate or gate.get("schoolId") != school_id:
        raise AppError("VALIDATION", f"Unknown gateId {gate_id}", 400)
    if not gate.get("active", True):
        raise AppError("VALIDATION", f"Gate {gate_id} is inactive", 400)
    return gate


def _require_active_host(host_id: str, school_id: str) -> dict:
    staff = store.get_staff(host_id)
    if not staff or staff.get("schoolId") != school_id:
        raise AppError("VALIDATION", f"Unknown hostId {host_id}", 400)
    if not staff.get("active", True):
        raise AppError("VALIDATION", f"Host {host_id} is inactive", 400)
    return staff


def _block_without_override(visit: dict, action: str) -> None:
    if not visit.get("blacklistHit") or not visit.get("blacklistId"):
        return
    if visit.get("blacklistOverrideByUserId"):
        return
    bl = store.get_blacklist(visit["blacklistId"])
    if bl and bl.get("severity") == "Block":
        raise AppError(
            "BLACKLIST_BLOCK",
            f"Cannot {action} Block hit without security_head override on visit",
            403,
            {"blacklistId": bl["id"], "severity": bl.get("severity")},
        )


@router.get("/inside")
def visits_inside(
    user: CurrentUser,
    gateId: Optional[str] = None,
    visitorType: Optional[str] = None,
    hostId: Optional[str] = None,
    overdueOnly: bool = False,
    q: Optional[str] = None,
    afterHours: Optional[bool] = None,
    policyTrigger: Optional[str] = None,
):
    school = store.get_school(user["schoolId"]) or store.school()
    overdue_h = school.get("overdueHoursDefault", 4) if school else 4
    now = datetime.now(TZ)
    rows = []
    for v in list_inside_visits(user["schoolId"]):
        if user["role"] == "host" and not _host_owns(user, v):
            continue
        if gateId and v.get("gateInId") != gateId and v.get("gateId") != gateId:
            continue
        if visitorType and v.get("visitorType") != visitorType:
            continue
        if hostId and v.get("hostId") != hostId:
            continue
        if afterHours is not None and bool(v.get("afterHours")) != afterHours:
            continue
        if policyTrigger and v.get("policyTrigger") != policyTrigger:
            continue
        if q:
            ql = q.lower()
            blob = " ".join(
                [
                    v.get("visitorName") or "",
                    v.get("mobile") or "",
                    v.get("passId") or "",
                    v.get("id") or "",
                ]
            ).lower()
            if ql not in blob:
                continue
        tin = parse_iso(v.get("timeIn"))
        overdue = False
        if tin:
            overdue = now - tin >= timedelta(hours=overdue_h)
        if overdueOnly and not overdue:
            continue
        item = _visit_public(v)
        item["overdue"] = overdue
        rows.append(item)
    rows.sort(key=lambda x: x.get("timeIn") or "", reverse=True)
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.get("")
def list_visits(
    user: CurrentUser,
    dateFrom: Optional[str] = None,
    dateTo: Optional[str] = None,
    gateId: Optional[str] = None,
    visitorType: Optional[str] = None,
    hostId: Optional[str] = None,
    status: Optional[str] = None,
    checkoutStatus: Optional[str] = None,
    blacklistHit: Optional[bool] = None,
    q: Optional[str] = None,
    afterHours: Optional[bool] = None,
    policyTrigger: Optional[str] = None,
):
    today = datetime.now(TZ).date()
    if not dateFrom:
        dateFrom = today.isoformat()
    if not dateTo:
        dateTo = today.isoformat()
    try:
        d0 = datetime.fromisoformat(dateFrom).date()
        d1 = datetime.fromisoformat(dateTo).date()
    except ValueError as e:
        raise AppError("VALIDATION", "Invalid dateFrom/dateTo", 400) from e
    if (d1 - d0).days > 90:
        raise AppError("VALIDATION", "Date range max 90 days", 400)

    rows = []
    for v in store.list_visits(user["schoolId"]):
        if user["role"] == "host" and not _host_owns(user, v):
            continue
        created = parse_iso(v.get("createdAt"))
        if created:
            cd = created.astimezone(TZ).date()
            if cd < d0 or cd > d1:
                continue
        if gateId and v.get("gateId") != gateId and v.get("gateInId") != gateId:
            continue
        if visitorType and v.get("visitorType") != visitorType:
            continue
        if hostId and v.get("hostId") != hostId:
            continue
        if status and v.get("status") != status:
            continue
        if checkoutStatus:
            ct = v.get("checkoutType")
            if checkoutStatus == "never" and ct not in (None, "never"):
                continue
            if checkoutStatus != "never" and ct != checkoutStatus:
                continue
        if blacklistHit is not None and bool(v.get("blacklistHit")) != blacklistHit:
            continue
        if afterHours is not None and bool(v.get("afterHours")) != afterHours:
            continue
        if policyTrigger and v.get("policyTrigger") != policyTrigger:
            continue
        if q:
            ql = q.lower()
            blob = " ".join(
                [
                    v.get("visitorName") or "",
                    v.get("mobile") or "",
                    v.get("passId") or "",
                    v.get("id") or "",
                ]
            ).lower()
            if ql not in blob:
                continue
        rows.append(_visit_public(v))
    rows.sort(key=lambda x: x.get("createdAt") or "", reverse=True)
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.get("/{visit_id}")
def get_visit(visit_id: str, user: CurrentUser):
    v = store.get_visit(visit_id)
    if not v or not _can_view_visit(user, v):
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    return _meta_visit(v)


@router.post("")
def create_visit(
    body: VisitCreate,
    user: dict = Depends(require_roles(Role.gate, Role.security_head)),
):
    _require_id_fields(body)
    _require_active_gate(body.gateId, user["schoolId"])
    _require_active_host(body.hostId, user["schoolId"])
    assert_gate_allowed(user, body.gateId)
    _require_media_key(body.livePhotoKey, user["schoolId"], "livePhotoKey")
    _require_media_key(body.idImageKey, user["schoolId"], "idImageKey")
    _require_media_key(body.signatureKey, user["schoolId"], "signatureKey")

    mobile = normalize_mobile(body.mobile)
    if not mobile:
        raise AppError(
            "VALIDATION",
            "Invalid mobile — expected IN 10-digit or E.164",
            400,
        )

    hit = match_blacklist_internal(
        user["schoolId"], mobile, body.idType.value if body.idType else None, body.idNumber
    )

    if hit and hit["severity"] == "Block" and not body.blacklistOverride:
        raise AppError(
            "BLACKLIST_BLOCK",
            "Visitor matches blacklist Block entry; override required",
            403,
            {"blacklistId": hit["id"], "severity": hit["severity"]},
        )
    if hit and hit["severity"] == "Block" and body.blacklistOverride:
        if user["role"] != "security_head":
            raise AppError(
                "FORBIDDEN",
                "Blacklist Block override requires security_head",
                403,
            )

    seq = store.next_seq("visit_seq")
    ts = now_iso()
    vid = gen_visit_id(seq)
    visit = {
        "id": vid,
        "schoolId": user["schoolId"],
        "visitorName": body.visitorName,
        "mobile": mobile,
        "visitorType": body.visitorType.value,
        "purpose": body.purpose,
        "hostId": body.hostId,
        "livePhotoKey": body.livePhotoKey,
        "idType": body.idType.value,
        "idNumber": body.idNumber,
        "idImageKey": body.idImageKey,
        "vehicleNumber": body.vehicleNumber,
        "accompanyingCount": body.accompanyingCount,
        "notes": body.notes,
        "signatureKey": body.signatureKey,
        "gateId": body.gateId,
        "registeredByUserId": user["id"],
        "status": "pending",
        "rejectReason": None,
        "decidedAt": None,
        "decidedByUserId": None,
        "passId": None,
        "qrToken": None,
        "timeIn": None,
        "timeOut": None,
        "gateInId": None,
        "gateOutId": None,
        "checkoutType": None,
        "forceCheckoutReason": None,
        "forceCheckoutByUserId": None,
        "blacklistHit": bool(hit),
        "blacklistId": hit["id"] if hit else None,
        "blacklistOverrideByUserId": user["id"]
        if hit and hit["severity"] == "Block" and body.blacklistOverride
        else None,
        "meetingDoneAt": None,
        "createdAt": ts,
        "updatedAt": ts,
    }
    stamp_after_hours(visit)
    stamp_escort_zones(visit)
    store.put_visit(visit)
    _emit("visit.pending", visit, {"blacklistHit": bool(hit)})
    if hit:
        _emit("blacklist.hit", visit, {"blacklistId": hit["id"], "severity": hit["severity"]})
    return _meta_visit(visit)


@router.post("/{visit_id}/approve")
def approve_visit(
    visit_id: str,
    body: ApproveBody = ApproveBody(),
    user: dict = Depends(require_roles(Role.host, Role.admin, Role.security_head)),
):
    v = store.get_visit(visit_id)
    if not v or v["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if user["role"] == "host" and not _host_owns(user, v):
        raise AppError("FORBIDDEN", "Host may only approve own visits", 403)
    if v["status"] != "pending":
        raise AppError("INVALID_STATE", f"Cannot approve from status {v['status']}", 409)

    _block_without_override(v, "approve")

    # A3/A4: sticky afterHours — do not re-evaluate; Host/Admin Approve is no-op
    if v.get("afterHours"):
        if user["role"] != "security_head":
            _after_hours_sh_required(v)
        if not body.reason:
            raise AppError(
                "VALIDATION",
                "reason is required for Security Head after-hours approve",
                400,
            )

    ts = now_iso()
    pass_id = gen_pass_id()
    # Walkthrough convenience: if this is a Priya-like create, keep story — but seed already has P-4F21
    token = gen_qr_token()
    v["status"] = "approved"
    v["decidedAt"] = ts
    v["decidedByUserId"] = user["id"]
    v["passId"] = pass_id
    v["qrToken"] = token
    v["updatedAt"] = ts
    if v.get("afterHours"):
        v["afterHoursApproveReason"] = body.reason
    store.put_visit(v)
    store.put_pass(
        {
            "passId": pass_id,
            "token": token,
            "visitId": v["id"],
            "schoolId": v["schoolId"],
            "issuedAt": ts,
            "expiresAt": None,
            "revoked": False,
        }
    )
    _emit("visit.approved", v)
    return _meta_visit(v)


@router.post("/{visit_id}/reject")
def reject_visit(
    visit_id: str,
    body: RejectBody,
    user: dict = Depends(require_roles(Role.host, Role.admin, Role.security_head)),
):
    v = store.get_visit(visit_id)
    if not v or v["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if user["role"] == "host" and not _host_owns(user, v):
        raise AppError("FORBIDDEN", "Host may only reject own visits", 403)
    if v["status"] != "pending":
        raise AppError("INVALID_STATE", f"Cannot reject from status {v['status']}", 409)
    # A4/A6: after-hours reject is SH-only; reason already required by RejectBody
    if v.get("afterHours") and user["role"] != "security_head":
        _after_hours_sh_required(v)
    ts = now_iso()
    v["status"] = "rejected"
    v["rejectReason"] = body.reason
    v["decidedAt"] = ts
    v["decidedByUserId"] = user["id"]
    v["checkoutType"] = "never"
    v["updatedAt"] = ts
    v["escortSuggestedByHost"] = None
    if v.get("passId"):
        p = store.get_pass(v["passId"])
        if p:
            p["revoked"] = True
            store.put_pass(p)
    store.put_visit(v)
    _emit("visit.rejected", v, {"reason": body.reason})
    return _meta_visit(v)


@router.post("/{visit_id}/check-in")
def check_in(
    visit_id: str,
    body: CheckInBody = CheckInBody(),
    user: dict = Depends(require_roles(Role.gate)),
):
    return _do_check_in(visit_id, user, body.gateId)


def _do_check_in(visit_id: str, user: dict, gate_id: Optional[str] = None) -> dict:
    v = store.get_visit(visit_id)
    if not v or v["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if v["status"] != "approved":
        raise AppError("INVALID_STATE", f"Cannot check-in from status {v['status']}", 409)
    _block_without_override(v, "check-in")
    assert_escort_ready(v, "check-in")
    gid = gate_id or v["gateId"]
    _require_active_gate(gid, user["schoolId"])
    assert_gate_allowed(user, gid)
    ts = now_iso()
    v["status"] = "inside"
    v["timeIn"] = ts
    v["gateInId"] = gid
    v["updatedAt"] = ts
    store.put_visit(v)
    _emit("visit.checked_in", v)
    return _meta_visit(v)


@router.post("/{visit_id}/check-out")
def check_out(
    visit_id: str,
    user: dict = Depends(require_roles(Role.gate)),
):
    return _do_check_out(visit_id, user, None)


def _do_check_out(visit_id: str, user: dict, gate_id: Optional[str] = None) -> dict:
    v = store.get_visit(visit_id)
    if not v or v["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if v["status"] != "inside":
        raise AppError("INVALID_STATE", f"Cannot check-out from status {v['status']}", 409)
    gid = gate_id or v.get("gateInId") or v["gateId"]
    _require_active_gate(gid, user["schoolId"])
    assert_gate_allowed(user, gid)
    ts = now_iso()
    v["status"] = "completed"
    v["timeOut"] = ts
    v["gateOutId"] = gid
    v["checkoutType"] = "normal"
    v["escortClearedAt"] = ts
    v["updatedAt"] = ts
    store.put_visit(v)
    _emit("visit.checked_out", v)
    return _meta_visit(v)


@router.post("/{visit_id}/force-checkout")
def force_checkout(
    visit_id: str,
    body: ForceCheckoutBody,
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    v = store.get_visit(visit_id)
    if not v or v["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if v["status"] != "inside":
        raise AppError("INVALID_STATE", f"Cannot force-checkout from status {v['status']}", 409)
    ts = now_iso()
    v["status"] = "force_completed"
    v["timeOut"] = ts
    v["gateOutId"] = v.get("gateInId") or v["gateId"]
    v["checkoutType"] = "force"
    v["forceCheckoutReason"] = body.reason
    v["forceCheckoutByUserId"] = user["id"]
    v["escortClearedAt"] = ts
    v["updatedAt"] = ts
    store.put_visit(v)
    _emit("visit.force_checkout", v, {"reason": body.reason})
    return _meta_visit(v)


@router.post("/{visit_id}/meeting-done")
def meeting_done(
    visit_id: str,
    user: dict = Depends(require_roles(Role.host, Role.admin, Role.security_head)),
):
    v = store.get_visit(visit_id)
    if not v or v["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if user["role"] == "host" and not _host_owns(user, v):
        raise AppError("FORBIDDEN", "Host may only mark own visits", 403)
    if v["status"] not in ("approved", "inside"):
        raise AppError("INVALID_STATE", f"Cannot meeting-done from status {v['status']}", 409)
    ts = now_iso()
    v["meetingDoneAt"] = ts
    v["updatedAt"] = ts
    store.put_visit(v)
    return _meta_visit(v)


_ESCORT_ASSIGN_STATUSES = ("pending", "approved", "inside")


def _require_active_escort_staff(staff_id: str, school_id: str) -> dict:
    staff = store.get_staff(staff_id)
    if not staff or staff.get("schoolId") != school_id:
        raise AppError("VALIDATION", f"Unknown escortStaffId {staff_id}", 400)
    if not staff.get("active", True):
        raise AppError("VALIDATION", f"Escort staff {staff_id} is inactive", 400)
    return staff


@router.post("/{visit_id}/assign-escort")
def assign_escort(
    visit_id: str,
    body: AssignEscortBody,
    user: dict = Depends(require_roles(Role.gate, Role.host, Role.security_head)),
):
    v = store.get_visit(visit_id)
    if not v or not _can_view_visit(user, v):
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if v["status"] not in _ESCORT_ASSIGN_STATUSES:
        raise AppError(
            "INVALID_STATE",
            f"Cannot assign escort from status {v['status']}",
            409,
        )
    ts = now_iso()
    if user["role"] == "host":
        suggested = body.escortSuggestedByHost or body.escortStaffId
        if not suggested:
            raise AppError("VALIDATION", "escortStaffId is required", 400)
        _require_active_escort_staff(suggested, user["schoolId"])
        v["escortSuggestedByHost"] = suggested
        v["updatedAt"] = ts
        store.put_visit(v)
        return _meta_visit(v)

    if not body.escortStaffId:
        raise AppError("VALIDATION", "escortStaffId is required", 400)
    _require_active_escort_staff(body.escortStaffId, user["schoolId"])
    v["escortStaffId"] = body.escortStaffId
    if body.escortSuggestedByHost:
        _require_active_escort_staff(body.escortSuggestedByHost, user["schoolId"])
        v["escortSuggestedByHost"] = body.escortSuggestedByHost
    v["updatedAt"] = ts
    store.put_visit(v)
    return _meta_visit(v)


@router.post("/{visit_id}/waive-escort")
def waive_escort(
    visit_id: str,
    body: WaiveEscortBody,
    user: dict = Depends(require_roles(Role.security_head)),
):
    v = store.get_visit(visit_id)
    if not v or v["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Visit {visit_id} not found", 404)
    if v["status"] not in _ESCORT_ASSIGN_STATUSES:
        raise AppError(
            "INVALID_STATE",
            f"Cannot waive escort from status {v['status']}",
            409,
        )
    ts = now_iso()
    v["escortWaived"] = True
    v["escortWaiveReason"] = body.reason
    v["updatedAt"] = ts
    store.put_visit(v)
    return _meta_visit(v)


# Export helpers for passes router
do_check_in = _do_check_in
do_check_out = _do_check_out
