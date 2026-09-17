"""App config for demo stub."""
from __future__ import annotations

JWT_SECRET = "satcop-smart-visitor-demo-secret-not-for-prod"
JWT_ALG = "HS256"
JWT_TTL_SECONDS = 60 * 60 * 12  # 12h
SCHOOL_ID = "SCH-DEMO-01"
SCHOOL_TZ = "Asia/Calcutta"
WATERMARK = "DEMO"
MEDIA_BASE = "/v1/media"
HOST = "0.0.0.0"
PORT = 8080
PICKUP_CONSENT_VERSION = "pickup_notice_en_hi_v1"
COLLECTOR_PHOTO_RETENTION_DAYS = 90
