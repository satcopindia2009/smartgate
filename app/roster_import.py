"""Student + authorized-pickup CSV/Excel import (JWT schoolId is SoT)."""
from __future__ import annotations

import csv
import io
from datetime import date, datetime
from typing import Any, Optional

from app.config import PICKUP_CONSENT_VERSION, RESERVED_SCHOOL_ID, WATERMARK
from app.errors import AppError
from app.models import IdType, PickupRelation
from app.pickup_match import default_custody_flag, denormalize_blocked_by_custody
from app.util import last4, normalize_mobile, now_iso
from app import store

STUDENT_COLUMNS = (
    "schoolId",
    "studentId",
    "name",
    "class",
    "section",
    "active",
    "enrollmentEndedAt",
    "legalHold",
)
PICKUP_COLUMNS = (
    "schoolId",
    "studentId",
    "personName",
    "relation",
    "mobile",
    "idType",
    "idNumber",
    "idLast4",
    "active",
    "effectiveFrom",
    "effectiveTo",
    "pickupConsentVersion",
    "pickupConsentAt",
)
STUDENT_REQUIRED = ("studentId", "name", "class", "section")
PICKUP_REQUIRED = (
    "studentId",
    "personName",
    "relation",
    "mobile",
    "pickupConsentVersion",
    "pickupConsentAt",
)
RELATION_VALUES = {r.value for r in PickupRelation}
ID_TYPE_VALUES = {i.value for i in IdType}
CSV_CONTRACT = (
    "Students columns: schoolId?,studentId,name,class,section,active,"
    "enrollmentEndedAt,legalHold. "
    "Pickup columns: schoolId?,studentId,personName,relation,mobile,idType,"
    "idNumber,idLast4,active,effectiveFrom,effectiveTo,pickupConsentVersion,"
    "pickupConsentAt. "
    "relation enum: parent|guardian|sibling|relative|other. "
    "schoolId column is optional; JWT schoolId is the source of truth."
)


def _cell_str(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, datetime):
        if value.hour == 0 and value.minute == 0 and value.second == 0 and value.microsecond == 0:
            return value.date().isoformat()
        return value.isoformat(timespec="seconds")
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value).strip()


def _norm_header(name: Any) -> str:
    return _cell_str(name).lstrip("\ufeff").strip()


def _parse_bool(raw: str, default: bool, field: str) -> bool:
    if raw is None or str(raw).strip() == "":
        return default
    s = str(raw).strip().lower()
    if s in ("1", "true", "yes", "y", "active"):
        return True
    if s in ("0", "false", "no", "n", "inactive"):
        return False
    raise ValueError(f"{field} must be true/false")


def _filename_kind(filename: Optional[str], content_type: Optional[str]) -> str:
    name = (filename or "").lower()
    ctype = (content_type or "").lower()
    if name.endswith(".xlsx") or "spreadsheet" in ctype or name.endswith(".xlsm"):
        return "xlsx"
    if name.endswith(".csv") or "csv" in ctype or ctype in ("text/plain", "application/octet-stream", ""):
        return "csv"
    raise AppError(
        "VALIDATION",
        "Roster import accepts .csv or .xlsx only",
        400,
        {"filename": filename, "contentType": content_type},
    )


def _read_tabular(data: bytes, filename: Optional[str], content_type: Optional[str]) -> list[dict[str, str]]:
    if not data or not data.strip():
        raise AppError("VALIDATION", "Import file is empty", 400, {"filename": filename})
    kind = _filename_kind(filename, content_type)
    if kind == "xlsx":
        try:
            from openpyxl import load_workbook
        except ImportError as e:
            raise AppError("VALIDATION", "openpyxl is required for .xlsx import", 400) from e
        try:
            wb = load_workbook(io.BytesIO(data), read_only=True, data_only=True)
        except Exception as e:
            raise AppError("VALIDATION", f"Could not read Excel file: {e}", 400) from e
        ws = wb.active
        rows_iter = ws.iter_rows(values_only=True)
        try:
            header_row = next(rows_iter)
        except StopIteration as e:
            raise AppError("VALIDATION", "Excel sheet is empty", 400) from e
        headers = [_norm_header(h) for h in header_row]
        if not any(headers):
            raise AppError("VALIDATION", "Excel header row is empty", 400)
        out: list[dict[str, str]] = []
        for excel_row in rows_iter:
            mapped = {
                headers[i]: _cell_str(excel_row[i] if i < len(excel_row) else "")
                for i in range(len(headers))
                if headers[i]
            }
            mapped["_row"] = str(len(out) + 2)
            out.append(mapped)
        return out

    text = data.decode("utf-8-sig")
    reader = csv.DictReader(io.StringIO(text))
    if not reader.fieldnames:
        raise AppError("VALIDATION", "CSV header row is missing", 400)
    reader.fieldnames = [_norm_header(h) for h in reader.fieldnames]
    out = []
    for i, row in enumerate(reader, start=2):
        mapped = {_norm_header(k): _cell_str(v) for k, v in (row or {}).items() if k}
        mapped["_row"] = str(i)
        out.append(mapped)
    return out


