"""PickupEvent REST — separate entity, not a Visit subtype (P1)."""
from __future__ import annotations

from datetime import datetime
from typing import Optional
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Query

from app.after_hours import stamp_after_hours
from app.auth import assert_gate_allowed, require_roles
from app.blacklist_match import match_blacklist
from app.config import SCHOOL_TZ, WATERMARK
from app.errors import AppError
from app.models import (
    PickupConsentBody,
    PickupCreate,
    PickupOut,
    PickupReleaseBody,
    ReasonBody,
    Role,
)
from app.pickup_match import (
    evaluate_release,
    gate_prompt,
    get_or_default_flag,
    match_authorized_person,
)
from app.util import (
    gen_pickup_id,
    gen_visit_id,
    normalize_mobile,
    now_iso,
    parse_iso,
)
from app import store

router = APIRouter(prefix="/pickups", tags=["pickups"])
TZ = ZoneInfo(SCHOOL_TZ)

_TERMINAL = {
    "Released",
    "BlockedNotAuthorized",
    "BlockedCustody",
    "ReleasedWithOverride",
}
_BLOCKED = {"BlockedNotAuthorized", "BlockedCustody"}


def _meta(row: dict, extra: Optional[dict] = None) -> dict:
    out = {k: v for k, v in row.items() if not k.startswith("_")}
    meta = {"watermark": WATERMARK}
    if extra:
        meta.update(extra)
    student = store.get_student(row.get("studentId") or "")
    person = (
        store.get_authorized_person(row["collectorPickupPersonId"])
        if row.get("collectorPickupPersonId")
        else None
    )
    prompt = gate_prompt(
        row.get("status") or "",
        get_or_default_flag(student) if student else {"gateInstruction": ""},
        student["name"] if student else "",
        row.get("collectorName") or "",
        person.get("relation") if person else None,
        override_requested=bool(row.get("_overrideRequested")),
    )
    if prompt:
        meta["gatePrompt"] = prompt
    out["meta"] = meta
    return out


def _require_pickup(pickup_id: str, user: dict) -> dict:
    p = store.get_pickup(pickup_id)
    if not p or p.get("schoolId") != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Pickup {pickup_id} not found", 404)
    if user["role"] == "gate" and p.get("gateUserId") != user["id"]:
        raise AppError("NOT_FOUND", f"Pickup {pickup_id} not found", 404)
    return p


def _require_mutable(pickup: dict, action: str) -> None:
    if pickup["status"] in _TERMINAL and action != "request-override" and action != "override":
        raise AppError(
            "INVALID_STATE",
            f"Cannot {action} from terminal status {pickup['status']}",
            409,
        )


def _require_active_gate(gate_id: str, school_id: str) -> dict:
    gate = store.get_gate(gate_id)
    if not gate or gate.get("schoolId") != school_id:
        raise AppError("VALIDATION", f"Unknown gateId {gate_id}", 400)
    if not gate.get("active", True):
        raise AppError("VALIDATION", f"Gate {gate_id} is inactive", 400)
    return gate


def _require_media_key(key: Optional[str], school_id: str, field: str) -> None:
    if not key:
        return
    media = store.get_media(key)
    if not media or media.get("schoolId") != school_id:
        raise AppError("VALIDATION", f"Unknown {field}", 400, {"key": key})


def _emit(event: str, pickup: dict, extra: Optional[dict] = None) -> None:
    student = store.get_student(pickup["studentId"])
    payload = {
        "pickupId": pickup["id"],
        "studentId": pickup["studentId"],
        "studentName": student["name"] if student else None,
        "collectorName": pickup.get("collectorName"),
        "collectorMobile": pickup.get("collectorMobile"),
        "status": pickup["status"],
        "gateId": pickup.get("gateId"),
        "gateInstruction": None,
    }
    flag = store.get_custody_flag(pickup["studentId"])
    if flag:
        payload["gateInstruction"] = flag.get("gateInstruction")
    if extra:
        payload.update(extra)
    store.add_outbox(
        {
            "schoolId": pickup["schoolId"],
            "event": event,
            "pickupId": pickup["id"],
            "visitId": pickup.get("linkedVisitId"),
            "payload": payload,
            "channelHints": ["in_app", "sms"],
            "status": "pending",
            "createdAt": now_iso(),
        }
    )


