"""In-memory multi-tenant store (single demo school)."""
from __future__ import annotations

from copy import deepcopy
from typing import Any, Optional

_state: dict[str, Any] = {
    "school": None,
    "gates": {},
    "staff": {},
    "users": {},  # id -> user
    "users_by_username": {},  # username -> user id
    "visits": {},
    "passes": {},  # passId -> pass record
    "passes_by_token": {},
    "blacklist": {},
    "media": {},
    "outbox": [],
    "exports": [],
    "counters": {"visit_seq": 40, "staff_seq": 10, "bl_seq": 10, "outbox_seq": 1},
}


def reset() -> None:
    global _state
    _state = {
        "school": None,
        "gates": {},
        "staff": {},
        "users": {},
        "users_by_username": {},
        "visits": {},
        "passes": {},
        "passes_by_token": {},
        "blacklist": {},
        "media": {},
        "outbox": [],
        "exports": [],
        "counters": {"visit_seq": 40, "staff_seq": 10, "bl_seq": 10, "outbox_seq": 1},
    }


def school() -> dict:
    return _state["school"]


def set_school(s: dict) -> None:
    _state["school"] = s


def next_seq(name: str) -> int:
    _state["counters"][name] = _state["counters"].get(name, 0) + 1
    return _state["counters"][name]


# --- users ---


def put_user(user: dict) -> None:
    _state["users"][user["id"]] = user
    _state["users_by_username"][user["username"].lower()] = user["id"]


def get_user(user_id: str) -> Optional[dict]:
    return _state["users"].get(user_id)


def get_user_by_username(username: str) -> Optional[dict]:
    uid = _state["users_by_username"].get(username.lower())
    return _state["users"].get(uid) if uid else None


# --- gates / staff ---


def put_gate(g: dict) -> None:
    _state["gates"][g["id"]] = g


def list_gates(school_id: str) -> list[dict]:
    return [g for g in _state["gates"].values() if g["schoolId"] == school_id]


def get_gate(gate_id: str) -> Optional[dict]:
    return _state["gates"].get(gate_id)


def gate_by_name(name: str) -> Optional[dict]:
    for g in _state["gates"].values():
        if g["name"] == name:
            return g
    return None


def put_staff(s: dict) -> None:
    _state["staff"][s["id"]] = s


def list_staff(school_id: str, active: Optional[bool] = None) -> list[dict]:
    out = [s for s in _state["staff"].values() if s["schoolId"] == school_id]
    if active is not None:
        out = [s for s in out if s["active"] == active]
    return out


def get_staff(staff_id: str) -> Optional[dict]:
    return _state["staff"].get(staff_id)


# --- visits ---


def put_visit(v: dict) -> None:
    _state["visits"][v["id"]] = v


def get_visit(visit_id: str) -> Optional[dict]:
    return _state["visits"].get(visit_id)


def list_visits(school_id: str) -> list[dict]:
    return [v for v in _state["visits"].values() if v["schoolId"] == school_id]


# --- passes ---


def put_pass(p: dict) -> None:
    _state["passes"][p["passId"]] = p
    if p.get("token"):
        _state["passes_by_token"][p["token"]] = p["passId"]


def get_pass(pass_id: str) -> Optional[dict]:
    return _state["passes"].get(pass_id)


def get_pass_by_token(token: str) -> Optional[dict]:
    pid = _state["passes_by_token"].get(token)
    return _state["passes"].get(pid) if pid else None


# --- blacklist ---


def put_blacklist(b: dict) -> None:
    _state["blacklist"][b["id"]] = b


def get_blacklist(bl_id: str) -> Optional[dict]:
    return _state["blacklist"].get(bl_id)


def list_blacklist(school_id: str, active: Optional[bool] = None) -> list[dict]:
    out = [b for b in _state["blacklist"].values() if b["schoolId"] == school_id]
    if active is not None:
        out = [b for b in out if b["active"] == active]
    return out


# --- media ---


def put_media(m: dict) -> None:
    _state["media"][m["key"]] = m


def get_media(key: str) -> Optional[dict]:
    return _state["media"].get(key)


# --- outbox / exports ---


def add_outbox(event: dict) -> dict:
    event = deepcopy(event)
    event["id"] = f"OB-{next_seq('outbox_seq'):04d}"
    _state["outbox"].append(event)
    return event


def list_outbox(status: Optional[str] = None) -> list[dict]:
    items = list(_state["outbox"])
    if status:
        items = [o for o in items if o["status"] == status]
    return items


def add_export(rec: dict) -> dict:
    _state["exports"].append(rec)
    return rec


def list_exports() -> list[dict]:
    return list(_state["exports"])
