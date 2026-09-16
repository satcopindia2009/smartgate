#!/usr/bin/env python3
"""Regenerate committed openapi.json from the FastAPI app."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.main import app

spec = app.openapi()
draft_path = ROOT / "notifications" / "openapi-blast-slice-draft.json"
if draft_path.exists():
    draft = json.loads(draft_path.read_text())
    spec.setdefault("components", {}).setdefault("schemas", {})
    for name, schema in (draft.get("components") or {}).get("schemas", {}).items():
        spec["components"]["schemas"].setdefault(name, schema)
    spec.setdefault("paths", {})
    for path, item in (draft.get("paths") or {}).items():
        if path not in spec["paths"]:
            spec["paths"][path] = item
            continue
        for method, op in item.items():
            spec["paths"][path].setdefault(method, op)

out = ROOT / "openapi.json"
out.write_text(json.dumps(spec, indent=2) + "\n")
print(f"wrote {out}")