def _try_link_visit(pickup: dict, user: dict) -> None:
    """P2: optional linked Visit only if collector enters beyond lobby."""
    student = store.get_student(pickup["studentId"])
    person = (
        store.get_authorized_person(pickup["collectorPickupPersonId"])
        if pickup.get("collectorPickupPersonId")
        else None
    )
    mobile = normalize_mobile(pickup.get("collectorMobile"))
    photo = pickup.get("collectorLivePhotoRef")
    if not mobile or not photo or not student:
        pickup["visitLinkFailed"] = True
        pickup["linkedVisitId"] = None
        return

    host = store.get_staff("H01")
    if not host or not host.get("active", True):
        pickup["visitLinkFailed"] = True
        pickup["linkedVisitId"] = None
        return

    hit = match_blacklist(
        user["schoolId"],
        mobile,
        person.get("idType") if person else None,
        person.get("idNumber") if person else None,
    )
    if hit and hit.get("severity") == "Block":
        pickup["visitLinkFailed"] = True
        pickup["linkedVisitId"] = None
        return

    ts = now_iso()
    vid = gen_visit_id(store.next_seq("visit_seq"))
    visit = {
        "id": vid,
        "schoolId": user["schoolId"],
        "visitorName": pickup.get("collectorName") or "Pickup collector",
        "mobile": mobile,
        "visitorType": "Parent",
        "purpose": f"Student pickup — {student['name']}",
        "hostId": "H01",
        "livePhotoKey": photo,
        "idType": (person.get("idType") if person else None) or "Other",
        "idNumber": (person.get("idNumber") or person.get("idLast4")) if person else "PICKUP",
        "idImageKey": None,
        "vehicleNumber": None,
        "accompanyingCount": 0,
        "notes": f"Linked from pickup {pickup['id']}",
        "signatureKey": None,
        "gateId": pickup["gateId"],
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
        "blacklistOverrideByUserId": None,
        "meetingDoneAt": None,
        "createdAt": ts,
        "updatedAt": ts,
    }
    stamp_after_hours(visit)
    store.put_visit(visit)
    pickup["linkedVisitId"] = vid
    pickup["visitLinkFailed"] = False


@router.post("", response_model=PickupOut)
def start_pickup(
    body: PickupCreate,
    user: dict = Depends(require_roles(Role.gate)),
):
    student = store.get_student(body.studentId)
    if not student or student.get("schoolId") != user["schoolId"]:
        raise AppError(
            "NOT_FOUND",
            "Student not found — Admin must add the student before pickup",
            404,
        )
    if not student.get("active", True):
        raise AppError("VALIDATION", "Student is inactive — cannot start pickup", 400)

    _require_active_gate(body.gateId, user["schoolId"])
    assert_gate_allowed(user, body.gateId)

    person, method = match_authorized_person(
        user["schoolId"],
        student["id"],
        person_id=body.collectorPickupPersonId,
        mobile=body.collectorMobile,
        id_type=body.idType.value if body.idType else None,
        id_number=body.idNumber,
        id_last4=body.idLast4,
    )
    flag = get_or_default_flag(student)
    ts = now_iso()
    pid = gen_pickup_id(store.next_seq("pickup_seq"))
    collector_name = body.collectorName or (person["name"] if person else "Unknown")
    collector_mobile = normalize_mobile(body.collectorMobile) or (
        person.get("mobile") if person else ""
    ) or ""
    row = {
        "id": pid,
        "schoolId": user["schoolId"],
        "gateId": body.gateId,
        "studentId": student["id"],
        "collectorPickupPersonId": person["id"] if person else None,
        "collectorName": collector_name,
        "collectorMobile": collector_mobile,
        "collectorRelation": person.get("relation") if person else None,
        "matchMethod": method or "none",
        "pickupReason": body.pickupReason.value,
        "reasonOther": (body.reasonOther or "").strip() or None,
        "collectorLivePhotoRef": None,
        "status": "Matching",
        "custodyFlagSnapshot": flag.get("flag") or "none",
        "override": False,
        "overrideByUserId": None,
        "overrideReason": None,
        "linkedVisitId": None,
        "visitLinkFailed": None,
        "releasedAt": None,
        "attemptedAt": ts,
        "gateUserId": user["id"],
        "pickupConsentAt": None,
        "pickupConsentVersion": None,
        "createdAt": ts,
        "updatedAt": ts,
        "_linkVisit": body.linkVisit,
    }
    store.put_pickup(row)
    return _meta(row)


