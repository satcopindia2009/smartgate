"""Student directory + authorized pickup list + custody flag (P2 §1.4)."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, File, Query, UploadFile

from app.auth import require_roles
from app.config import WATERMARK
from app.errors import AppError
from app.models import (
    AuthorizedPickupCreate,
    AuthorizedPickupPatch,
    CustodyFlagPut,
    Role,
    StudentCreate,
    StudentPatch,
)
from app.pickup_match import (
    default_custody_flag,
    denormalize_blocked_by_custody,
    get_or_default_flag,
)
from app.roster_import import (
    CSV_CONTRACT,
    import_pickup,
    import_students,
    merge_import_results,
    read_upload,
)
from app.util import last4, normalize_mobile, now_iso
from app import store

router = APIRouter(tags=["students"])

_DIR_ROLES = (Role.gate, Role.admin, Role.security_head)
_WRITE_ROLES = (Role.admin, Role.security_head)


def _meta(row: dict, extra: Optional[dict] = None) -> dict:
    out = dict(row)
    meta = {"watermark": WATERMARK}
    if extra:
        meta.update(extra)
    out["meta"] = meta
    return out


def _require_student(student_id: str, school_id: str) -> dict:
    student = store.get_student(student_id)
    if not student or student.get("schoolId") != school_id:
        raise AppError("NOT_FOUND", f"Student {student_id} not found", 404)
    return student


def _require_photo_ref(key: Optional[str], school_id: str) -> None:
    if not key:
        return
    media = store.get_media(key)
    if not media or media.get("schoolId") != school_id:
        raise AppError("VALIDATION", "Unknown photoRef", 400, {"key": key})
    if media.get("kind") not in ("pickup_list_photo", "other"):
        raise AppError(
            "VALIDATION",
            "photoRef must be a pickup_list_photo media key",
            400,
            {"key": key, "kind": media.get("kind")},
        )


def _mask_person_for_gate(person: dict) -> dict:
    out = dict(person)
    if out.get("idNumber"):
        out["idNumber"] = None
    return out


def _custody_for_role(flag: dict, role: str) -> dict:
    out = dict(flag)
    if role == "gate":
        out["blockedPersonIds"] = None
        out["allowedPersonIds"] = None
    return out


@router.get("/students")
def list_students(
    user: dict = Depends(require_roles(*_DIR_ROLES)),
    q: Optional[str] = None,
    class_: Optional[str] = Query(default=None, alias="class"),
    section: Optional[str] = None,
    active: Optional[bool] = None,
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
):
    rows = store.list_students(user["schoolId"])
    if q:
        ql = q.lower()
        rows = [s for s in rows if ql in (s.get("name") or "").lower()]
    if class_ is not None:
        rows = [s for s in rows if s.get("class") == class_]
    if section is not None:
        rows = [s for s in rows if s.get("section") == section]
    if active is not None:
        rows = [s for s in rows if s.get("active") == active]
    rows.sort(key=lambda s: (s.get("class") or "", s.get("section") or "", s.get("name") or ""))
    total = len(rows)
    page = rows[offset : offset + limit]
    return {
        "data": page,
        "meta": {
            "watermark": WATERMARK,
            "total": total,
            "limit": limit,
            "offset": offset,
        },
    }


@router.post("/students")
def create_student(
    body: StudentCreate,
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    ts = now_iso()
    sid = f"STU-{store.next_seq('student_seq'):04d}"
    row = {
        "id": sid,
        "schoolId": user["schoolId"],
        "studentId": body.studentId,
        "name": body.name,
        "class": body.klass,
        "section": body.section,
        "active": body.active,
        "enrollmentEndedAt": body.enrollmentEndedAt,
        "legalHold": body.legalHold,
        "createdAt": ts,
        "updatedAt": ts,
    }
    store.put_student(row)
    store.put_custody_flag(default_custody_flag(sid, user["schoolId"]))
    return _meta(row)


@router.post(
    "/students/import",
    summary="Import students CSV/Excel",
    description=(
        "Admin / Security Head. Multipart field `file` (.csv or .xlsx). "
        f"{CSV_CONTRACT} "
        "Upsert by (JWT schoolId + studentId). Returns `{ created, updated, errors[] }`."
    ),
)
async def import_students_csv(
    file: UploadFile = File(...),
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    data, filename, ctype = await read_upload(file)
    result = import_students(
        data,
        school_id=user["schoolId"],
        user_id=user["id"],
        filename=filename,
        content_type=ctype,
        file_label="students",
    )
    return merge_import_results(result, None)


@router.post(
    "/students/authorized-pickup/import",
    summary="Import authorized pickup CSV/Excel",
    description=(
        "Admin / Security Head. Multipart field `file` (.csv or .xlsx). "
        f"{CSV_CONTRACT} "
        "Upsert by (JWT schoolId + studentId + mobile). Student must already exist. "
        "Returns `{ created, updated, errors[] }`."
    ),
)
async def import_authorized_pickup_csv(
    file: UploadFile = File(...),
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    data, filename, ctype = await read_upload(file)
    result = import_pickup(
        data,
        school_id=user["schoolId"],
        user_id=user["id"],
        filename=filename,
        content_type=ctype,
        file_label="pickup",
    )
    return merge_import_results(None, result)


@router.get("/students/{student_id}")
def get_student(
    student_id: str,
    user: dict = Depends(require_roles(*_DIR_ROLES)),
):
    return _meta(_require_student(student_id, user["schoolId"]))


@router.patch("/students/{student_id}")
def patch_student(
    student_id: str,
    body: StudentPatch,
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    row = _require_student(student_id, user["schoolId"])
    data = body.model_dump(exclude_unset=True)
    if "klass" in data:
        data["class"] = data.pop("klass")
    if data.get("active") is False and not data.get("enrollmentEndedAt") and not row.get(
        "enrollmentEndedAt"
    ):
        data["enrollmentEndedAt"] = now_iso()
    row.update(data)
    row["updatedAt"] = now_iso()
    store.put_student(row)
    return _meta(row)


@router.get("/students/{student_id}/authorized-pickup")
def list_authorized_pickup(
    student_id: str,
    user: dict = Depends(require_roles(*_DIR_ROLES)),
):
    _require_student(student_id, user["schoolId"])
    rows = store.list_authorized_people(user["schoolId"], student_id)
    rows.sort(key=lambda p: p.get("name") or "")
    if user["role"] == "gate":
        rows = [_mask_person_for_gate(p) for p in rows]
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.post("/students/{student_id}/authorized-pickup")
def create_authorized_pickup(
    student_id: str,
    body: AuthorizedPickupCreate,
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    student = _require_student(student_id, user["schoolId"])
    mobile = normalize_mobile(body.mobile)
    if not mobile:
        raise AppError(
            "VALIDATION",
            "Invalid mobile — expected IN 10-digit or E.164",
            400,
        )
    _require_photo_ref(body.photoRef, user["schoolId"])
    ts = now_iso()
    pid = f"APP-{store.next_seq('person_seq'):04d}"
    derived_last4 = body.idLast4 or last4(body.idNumber)
    row = {
        "id": pid,
        "schoolId": user["schoolId"],
        "studentId": student["id"],
        "name": body.name,
        "relation": body.relation.value,
        "mobile": mobile,
        "idType": body.idType.value if body.idType else None,
        "idNumber": body.idNumber,
        "idLast4": derived_last4,
        "photoRef": body.photoRef,
        "active": body.active,
        "effectiveFrom": body.effectiveFrom,
        "effectiveTo": body.effectiveTo,
        "blockedByCustody": False,
        "pickupConsentVersion": body.pickupConsentVersion,
        "pickupConsentAt": body.pickupConsentAt,
        "createdByUserId": user["id"],
        "updatedByUserId": user["id"],
        "createdAt": ts,
        "updatedAt": ts,
    }
    store.put_authorized_person(row)
    denormalize_blocked_by_custody(student["id"], user["schoolId"])
    row = store.get_authorized_person(pid)
    return _meta(row)


@router.patch("/students/{student_id}/authorized-pickup/{person_id}")
def patch_authorized_pickup(
    student_id: str,
    person_id: str,
    body: AuthorizedPickupPatch,
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    _require_student(student_id, user["schoolId"])
    row = store.get_authorized_person(person_id)
    if (
        not row
        or row.get("schoolId") != user["schoolId"]
        or row.get("studentId") != student_id
    ):
        raise AppError("NOT_FOUND", f"Authorized pickup person {person_id} not found", 404)
    data = body.model_dump(exclude_unset=True)
    if "mobile" in data and data["mobile"] is not None:
        mobile = normalize_mobile(data["mobile"])
        if not mobile:
            raise AppError(
                "VALIDATION",
                "Invalid mobile — expected IN 10-digit or E.164",
                400,
            )
        data["mobile"] = mobile
    if "relation" in data and data["relation"] is not None:
        data["relation"] = data["relation"].value
    if "idType" in data and data["idType"] is not None:
        data["idType"] = data["idType"].value
    if "photoRef" in data:
        _require_photo_ref(data["photoRef"], user["schoolId"])
    if "idNumber" in data or "idLast4" in data:
        merged_number = data["idNumber"] if "idNumber" in data else row.get("idNumber")
        merged_last4 = data["idLast4"] if "idLast4" in data else row.get("idLast4")
        data["idLast4"] = merged_last4 or last4(merged_number)
    row.update(data)
    row["updatedByUserId"] = user["id"]
    row["updatedAt"] = now_iso()
    store.put_authorized_person(row)
    denormalize_blocked_by_custody(student_id, user["schoolId"])
    return _meta(store.get_authorized_person(person_id))


@router.get("/students/{student_id}/custody-flag")
def get_custody_flag(
    student_id: str,
    user: dict = Depends(require_roles(*_DIR_ROLES)),
):
    student = _require_student(student_id, user["schoolId"])
    flag = get_or_default_flag(student)
    return _meta(_custody_for_role(flag, user["role"]))


@router.put("/students/{student_id}/custody-flag")
def put_custody_flag(
    student_id: str,
    body: CustodyFlagPut,
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    student = _require_student(student_id, user["schoolId"])
    if body.flag.value == "court_order" and user["role"] != "security_head":
        raise AppError(
            "FORBIDDEN",
            "court_order custody flag write requires security_head",
            403,
        )
    if body.flag.value == "court_order" and not body.gateInstruction:
        raise AppError(
            "VALIDATION",
            "court_order requires a non-empty gateInstruction (F6 fail-closed)",
            400,
        )
    existing = store.get_custody_flag(student_id)
    if (
        existing
        and existing.get("flag") == "court_order"
        and body.flag.value != "court_order"
        and user["role"] != "security_head"
    ):
        raise AppError(
            "FORBIDDEN",
            "Clearing court_order requires security_head",
            403,
        )

    people_ids = {p["id"] for p in store.list_authorized_people(user["schoolId"], student_id)}
    for pid in body.blockedPersonIds:
        if pid not in people_ids:
            raise AppError(
                "VALIDATION",
                f"blockedPersonIds contains unknown person {pid}",
                400,
            )
    if body.allowedPersonIds is not None:
        for pid in body.allowedPersonIds:
            if pid not in people_ids:
                raise AppError(
                    "VALIDATION",
                    f"allowedPersonIds contains unknown person {pid}",
                    400,
                )

    ts = now_iso()
    row = {
        "studentId": student["id"],
        "schoolId": user["schoolId"],
        "flag": body.flag.value,
        "gateInstruction": body.gateInstruction,
        "blockedPersonIds": list(body.blockedPersonIds),
        "allowedPersonIds": None
        if body.allowedPersonIds is None
        else list(body.allowedPersonIds),
        "updatedByUserId": user["id"],
        "updatedAt": ts,
    }
    store.put_custody_flag(row)
    denormalize_blocked_by_custody(student["id"], user["schoolId"])
    return _meta(_custody_for_role(row, user["role"]))
