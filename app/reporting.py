"""In-memory report aggregates for Admin Hub (SCH-DEMO-01 seed)."""
from __future__ import annotations

from collections import defaultdict
from datetime import date
from statistics import median
from typing import Optional

from app.config import DEMO_TODAY, SCHOOL_TZ
from app.errors import AppError
from app.models import VisitorType
from app.util import parse_iso
from app import store

from zoneinfo import ZoneInfo

TZ = ZoneInfo(SCHOOL_TZ)

GATE_REPORT_FIELDS = (
    "gate",
    "gateId",
    "checkIns",
    "checkOuts",
    "stillInside",
    "rejects",
    "blacklistHits",
    "forceCheckouts",
    "uniqueMobiles",
    "medianApprovalSec",
    "peakInside",
)


def parse_report_date(value: str, name: str) -> str:
    raw = (value or "").strip()
    if not raw:
        raise AppError("VALIDATION", f"{name} is required", 400)
    try:
        return date.fromisoformat(raw[:10]).isoformat()
    except ValueError as e:
        raise AppError("VALIDATION", f"Invalid {name} date", 400) from e


def validate_date_range(from_s: str, to_s: str) -> tuple[str, str]:
    start = parse_report_date(from_s, "from")
    end = parse_report_date(to_s, "to")
    if start > end:
        raise AppError("VALIDATION", "from must be on or before to", 400)
    d0 = date.fromisoformat(start)
    d1 = date.fromisoformat(end)
    if (d1 - d0).days > 90:
        raise AppError("VALIDATION", "Date range max 90 days", 400)
    return start, end


def visit_local_date(visit: dict) -> Optional[str]:
    created = parse_iso(visit.get("createdAt"))
    if not created:
        return None
    if created.tzinfo is None:
        created = created.replace(tzinfo=TZ)
    return created.astimezone(TZ).date().isoformat()


def visits_in_range(school_id: str, date_from: Optional[str], date_to: Optional[str]) -> list[dict]:
    out = []
    start = date_from[:10] if date_from else None
    end = date_to[:10] if date_to else None
    for v in store.list_visits(school_id):
        day = visit_local_date(v)
        if start and day and day < start:
            continue
        if end and day and day > end:
            continue
        if (start or end) and not day:
            continue
        out.append(v)
    return out


def _approval_seconds(visit: dict) -> Optional[float]:
    created = parse_iso(visit.get("createdAt"))
    decided = parse_iso(visit.get("decidedAt"))
    if not created or not decided:
        return None
    if created.tzinfo is None:
        created = created.replace(tzinfo=TZ)
    if decided.tzinfo is None:
        decided = decided.replace(tzinfo=TZ)
    delta = (decided - created).total_seconds()
    return delta if delta >= 0 else None


def _peak_inside(visits: list[dict]) -> int:
    events: list[tuple] = []
    for v in visits:
        tin = parse_iso(v.get("timeIn"))
        tout = parse_iso(v.get("timeOut"))
        if tin:
            events.append((tin, 1))
        if tout:
            events.append((tout, -1))
    events.sort(key=lambda x: (x[0], x[1]))
    peak = current = 0
    for _when, delta in events:
        current += delta
        if current > peak:
            peak = current
    return peak


def agg_by_gate(school_id: str, date_from: Optional[str], date_to: Optional[str]) -> list[dict]:
    gates = {g["id"]: g["name"] for g in store.list_gates(school_id)}
    stats = {
        gid: {
            "gate": name,
            "gateId": gid,
            "checkIns": 0,
            "checkOuts": 0,
            "stillInside": 0,
            "rejects": 0,
            "blacklistHits": 0,
            "forceCheckouts": 0,
            "uniqueMobiles": set(),
            "_approvals": [],
            "_inside_visits": [],
        }
        for gid, name in gates.items()
    }

    for v in visits_in_range(school_id, date_from, date_to):
        gid = v.get("gateInId") or v.get("gateId")
        if gid not in stats:
            continue
        s = stats[gid]
        if v.get("timeIn"):
            s["checkIns"] += 1
            if v.get("mobile"):
                s["uniqueMobiles"].add(v["mobile"])
            s["_inside_visits"].append(v)
        if v.get("timeOut") and v.get("checkoutType") == "normal":
            s["checkOuts"] += 1
        if v["status"] == "inside":
            s["stillInside"] += 1
        if v["status"] == "rejected":
            s["rejects"] += 1
        if v.get("blacklistHit"):
            s["blacklistHits"] += 1
        if v.get("checkoutType") == "force":
            s["forceCheckouts"] += 1
        secs = _approval_seconds(v)
        if secs is not None:
            s["_approvals"].append(secs)

    out = []
    for s in stats.values():
        approvals = s.pop("_approvals")
        inside_visits = s.pop("_inside_visits")
        s["uniqueMobiles"] = len(s["uniqueMobiles"])
        s["medianApprovalSec"] = int(median(approvals)) if approvals else None
        s["peakInside"] = _peak_inside(inside_visits) or s["stillInside"]
        out.append(s)
    return out


def visitor_type_mix(school_id: str, date_from: str, date_to: str) -> list[dict]:
    counts: dict[str, int] = defaultdict(int)
    for v in visits_in_range(school_id, date_from, date_to):
        counts[v.get("visitorType") or "Unknown"] += 1
    # Stable Admin chart: known types first (zeros included when seeded range is empty)
    known = [vt.value for vt in VisitorType]
    data = [{"visitorType": k, "count": counts.get(k, 0)} for k in known]
    extras = sorted(k for k in counts if k not in known)
    data.extend({"visitorType": k, "count": counts[k]} for k in extras)
    return data


def demo_today() -> str:
    return DEMO_TODAY