@router.post("/{pickup_id}/consent", response_model=PickupOut)
def record_consent(
    pickup_id: str,
    body: PickupConsentBody,
    user: dict = Depends(require_roles(Role.gate)),
):
    pickup = _require_pickup(pickup_id, user)
    _require_mutable(pickup, "consent")
    ts = now_iso()
    pickup["pickupConsentVersion"] = body.pickupConsentVersion
    pickup["pickupConsentAt"] = body.pickupConsentAt or ts
    pickup["updatedAt"] = ts
    store.put_pickup(pickup)
    return _meta(pickup)


@router.post("/{pickup_id}/release", response_model=PickupOut)
def release_pickup(
    pickup_id: str,
    body: PickupReleaseBody,
    user: dict = Depends(require_roles(Role.gate)),
):
    pickup = _require_pickup(pickup_id, user)
    if pickup["status"] != "Matching":
        raise AppError(
            "INVALID_STATE",
            f"Cannot release from status {pickup['status']}",
            409,
        )
    if not pickup.get("pickupConsentAt") or not pickup.get("pickupConsentVersion"):
        raise AppError(
            "CONSENT_REQUIRED",
            "Pickup consent must be recorded before photo accept / release",
            400,
        )
    _require_media_key(body.collectorLivePhotoRef, user["schoolId"], "collectorLivePhotoRef")
    media = store.get_media(body.collectorLivePhotoRef)
    if media and media.get("kind") not in ("collector_live_photo", "live_photo"):
        raise AppError(
            "VALIDATION",
            "collectorLivePhotoRef must be a collector_live_photo media key",
            400,
            {"kind": media.get("kind")},
        )

    student = store.get_student(pickup["studentId"])
    flag = get_or_default_flag(student) if student else {"flag": "none", "gateInstruction": ""}
    person, method = match_authorized_person(
        user["schoolId"],
        pickup["studentId"],
        person_id=pickup.get("collectorPickupPersonId"),
        mobile=pickup.get("collectorMobile") if not pickup.get("collectorPickupPersonId") else None,
        id_type=None,
        id_number=None,
        id_last4=None,
    )
    if person:
        pickup["collectorPickupPersonId"] = person["id"]
        pickup["collectorRelation"] = person.get("relation")
        pickup["matchMethod"] = pickup.get("matchMethod") if pickup.get("matchMethod") != "none" else method
        if not pickup.get("collectorName"):
            pickup["collectorName"] = person["name"]
        if not pickup.get("collectorMobile"):
            pickup["collectorMobile"] = person.get("mobile") or ""

    status = evaluate_release(person, flag)
    ts = now_iso()
    pickup["collectorLivePhotoRef"] = body.collectorLivePhotoRef
    pickup["custodyFlagSnapshot"] = flag.get("flag") or "none"
    pickup["status"] = status
    pickup["updatedAt"] = ts
    if status == "Released":
        pickup["releasedAt"] = ts
        link = body.linkVisit if body.linkVisit is not None else pickup.get("_linkVisit")
        if link:
            _try_link_visit(pickup, user)

    store.put_pickup(pickup)
    if status == "BlockedCustody":
        _emit("pickup.blocked_custody", pickup)
    return _meta(pickup)