def _require_columns(rows: list[dict[str, str]], required: tuple[str, ...], filename: Optional[str]) -> None:
    headers = set()
    if rows:
        headers = {k for k in rows[0].keys() if k != "_row"}
    missing = [c for c in required if c not in headers]
    if missing:
        raise AppError(
            "VALIDATION",
            f"Missing required column(s): {', '.join(missing)}",
            400,
            {"filename": filename, "missing": missing, "contract": CSV_CONTRACT},
        )


def _blank_row(row: dict[str, str], keys: tuple[str, ...]) -> bool:
    return not any((row.get(k) or "").strip() for k in keys)


def _check_school_id(row_school: str, jwt_school: str) -> Optional[str]:
    raw = (row_school or "").strip()
    if not raw:
        return None
    if raw != jwt_school:
        return f"schoolId '{raw}' does not match JWT schoolId '{jwt_school}' (JWT is source of truth)"
    return None


def _err(row_no: int, message: str, field: Optional[str] = None, file: Optional[str] = None) -> dict:
    item: dict[str, Any] = {"row": row_no, "message": message}
    if field:
        item["field"] = field
    if file:
        item["file"] = file
    return item


def import_students(
    data: bytes,
    *,
    school_id: str,
    user_id: str,
    filename: Optional[str] = None,
    content_type: Optional[str] = None,
    file_label: str = "students",
) -> dict:
    rows = _read_tabular(data, filename, content_type)
    _require_columns(rows, STUDENT_REQUIRED, filename)
    created = 0
    updated = 0
    errors: list[dict] = []
    ts = now_iso()
    for row in rows:
        row_no = int(row.get("_row") or 0)
        if _blank_row(row, STUDENT_COLUMNS):
            continue
        mismatch = _check_school_id(row.get("schoolId", ""), school_id)
        if mismatch:
            errors.append(_err(row_no, mismatch, "schoolId", file_label))
            continue
        roster_id = (row.get("studentId") or "").strip()
        name = (row.get("name") or "").strip()
        klass = (row.get("class") or "").strip()
        section = (row.get("section") or "").strip()
        if not roster_id:
            errors.append(_err(row_no, "studentId is required", "studentId", file_label))
            continue
        if not name:
            errors.append(_err(row_no, "name is required", "name", file_label))
            continue
        if not klass:
            errors.append(_err(row_no, "class is required", "class", file_label))
            continue
        if not section:
            errors.append(_err(row_no, "section is required", "section", file_label))
            continue
        try:
            active = _parse_bool(row.get("active", ""), True, "active")
            legal_hold = _parse_bool(row.get("legalHold", ""), False, "legalHold")
        except ValueError as e:
            errors.append(_err(row_no, str(e), file=file_label))
            continue
        ended = (row.get("enrollmentEndedAt") or "").strip() or None
        if not active and not ended:
            ended = ts
        existing = store.get_student_by_roster_id(school_id, roster_id)
        if existing:
            existing.update(
                {
                    "name": name,
                    "class": klass,
                    "section": section,
                    "active": active,
                    "enrollmentEndedAt": ended if ended is not None else existing.get("enrollmentEndedAt"),
                    "legalHold": legal_hold,
                    "updatedAt": ts,
                }
            )
            store.put_student(existing)
            updated += 1
        else:
            sid = f"STU-{store.next_seq('student_seq'):04d}"
            store.put_student(
                {
                    "id": sid,
                    "schoolId": school_id,
                    "studentId": roster_id,
                    "name": name,
                    "class": klass,
                    "section": section,
                    "active": active,
                    "enrollmentEndedAt": ended,
                    "legalHold": legal_hold,
                    "createdAt": ts,
                    "updatedAt": ts,
                    "importedByUserId": user_id,
                }
            )
            store.put_custody_flag(default_custody_flag(sid, school_id))
            created += 1
    return {"created": created, "updated": updated, "errors": errors}


