"""School tenant create / me + blast toggle (P2 E3) + roster import."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, File, Query, UploadFile

from app.auth import CurrentUser, require_roles, require_school_create
from app.blast import school_blast_config
from app.config import (
    BLAST_DEFAULT_STAFF_CHANNELS,
    BLAST_DEFAULT_VISITOR_CHANNELS,
    PRANAY_SCHOOL_CODE,
    PRANAY_SCHOOL_ID,
    PRANAY_SCHOOL_NAME,
    RESERVED_SCHOOL_ID,
    WATERMARK,
)
from app.errors import AppError
from app.models import BlastChannel, BlastConfigPatch, Role, SchoolCreate
from app.roster_import import (
    CSV_CONTRACT,
    import_locked_rows,
    import_pickup,
    import_students,
    merge_import_results,
    read_upload,
)
from app.school_bootstrap import create_school, public_school
from app.util import now_iso
from app import store

router = APIRouter(prefix="/schools", tags=["schools"])

_BLAST_ROLES = (Role.admin, Role.security_head)
_WRITE_ROLES = (Role.admin, Role.security_head)
_ALLOWED_VISITOR = {BlastChannel.sms.value, BlastChannel.whatsapp.value}
_ALLOWED_STAFF = {BlastChannel.in_app.value, BlastChannel.push.value}

_CREATE_DESC = (
    "Create a **new** school tenant (not SCH-DEMO-01). "
    "Admin / Security Head JWT **or** documented header `X-Bootstrap-Token: satcop-school-bootstrap`. "
    "Auto-seeds 4 gates (G-MAIN / G-PED / G-STAFF / G-BUS), Mon–Fri 08:00–18:00 campus hours "
    "(weekend closed, empty holidays), EscortZoneRule defaults (Vendor escort ON reception+admin), "
    "and Admin + Security Head users. Generated credentials are returned **once**. "
    f"`{RESERVED_SCHOOL_ID}` is reserved and cannot be created or overwritten. "
    f"**Locked first real tenant:** schoolId `{PRANAY_SCHOOL_ID}` · school_code `{PRANAY_SCHOOL_CODE}` "
    f"· name `{PRANAY_SCHOOL_NAME}` (ops seed `pranay.admin` / `pranay.sh` / `pranay.gate`, "
    "gates PS-G-MAIN/PED/STAFF/BUS). "
    "`schoolCode=PRANAY` or that name maps to SCH-PRANAY-01; never `SCH-PRANAY-PUNE-01`."
)
_IMPORT_DESC = (
    "Admin / Security Head multipart roster import for the JWT school. "
    "Preferred field: `file` (.csv / .xlsx) using the **EXACT** locked snake_case template "
    "(one row = one authorized person; no thinner camelCase headers). "
    "Optional split fields `students` + `pickup` use the same locked headers. "
    f"{CSV_CONTRACT} "
    "`mode=validate` is dry-run (no writes); `mode=commit` applies upserts. "
    "Audit returns who/when/filename/counts. Court-document columns are rejected. "
    f"Does not wipe {RESERVED_SCHOOL_ID}. First real tenant is SCH-PRANAY-01 / school_code PRANAY."
)


def _config_public(school: dict) -> dict:
    out = school_blast_config(school)
    out["meta"] = {"watermark": WATERMARK}
    return out


def _validate_channels(values: list[str] | None, allowed: set[str], field: str) -> list[str] | None:
    if values is None:
        return None
    cleaned: list[str] = []
    for raw in values:
        ch = (raw or "").strip()
        if ch not in allowed:
            raise AppError(
                "VALIDATION",
                f"Unsupported {field} channel '{ch}'",
                400,
                {"allowed": sorted(allowed)},
            )
        if ch not in cleaned:
            cleaned.append(ch)
    if not cleaned:
        raise AppError("VALIDATION", f"{field} must not be empty", 400)
    return cleaned


def _require_user_school(user: dict) -> dict:
    school = store.get_school(user["schoolId"])
    if not school:
        raise AppError("NOT_FOUND", "School not found", 404)
    return school


@router.post(
    "",
    summary="Create school tenant",
    description=_CREATE_DESC,
)
def post_school(
    body: SchoolCreate,
    actor: dict = Depends(require_school_create),
):
    created = create_school(
        name=body.name,
        timezone=body.timezone,
        slug=body.slug,
        school_code=body.schoolCode,
        admin_password=body.adminPassword,
        security_head_password=body.securityHeadPassword,
        created_by_user_id=actor.get("id"),
    )
    created["meta"] = {
        "watermark": WATERMARK,
        "reservedSchoolId": RESERVED_SCHOOL_ID,
        "credentialsOnce": True,
        "note": (
            f"{RESERVED_SCHOOL_ID} demo seed is reserved and was not modified. "
            f"Locked first real tenant is {PRANAY_SCHOOL_ID} / {PRANAY_SCHOOL_CODE}. "
            "Never SCH-PRANAY-PUNE-01. "
            "Store generated credentials now — they are not returned again."
        ),
        "pranaySchoolId": PRANAY_SCHOOL_ID,
        "pranaySchoolCode": PRANAY_SCHOOL_CODE,
    }
    return created


@router.get(
    "/me",
    summary="Current school from JWT",
    description=(
        "Returns the school bound to the access token. Any authenticated role. "
        f"First real tenant JWT (`pranay.admin` / `pranay.sh` / `pranay.gate`) is "
        f"schoolId `{PRANAY_SCHOOL_ID}` / school_code `{PRANAY_SCHOOL_CODE}` "
        f"({PRANAY_SCHOOL_NAME}). `{RESERVED_SCHOOL_ID}` is the separate demo seed."
    ),
)
def get_school_me(user: CurrentUser):
    school = _require_user_school(user)
    out = public_school(school)
    out["meta"] = {"watermark": WATERMARK, "reservedSchoolId": RESERVED_SCHOOL_ID}
    return out


@router.post(
    "/me/roster/import",
    summary="Import students + authorized pickup CSV/Excel",
    description=_IMPORT_DESC,
)
async def import_roster(
    file: Optional[UploadFile] = File(default=None),
    students: Optional[UploadFile] = File(default=None),
    pickup: Optional[UploadFile] = File(default=None),
    mode: str = Query(default="commit", description="validate = dry-run; commit = write"),
    user: dict = Depends(require_roles(*_WRITE_ROLES)),
):
    _require_user_school(user)
    mode_n = (mode or "commit").strip().lower()
    if mode_n not in {"validate", "commit"}:
        raise AppError("VALIDATION", "mode must be validate or commit", 400)
    if file is None and students is None and pickup is None:
        raise AppError(
            "VALIDATION",
            "multipart field `file` (locked template) or `students`/`pickup` required (.csv or .xlsx)",
            400,
            {"contract": CSV_CONTRACT},
        )
    unified = None
    student_result = None
    pickup_result = None
    filenames: list[str] = []
    if file is not None:
        data, filename, ctype = await read_upload(file)
        filenames.append(filename or "file")
        unified = import_locked_rows(
            data,
            school_id=user["schoolId"],
            user=user,
            filename=filename,
            content_type=ctype,
            file_label="file",
            mode=mode_n,
        )
    if students is not None:
        data, filename, ctype = await read_upload(students)
        filenames.append(filename or "students")
        student_result = import_students(
            data,
            school_id=user["schoolId"],
            user_id=user["id"],
            user=user,
            filename=filename,
            content_type=ctype,
            file_label="students",
            mode=mode_n,
        )
    if pickup is not None:
        data, filename, ctype = await read_upload(pickup)
        filenames.append(filename or "pickup")
        pickup_result = import_pickup(
            data,
            school_id=user["schoolId"],
            user_id=user["id"],
            user=user,
            filename=filename,
            content_type=ctype,
            file_label="pickup",
            mode=mode_n,
        )
    return merge_import_results(
        student_result,
        pickup_result,
        unified=unified,
        mode=mode_n,
        user=user,
        filenames=filenames,
        school_id=user["schoolId"],
    )


@router.get("/me/blast-config")
def get_blast_config(user: dict = Depends(require_roles(*_BLAST_ROLES))):
    school = _require_user_school(user)
    return _config_public(school)


@router.put("/me/blast-config")
def put_blast_config(
    body: BlastConfigPatch,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    return patch_blast_config(body, user)


@router.patch("/me/blast-config")
def patch_blast_config(
    body: BlastConfigPatch,
    user: dict = Depends(require_roles(*_BLAST_ROLES)),
):
    school = _require_user_school(user)
    visitor = _validate_channels(
        body.blastChannelsVisitor, _ALLOWED_VISITOR, "blastChannelsVisitor"
    )
    staff = _validate_channels(
        body.blastChannelsStaff, _ALLOWED_STAFF, "blastChannelsStaff"
    )
    if body.emergencyBlastEnabled is not None:
        school["emergencyBlastEnabled"] = bool(body.emergencyBlastEnabled)
    if body.blastStaffLaneEnabled is not None:
        school["blastStaffLaneEnabled"] = bool(body.blastStaffLaneEnabled)
    if visitor is not None:
        school["blastChannelsVisitor"] = visitor
    elif "blastChannelsVisitor" not in school:
        school["blastChannelsVisitor"] = list(BLAST_DEFAULT_VISITOR_CHANNELS)
    if staff is not None:
        school["blastChannelsStaff"] = staff
    elif "blastChannelsStaff" not in school:
        school["blastChannelsStaff"] = list(BLAST_DEFAULT_STAFF_CHANNELS)
    school["blastConfigUpdatedByUserId"] = user["id"]
    school["blastConfigUpdatedAt"] = now_iso()
    store.put_school(school)
    return _config_public(school)