@router.post("/{pickup_id}/request-override", response_model=PickupOut)
def request_override(
    pickup_id: str,
    user: dict = Depends(require_roles(Role.gate)),
):
    pickup = _require_pickup(pickup_id, user)
    if pickup["status"] not in _BLOCKED:
        raise AppError(
            "INVALID_STATE",
            f"Override can only be requested from a blocked status (have {pickup['status']})",
            409,
        )
    pickup["updatedAt"] = now_iso()
    pickup["_overrideRequested"] = True
    store.put_pickup(pickup)
    _emit("pickup.override_requested", pickup)
    return _meta(pickup, extra={"overrideRequested": True})


@router.post("/{pickup_id}/override", response_model=PickupOut)
def override_pickup(
    pickup_id: str,
    body: ReasonBody,
    user: dict = Depends(require_roles(Role.security_head)),
):
    pickup = store.get_pickup(pickup_id)
    if not pickup or pickup.get("schoolId") != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Pickup {pickup_id} not found", 404)
    if pickup["status"] in ("Released", "ReleasedWithOverride"):
        raise AppError(
            "INVALID_STATE",
            f"Cannot override from status {pickup['status']}",
            409,
        )
    if pickup["status"] not in _BLOCKED and pickup["status"] != "Matching":
        raise AppError(
            "INVALID_STATE",
            f"Cannot override from status {pickup['status']}",
            409,
        )
    ts = now_iso()
    pickup["status"] = "ReleasedWithOverride"
    pickup["override"] = True
    pickup["overrideByUserId"] = user["id"]
    pickup["overrideReason"] = body.reason
    pickup["releasedAt"] = ts
    pickup["updatedAt"] = ts
    if pickup.get("_linkVisit"):
        _try_link_visit(pickup, user)
    store.put_pickup(pickup)
    _emit("pickup.override_completed", pickup, {"reason": body.reason})
    return _meta(pickup)


@router.get("")
def list_pickups(
    user: dict = Depends(require_roles(Role.gate, Role.admin, Role.security_head)),
    dateFrom: Optional[str] = None,
    dateTo: Optional[str] = None,
    studentId: Optional[str] = None,
    gateId: Optional[str] = None,
    status: Optional[str] = None,
    override: Optional[bool] = None,
    q: Optional[str] = None,
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
    for p in store.list_pickups(user["schoolId"]):
        if user["role"] == "gate" and p.get("gateUserId") != user["id"]:
            continue
        created = parse_iso(p.get("attemptedAt") or p.get("createdAt"))
        if created:
            cd = created.astimezone(TZ).date()
            if cd < d0 or cd > d1:
                continue
        if studentId and p.get("studentId") != studentId:
            continue
        if gateId and p.get("gateId") != gateId:
            continue
        if status and p.get("status") != status:
            continue
        if override is not None and bool(p.get("override")) != override:
            continue
        if q:
            student = store.get_student(p.get("studentId") or "")
            ql = q.lower()
            blob = " ".join(
                [
                    p.get("id") or "",
                    p.get("collectorName") or "",
                    p.get("collectorMobile") or "",
                    p.get("collectorRelation") or "",
                    student["name"] if student else "",
                    p.get("studentId") or "",
                ]
            ).lower()
            if ql not in blob:
                continue
        rows.append({k: v for k, v in p.items() if not k.startswith("_")})
    rows.sort(key=lambda x: x.get("attemptedAt") or "", reverse=True)
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.get("/{pickup_id}", response_model=PickupOut)
def get_pickup(
    pickup_id: str,
    user: dict = Depends(require_roles(Role.gate, Role.admin, Role.security_head)),
):
    return _meta(_require_pickup(pickup_id, user))
