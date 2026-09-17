"""Shared helpers: TZ timestamps, mobile/ID normalize, IDs."""
from __future__ import annotations

import re
import secrets
import string
from datetime import datetime
from zoneinfo import ZoneInfo

from app.config import SCHOOL_TZ

TZ = ZoneInfo(SCHOOL_TZ)

# Re-export for media retention stamps
__all__ = [
    "TZ",
    "now_iso",
    "normalize_mobile",
    "normalize_id",
    "gen_visit_id",
    "gen_pickup_id",
    "gen_blast_id",
    "gen_blast_template_id",
    "mask_mobile",
    "gen_pass_id",
    "gen_qr_token",
    "gen_media_key",
    "parse_iso",
    "last4",
]


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
    return None


def normalize_id(id_number: str | None) -> str | None:
    if not id_number:
        return None
    return re.sub(r"[\s\-]", "", id_number).upper()


def gen_visit_id(seq: int, when: datetime | None = None) -> str:
    d = when or datetime.now(TZ)
    return f"V-{d.strftime('%Y%m%d')}-{seq:03d}"


def gen_pickup_id(seq: int, when: datetime | None = None) -> str:
    d = when or datetime.now(TZ)
    return f"PK-{d.strftime('%Y%m%d')}-{seq:03d}"


def gen_blast_id(seq: int, when: datetime | None = None) -> str:
    d = when or datetime.now(TZ)
    return f"B-{d.strftime('%Y%m%d')}-{seq:02d}"


def gen_blast_template_id(seq: int) -> str:
    return f"T-{seq:04d}"


def mask_mobile(mobile: str | None) -> str:
    """Visitor SMS never carries a full number (AC-E3g).

    Seed/demo shape matches notifications/blast-seed-demo.json: +91-9xxx-xx4442.
    """
    if not mobile:
        return "+91-9xxx-xx****"
    digits = re.sub(r"\D", "", mobile)
    if digits.startswith("91") and len(digits) == 12:
        digits = digits[2:]
    if len(digits) == 10:
        return f"+91-{digits[0]}xxx-xx{digits[-4:]}"
    if len(digits) >= 4:
        return f"+91-9xxx-xx{digits[-4:]}"
    return "+91-9xxx-xx****"


def last4(value: str | None) -> str | None:
    nid = normalize_id(value)
    if not nid:
        return None
    return nid[-4:] if len(nid) >= 4 else nid


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
