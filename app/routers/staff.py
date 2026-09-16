from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import CurrentUser, require_roles
from app.config import SCHOOL_ID, WATERMARK
from app.errors import AppError
from app.models import Role, StaffCreate, StaffPatch
from app import store

router = APIRouter(prefix="/staff", tags=["staff"])


@router.get("")
def list_staff(
    user: CurrentUser,
    active: Optional[bool] = Query(default=None),
):
    rows = store.list_staff(user["schoolId"], active=active)
    return {"data": rows, "meta": {"watermark": WATERMARK}}


@router.post("")
def create_staff(
    body: StaffCreate,
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    sid = f"H{store.next_seq('staff_seq'):02d}"
    row = {
        "id": sid,
        "schoolId": user["schoolId"],
        "name": body.name,
        "roleTitle": body.roleTitle,
        "mobile": body.mobile,
        "userId": body.userId,
        "active": body.active,
    }
    store.put_staff(row)
    return {**row, "meta": {"watermark": WATERMARK}}


@router.patch("/{staff_id}")
def patch_staff(
    staff_id: str,
    body: StaffPatch,
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    row = store.get_staff(staff_id)
    if not row or row["schoolId"] != user["schoolId"]:
        raise AppError("NOT_FOUND", f"Staff {staff_id} not found", 404)
    data = body.model_dump(exclude_unset=True)
    row.update(data)
    store.put_staff(row)
    return {**row, "meta": {"watermark": WATERMARK}}
