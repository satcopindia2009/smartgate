"""Blacklist hard-match rules (contract §5)."""
from __future__ import annotations

from datetime import date
from typing import Optional

from app.util import normalize_id, normalize_mobile, parse_iso
from app import store


def is_expired(entry: dict, today: Optional[date] = None) -> bool:
    exp = entry.get("expiresOn")
    if not exp:
        return False
    today = today or date.today()
    try:
        # date-only or ISO
        if "T" in exp:
            d = parse_iso(exp)
            return bool(d and d.date() < today)
        return date.fromisoformat(exp) < today
    except ValueError:
        return False


def match_blacklist(
    school_id: str,
    mobile: Optional[str] = None,
    id_type: Optional[str] = None,
    id_number: Optional[str] = None,
) -> Optional[dict]:
    nm = normalize_mobile(mobile)
    nid = normalize_id(id_number)
    for b in store.list_blacklist(school_id, active=True):
        if is_expired(b):
            continue
        bm = normalize_mobile(b.get("mobile"))
        if nm and bm and nm == bm:
            return b
        if id_type and nid and b.get("idType") == id_type:
            if normalize_id(b.get("idNumber")) == nid:
                return b
    return None
