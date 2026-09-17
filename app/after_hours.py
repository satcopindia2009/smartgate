"""After-hours / holiday evaluation (Hub A1–A6, AC-C4). Sticky at visit CREATE only."""
from __future__ import annotations

from datetime import date, datetime, time
from typing import Optional
from zoneinfo import ZoneInfo

from app.config import CAMPUS_TZ
from app import store

CAMPUS_ZONE = ZoneInfo(CAMPUS_TZ)
WEEKDAYS = ("mon", "tue", "wed", "thu", "fri", "sat", "sun")


def parse_hhmm(value: Optional[str]) -> Optional[time]:
    if not value:
        return None
    parts = str(value).strip().split(":")
    if len(parts) < 2:
        return None
    hour = int(parts[0])
    minute = int(parts[1])
    second = int(parts[2]) if len(parts) > 2 else 0
    return time(hour, minute, second)


def format_hhmm(value: Optional[str]) -> Optional[str]:
    t = parse_hhmm(value)
    if t is None:
        return None
    if t.second:
        return t.strftime("%H:%M:%S")
    return t.strftime("%H:%M")


def weekday_key(local: datetime) -> str:
    return WEEKDAYS[local.weekday()]


def is_outside_window(row: Optional[dict], local: datetime) -> bool:
    """True if local clock is outside [open, close). Exact close = outside."""
    if not row or row.get("closed"):
        return True
    open_t = parse_hhmm(row.get("openTime"))
    close_t = parse_hhmm(row.get("closeTime"))
    if open_t is None or close_t is None:
        return True
    clock = time(local.hour, local.minute, local.second)
    overnight = bool(row.get("overnight")) and close_t < open_t
    if overnight:
        # Single overnight window: in-hours if clock >= open OR clock < close
        return not (clock >= open_t or clock < close_t)
    if close_t <= open_t:
        # Empty / inverted window without overnight confirm → closed
        return True
    return not (open_t <= clock < close_t)


def evaluate_after_hours(
    school_id: str,
    when: Optional[datetime] = None,
) -> dict:
    """
    Evaluate sticky after-hours at registration time (A3).
    Holiday wins even if the clock is inside the weekday window.
    policyTrigger: outside_hours | holiday | both when afterHours, else None.
    """
    instant = when or datetime.now(CAMPUS_ZONE)
    if instant.tzinfo is None:
        instant = instant.replace(tzinfo=CAMPUS_ZONE)
    local = instant.astimezone(CAMPUS_ZONE)
    day = local.date()
    holiday = store.holiday_on_date(school_id, day.isoformat()) is not None
    row = store.get_hours_row(school_id, weekday_key(local))
    outside = is_outside_window(row, local)
    after = holiday or outside
    trigger = None
    if after:
        if holiday and outside:
            trigger = "both"
        elif holiday:
            trigger = "holiday"
        else:
            trigger = "outside_hours"
    return {
        "afterHours": after,
        "policyTrigger": trigger,
        "afterHoursEvaluatedAt": local.isoformat(timespec="seconds"),
    }


def stamp_after_hours(visit: dict, when: Optional[datetime] = None) -> dict:
    visit.update(evaluate_after_hours(visit["schoolId"], when))
    visit.setdefault("afterHoursApproveReason", None)
    return visit


def validate_hours_week(rows: list[dict]) -> list[str]:
    """Return validation error messages (empty if OK). Overnight confirm required when close < open."""
    errors: list[str] = []
    seen: list[str] = []
    for row in rows:
        wd = row.get("weekday")
        if wd in seen:
            errors.append(f"duplicate weekday {wd}")
        seen.append(wd)
        tz = row.get("timezone") or CAMPUS_TZ
        if tz not in (CAMPUS_TZ, "Asia/Calcutta"):
            errors.append(f"{wd}: timezone must be {CAMPUS_TZ}")
        closed = bool(row.get("closed"))
        open_t = parse_hhmm(row.get("openTime"))
        close_t = parse_hhmm(row.get("closeTime"))
        if closed:
            continue
        if open_t is None or close_t is None:
            errors.append(f"{wd}: openTime and closeTime required when not closed")
            continue
        if close_t < open_t and not row.get("overnight"):
            errors.append(
                f"{wd}: closeTime < openTime requires overnight=true (F6)"
            )
    missing = [wd for wd in WEEKDAYS if wd not in seen]
    if missing:
        errors.append(f"full week required; missing {', '.join(missing)}")
    extra = [wd for wd in seen if wd not in WEEKDAYS]
    if extra:
        errors.append(f"unknown weekday {', '.join(extra)}")
    return errors


def parse_date(value: str) -> date:
    return date.fromisoformat(value)
