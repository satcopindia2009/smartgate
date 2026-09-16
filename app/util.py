"""Shared helpers: TZ timestamps, mobile/ID normalize, IDs."""
from __future__ import annotations

import re
import secrets
import string
from datetime import datetime
from zoneinfo import ZoneInfo

from app.config import SCHOOL_TZ

TZ = ZoneInfo(SCHOOL_TZ)


def now_iso() -> str:
    return datetime.now(TZ).isoformat(timespec="seconds")


def normalize_mobile(mobile: str | None) -> str | None:
    if not mobile:
        return None
    digits = re.sub(r"\D", "", mobile)
    if digits.startswith("91") and len(digits) == 12:
        digits = digits[2:]
    if len(digits) == 10:
        return digits
    return digits or None


def normalize_id(id_number: str | None) -> str | None:
    if not id_number:
        return None
    return re.sub(r"[\s\-]", "", id_number).upper()


def gen_visit_id(seq: int, when: datetime | None = None) -> str:
    d = when or datetime.now(TZ)
    return f"V-{d.strftime('%Y%m%d')}-{seq:03d}"


def gen_pass_id() -> str:
    alphabet = string.ascii_uppercase + string.digits
    return "P-" + "".join(secrets.choice(alphabet) for _ in range(4))


def gen_qr_token() -> str:
    return "qr_" + secrets.token_urlsafe(16)


def gen_media_key(kind: str) -> str:
    return f"media/{kind}/{secrets.token_hex(8)}"


def parse_iso(s: str | None) -> datetime | None:
    if not s:
        return None
    return datetime.fromisoformat(s)
