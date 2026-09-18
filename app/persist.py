"""Durable JSON snapshot for the in-memory Day-1 stub.

Persists the full store under data/state.json so uvicorn restarts keep
visits, outbox, notifications, media metadata, schools, students, pickups,
users, etc. Media bytes remain on disk under data/media/.
"""
from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path
from typing import Any

from app import store

DATA_DIR = Path(__file__).resolve().parents[1] / "data"
STATE_PATH = DATA_DIR / "state.json"

# Dicts keyed by (schoolId, secondary) tuples — JSON needs string keys.
_TUPLE_KEY_BUCKETS = ("campus_hours", "zone_labels", "escort_rules")


def persist_enabled() -> bool:
    return os.environ.get("SATCOP_PERSIST", "1") != "0"


def _encode_tuple_key(key: tuple) -> str:
    return "||".join(str(p) for p in key)


def _decode_tuple_key(raw: str) -> tuple:
    parts = raw.split("||")
    if len(parts) == 2:
        return (parts[0], parts[1])
    return tuple(parts)


def serialize_state(state: dict[str, Any]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for key, value in state.items():
        if key in _TUPLE_KEY_BUCKETS and isinstance(value, dict):
            out[key] = {_encode_tuple_key(k): v for k, v in value.items()}
        else:
            out[key] = value
    return out


def deserialize_state(raw: dict[str, Any]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for key, value in raw.items():
        if key in _TUPLE_KEY_BUCKETS and isinstance(value, dict):
            rebuilt: dict = {}
            for sk, sv in value.items():
                if isinstance(sk, str) and "||" in sk:
                    rebuilt[_decode_tuple_key(sk)] = sv
                else:
                    # tolerate already-tuple or list forms
                    if isinstance(sk, (list, tuple)) and len(sk) == 2:
                        rebuilt[(sk[0], sk[1])] = sv
                    else:
                        rebuilt[sk] = sv
            out[key] = rebuilt
        else:
            out[key] = value
    return out


def save_state(path: Path | None = None) -> Path:
    """Atomic write of current store snapshot."""
    target = path or STATE_PATH
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": 1,
        "state": serialize_state(store.export_state()),
    }
    fd, tmp_name = tempfile.mkstemp(prefix="state.", suffix=".json", dir=str(target.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False, indent=0, default=str)
            fh.flush()
            os.fsync(fh.fileno())
        os.replace(tmp_name, target)
    except Exception:
        try:
            os.unlink(tmp_name)
        except OSError:
            pass
        raise
    return target


def load_state(path: Path | None = None) -> bool:
    """Load snapshot into store. Returns True if loaded non-empty state."""
    target = path or STATE_PATH
    if not target.is_file():
        return False
    try:
        with target.open("r", encoding="utf-8") as fh:
            payload = json.load(fh)
    except (OSError, json.JSONDecodeError):
        return False
    raw = payload.get("state") if isinstance(payload, dict) else None
    if not isinstance(raw, dict) or not raw:
        return False
    # Require at least a school or users to treat as real
    if not raw.get("school") and not raw.get("schools") and not raw.get("users"):
        return False
    state = deserialize_state(raw)
    store.import_state(state)
    return True
