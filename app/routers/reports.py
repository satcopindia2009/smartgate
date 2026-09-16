from __future__ import annotations

from fastapi import APIRouter, Depends, Query

from app.auth import require_roles
from app.config import WATERMARK
from app.models import GateReportResponse, ReportMeta, Role, TypeMixResponse
from app.reporting import agg_by_gate, demo_today, validate_date_range, visitor_type_mix

router = APIRouter(prefix="/reports", tags=["reports"])


@router.get("/today-by-gate", response_model=GateReportResponse, response_model_exclude_none=True)
def today_by_gate(user: dict = Depends(require_roles(Role.admin, Role.security_head, Role.gate))):
    today = demo_today()
    data = agg_by_gate(user["schoolId"], today, today)
    return GateReportResponse(
        data=data,
        meta=ReportMeta(
            watermark=WATERMARK,
            schoolId=user["schoolId"],
            demoToday=today,
            from_=today,
            to=today,
        ),
    )


@router.get("/range-by-gate", response_model=GateReportResponse, response_model_exclude_none=True)
def range_by_gate(
    from_: str = Query(alias="from"),
    to: str = Query(...),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    start, end = validate_date_range(from_, to)
    data = agg_by_gate(user["schoolId"], start, end)
    return GateReportResponse(
        data=data,
        meta=ReportMeta(
            watermark=WATERMARK,
            schoolId=user["schoolId"],
            from_=start,
            to=end,
        ),
    )


@router.get("/visitor-type-mix", response_model=TypeMixResponse, response_model_exclude_none=True)
def visitor_type_mix_report(
    from_: str = Query(alias="from"),
    to: str = Query(...),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    start, end = validate_date_range(from_, to)
    data = visitor_type_mix(user["schoolId"], start, end)
    return TypeMixResponse(
        data=data,
        meta=ReportMeta(
            watermark=WATERMARK,
            schoolId=user["schoolId"],
            from_=start,
            to=end,
        ),
    )
