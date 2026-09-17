"""App config for demo stub."""
from __future__ import annotations

import os

JWT_SECRET = "satcop-smart-visitor-demo-secret-not-for-prod"
JWT_ALG = "HS256"
JWT_TTL_SECONDS = 60 * 60 * 12  # 12h
SCHOOL_ID = "SCH-DEMO-01"
RESERVED_SCHOOL_ID = SCHOOL_ID
SCHOOL_TZ = "Asia/Calcutta"
# Documented first-bootstrap token (header X-Bootstrap-Token) — stub only
BOOTSTRAP_TOKEN = os.environ.get("SATCOP_BOOTSTRAP_TOKEN", "satcop-school-bootstrap")
DEFAULT_NEW_SCHOOL_TZ = "Asia/Kolkata"
DEFAULT_GATES = (
    ("G-MAIN", "Main Gate"),
    ("G-PED", "Pedestrian Gate"),
    ("G-STAFF", "Staff Gate"),
    ("G-BUS", "Bus Bay"),
)
# A1 lock — CampusHours / after-hours evaluation (alias of Asia/Calcutta)
CAMPUS_TZ = "Asia/Kolkata"
WATERMARK = "DEMO"
MEDIA_BASE = "/v1/media"
HOST = "0.0.0.0"
PORT = 8080
PICKUP_CONSENT_VERSION = "pickup_notice_en_hi_v1"
COLLECTOR_PHOTO_RETENTION_DAYS = 90
# B3 / WA HOLD — no live WhatsApp or SMS providers in this stub
WA_HOLD = True
BLAST_INSTRUCTION_MAX = 160
BLAST_DEFAULT_VISITOR_CHANNELS = ["sms"]
BLAST_DEFAULT_STAFF_CHANNELS = ["in_app", "push"]