def import_pickup(
    data: bytes,
    *,
    school_id: str,
    user_id: str,
    filename: Optional[str] = None,
    content_type: Optional[str] = None,
    file_label: str = "pickup",
) -> dict:
    rows = _read_tabular(data, filename, content_type)
    _require_columns(rows, PICKUP_REQUIRED, filename)
    created = 0
    updated = 0
    errors: list[dict] = []
    ts = now_iso()
    for row in rows:
        row_no = int(row.get("_row") or 0)
        if _blank_row(row, PICKUP_COLUMNS):
            continue
        mismatch = _check_school_id(row.get("schoolId", ""), school_id)
        if mismatch:
            errors.append(_err(row_no, mismatch, "schoolId", file_label))
            continue
        roster_id = (row.get("studentId") or "").strip()
        person_name = (row.get("personName") or "").strip()
        relation = (row.get("relation") or "").strip().lower()
        mobile_raw = (row.get("mobile") or "").strip()
        consent_ver = (row.get("pickupConsentVersion") or "").strip()
        consent_at = (row.get("pickupConsentAt") or "").strip()
        if not roster_id:
            errors.append(_err(row_no, "studentId is required", "studentId", file_label))
            continue
        student = store.get_student_by_roster_id(school_id, roster_id)
        if not student:
            errors.append(
                _err(
                    row_no,
                    f"Unknown studentId '{roster_id}' for this school",
                    "studentId",
                    file_label,
                )
            )
            continue
        if not person_name:
            errors.append(_err(row_no, "personName is required", "personName", file_label))
            continue
        if relation not in RELATION_VALUES:
            errors.append(
                _err(
                    row_no,
                    f"relation must be one of {sorted(RELATION_VALUES)}",
                    "relation",
                    file_label,
                )
            )
            continue
        mobile = normalize_mobile(mobile_raw)
        if not mobile:
            errors.append(
                _err(row_no, "Invalid mobile — expected IN 10-digit or E.164", "mobile", file_label)
            )
            continue
        if not consent_ver:
            errors.append(
                _err(row_no, "pickupConsentVersion is required", "pickupConsentVersion", file_label)
            )
            continue
        if not consent_at:
            errors.append(
                _err(row_no, "pickupConsentAt is required", "pickupConsentAt", file_label)
            )
            continue
        try:
            active = _parse_bool(row.get("active", ""), True, "active")
        except ValueError as e:
            errors.append(_err(row_no, str(e), "active", file_label))
            continue
        id_type_raw = (row.get("idType") or "").strip()
        id_type = None
        if id_type_raw:
            match = next((v for v in ID_TYPE_VALUES if v.lower() == id_type_raw.lower()), None)
            if not match:
                errors.append(
                    _err(
                        row_no,
                        f"idType must be one of {sorted(ID_TYPE_VALUES)}",
                        "idType",
                        file_label,
                    )
                )
                continue
            id_type = match
        id_number = (row.get("idNumber") or "").strip() or None
        id_last4 = (row.get("idLast4") or "").strip() or last4(id_number)
        existing = store.get_authorized_person_by_mobile(school_id, student["id"], mobile)
        payload = {
            "schoolId": school_id,
            "studentId": student["id"],
            "name": person_name,
            "relation": relation,
            "mobile": mobile,
            "idType": id_type,
            "idNumber": id_number,
            "idLast4": id_last4,
            "active": active,
            "effectiveFrom": (row.get("effectiveFrom") or "").strip() or None,
            "effectiveTo": (row.get("effectiveTo") or "").strip() or None,
            "pickupConsentVersion": consent_ver,
            "pickupConsentAt": consent_at,
            "updatedByUserId": user_id,
            "updatedAt": ts,
        }
        if existing:
            existing.update(payload)
            store.put_authorized_person(existing)
            denormalize_blocked_by_custody(student["id"], school_id)
            updated += 1
        else:
            pid = f"APP-{store.next_seq('person_seq'):04d}"
            store.put_authorized_person(
                {
                    "id": pid,
                    "photoRef": None,
                    "blockedByCustody": False,
                    "createdByUserId": user_id,
                    "createdAt": ts,
                    **payload,
                }
            )
            denormalize_blocked_by_custody(student["id"], school_id)
            created += 1
    return {"created": created, "updated": updated, "errors": errors}


def merge_import_results(
    students: Optional[dict] = None,
    pickup: Optional[dict] = None,
) -> dict:
    s = students or {"created": 0, "updated": 0, "errors": []}
    p = pickup or {"created": 0, "updated": 0, "errors": []}
    errors = list(s.get("errors") or []) + list(p.get("errors") or [])
    return {
        "created": int(s.get("created") or 0) + int(p.get("created") or 0),
        "updated": int(s.get("updated") or 0) + int(p.get("updated") or 0),
        "errors": errors,
        "students": s,
        "pickup": p,
        "meta": {
            "watermark": WATERMARK,
            "reservedSchoolId": RESERVED_SCHOOL_ID,
            "csvContract": CSV_CONTRACT,
            "defaultConsentVersion": PICKUP_CONSENT_VERSION,
        },
    }


async def read_upload(upload) -> tuple[bytes, Optional[str], Optional[str]]:
    data = await upload.read()
    return data, getattr(upload, "filename", None), getattr(upload, "content_type", None)
