from __future__ import annotations

from collections import defaultdict
from typing import Optional

from fastapi import APIRouter, Depends, Query

from app.auth import require_roles
from app.config import WATERMARK
from app.models import Role
from app.util import parse_iso
from app import store

router = APIRouter(prefix="/reports", tags=["reports"])


def _agg_by_gate(school_id: str, date_from: Optional[str], date_to: Optional[str]):
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
        }
        for gid, name in gates.items()
    }

    for v in store.list_visits(school_id):
        created = parse_iso(v.get("createdAt"))
        if date_from and created and created.date().isoformat() < date_from[:10]:
            continue
        if date_to and created and created.date().isoformat() > date_to[:10]:
            continue
        gid = v.get("gateInId") or v.get("gateId")
        if gid not in stats:
            continue
        s = stats[gid]
        if v.get("timeIn"):
            s["checkIns"] += 1
            s["uniqueMobiles"].add(v.get("mobile"))
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

    out = []
    for s in stats.values():
        s["uniqueMobiles"] = len(s["uniqueMobiles"])
        s["medianApprovalSec"] = None  # stub
        s["peakInside"] = s["stillInside"]
        out.append(s)
    return out


@router.get("/today-by-gate")
def today_by_gate(user: dict = Depends(require_roles(Role.admin, Role.security_head, Role.gate))):
    from datetime import datetime
    from zoneinfo import ZoneInfo
    from app.config import SCHOOL_TZ

    today = datetime.now(ZoneInfo(SCHOOL_TZ)).date().isoformat()
    data = _agg_by_gate(user["schoolId"], today, today)
    return {"data": data, "meta": {"watermark": WATERMARK}}


@router.get("/range-by-gate")
def range_by_gate(
    from_: str = Query(alias="from"),
    to: str = Query(...),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    data = _agg_by_gate(user["schoolId"], from_, to)
    return {"data": data, "meta": {"watermark": WATERMARK, "from": from_, "to": to}}


@router.get("/visitor-type-mix")
def visitor_type_mix(
    from_: str = Query(alias="from"),
    to: str = Query(...),
    user: dict = Depends(require_roles(Role.admin, Role.security_head)),
):
    counts: dict[str, int] = defaultdict(int)
    for v in store.list_visits(user["schoolId"]):
        created = parse_iso(v.get("createdAt"))
        if created:
            d = created.date().isoformat()
            if d < from_[:10] or d > to[:10]:
                continue
        counts[v.get("visitorType") or "Unknown"] += 1
    data = [{"visitorType": k, "count": c} for k, c in sorted(counts.items())]
    return {"data": data, "meta": {"watermark": WATERMARK, "from": from_, "to": to}}
