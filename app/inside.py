"""Currently-inside visitor set — Hub B1 / GET /v1/visits/inside.

Audience for emergency blast is this exact query: status `inside`,
`timeIn` set, `timeOut` null. After-hours visitors stay in; escort staff
are not visits and are never in this set.
"""
from __future__ import annotations

from app import store


def is_inside_visit(visit: dict) -> bool:
    return (
        visit.get("status") == "inside"
        and bool(visit.get("timeIn"))
        and not visit.get("timeOut")
    )


def list_inside_visits(school_id: str) -> list[dict]:
    rows = [v for v in store.list_visits(school_id) if is_inside_visit(v)]
    rows.sort(key=lambda x: x.get("timeIn") or "", reverse=True)
    return rows
