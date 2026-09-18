"""Living stub features: consent/L4, retention, DSR, persist, schools, notifications."""
from __future__ import annotations

import json
from datetime import date, timedelta

from app.config import (
    ID_IMAGE_RETENTION_DAYS,
    LIVE_PHOTO_RETENTION_DAYS,
    PICKUP_CONSENT_VERSION,
    REJECTED_VISIT_MEDIA_RETENTION_DAYS,
    SIGNATURE_RETENTION_DAYS,
    VISIT_CONSENT_VERSION_DEFAULT,
)
from app.persist import deserialize_state, load_state, save_state, serialize_state
from app.routers.media import MEDIA_DIR, retention_days_for_kind
from app.seed import seed
from app import store
from tests.conftest import auth, login, put_week_hours


def _upload(client, token: str, kind: str = "live_photo", **extra) -> dict:
    data = {"kind": kind, **extra}
    r = client.post(
        "/v1/media/upload",
        headers=auth(token),
        files={"file": ("p.jpg", b"fakejpeg-bytes", "image/jpeg")},
        data=data,
    )
    assert r.status_code == 200, r.text
    return r.json()


def _create_visit(client, token: str, **overrides) -> dict:
    key = overrides.pop("livePhotoKey", None) or _upload(client, token)["key"]
    body = {
        "visitorName": "Living Parent",
        "mobile": "9822088801",
        "visitorType": "Parent",
        "purpose": "Living slice",
        "hostId": "H03",
        "livePhotoKey": key,
        "idType": "Aadhaar",
        "idNumber": "999988887777",
        "gateId": "G-MAIN",
    }
    body.update(overrides)
    r = client.post("/v1/visits", headers=auth(token), json=body)
    assert r.status_code == 200, r.text
    return r.json()


def test_retention_policy_days():
    assert retention_days_for_kind("live_photo") == LIVE_PHOTO_RETENTION_DAYS == 90
    assert retention_days_for_kind("signature") == SIGNATURE_RETENTION_DAYS == 90
    assert retention_days_for_kind("id_image") == ID_IMAGE_RETENTION_DAYS == 30
    assert (
        retention_days_for_kind("live_photo", rejected=True)
        == REJECTED_VISIT_MEDIA_RETENTION_DAYS
        == 14
    )
    assert retention_days_for_kind("collector_live_photo") == 90


def test_media_upload_stamps_retention_and_writes_disk(client):
    gtoken = login(client, "gate", "gate123")
    live = _upload(client, gtoken, "live_photo")
    sig = _upload(client, gtoken, "signature")
    ident = _upload(client, gtoken, "id_image")
    assert live["url"] == f"/v1/media/{live['key']}"
    assert (MEDIA_DIR / live["key"]).is_file()
    got = client.get(f"/v1/media/{live['key']}", headers=auth(gtoken))
    assert got.status_code == 200
    assert got.content == b"fakejpeg-bytes"

    live_meta = store.get_media(live["key"])
    sig_meta = store.get_media(sig["key"])
    id_meta = store.get_media(ident["key"])
    assert live_meta["retainUntilDays"] == 90
    assert sig_meta["retainUntilDays"] == 90
    assert id_meta["retainUntilDays"] == 30
    assert live_meta["retainUntil"]


def test_visit_consent_l4_photo_gate_and_media_urls(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    suppressed = _create_visit(client, gtoken, mobile="9822088802", visitorName="No Consent")
    assert suppressed["consentAt"] is None
    assert suppressed["consentVersion"] is None
    assert suppressed["livePhotoUrl"] == f"/v1/media/{suppressed['livePhotoKey']}"
    assert suppressed["legalHold"] is False

    notes = client.get("/v1/notifications", headers=auth(htoken)).json()["data"]
    pending = next(n for n in notes if n.get("visitId") == suppressed["id"])
    assert pending["payload"].get("photoSuppressed") is True
    assert "livePhotoUrl" not in pending["payload"]

    consented = _create_visit(
        client,
        gtoken,
        mobile="9822088803",
        visitorName="With Consent",
        consentAt="2026-09-16T10:00:00+05:30",
        consentVersion=VISIT_CONSENT_VERSION_DEFAULT,
    )
    assert consented["consentAt"] == "2026-09-16T10:00:00+05:30"
    assert consented["consentVersion"] == VISIT_CONSENT_VERSION_DEFAULT
    notes = client.get("/v1/notifications", headers=auth(htoken)).json()["data"]
    yes = next(n for n in notes if n.get("visitId") == consented["id"])
    assert yes["payload"]["livePhotoUrl"] == f"/v1/media/{consented['livePhotoKey']}"
    assert yes["payload"]["consentAt"] == consented["consentAt"]


def test_rejected_visit_media_retention_14d(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")
    visit = _create_visit(client, gtoken, mobile="9822088804", visitorName="Reject Retain")
    rejected = client.post(
        f"/v1/visits/{visit['id']}/reject",
        headers=auth(htoken),
        json={"reason": "Not expected"},
    )
    assert rejected.status_code == 200, rejected.text
    media = store.get_media(visit["livePhotoKey"])
    assert media["retainUntilDays"] == 14


def test_internal_retention_status_purge_and_legal_hold(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    visit = _create_visit(client, gtoken, mobile="9822088805", visitorName="Hold Visit")
    hold = client.post(
        f"/v1/visits/{visit['id']}/legal-hold",
        headers=auth(stoken),
        json={"enabled": True, "reason": "Police request"},
    )
    assert hold.status_code == 200, hold.text
    assert hold.json()["legalHold"] is True
    assert hold.json()["legalHoldReason"] == "Police request"

    media = store.get_media(visit["livePhotoKey"])
    media["retainUntil"] = (date.today() - timedelta(days=1)).isoformat()
    store.put_media(media)

    status = client.get("/v1/internal/retention/status", headers=auth(atoken))
    assert status.status_code == 200
    assert status.json()["expired"] >= 1

    dry = client.post(
        "/v1/internal/retention/purge",
        headers=auth(atoken),
        json={"dryRun": True},
    )
    assert dry.status_code == 200
    assert visit["livePhotoKey"] in dry.json()["skippedLegalHoldKeys"]

    real = client.post(
        "/v1/internal/retention/purge",
        headers=auth(atoken),
        json={"dryRun": False},
    )
    assert real.status_code == 200
    assert store.get_media(visit["livePhotoKey"]) is not None
    assert client.get(f"/v1/visits/{visit['id']}", headers=auth(atoken)).json()[
        "livePhotoKey"
    ]

    host = login(client, "host", "host123")
    forbidden = client.get("/v1/internal/retention/status", headers=auth(host))
    assert forbidden.status_code == 403


def test_dsr_access_fulfil_and_erasure_exceptions(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    access = client.post(
        "/v1/internal/dsr/requests",
        headers=auth(atoken),
        json={"type": "access", "subjectMobile": "9822088806", "notes": "parent asked"},
    )
    assert access.status_code == 200, access.text
    assert access.json()["status"] == "open"
    listed = client.get("/v1/internal/dsr/requests", headers=auth(atoken))
    assert listed.status_code == 200
    assert any(i["id"] == access.json()["id"] for i in listed.json()["items"])
    done = client.post(
        f"/v1/internal/dsr/requests/{access.json()['id']}/fulfil",
        headers=auth(atoken),
    )
    assert done.status_code == 200
    assert done.json()["status"] == "fulfilled"

    held = _create_visit(client, gtoken, mobile="9822088807", visitorName="DSR Hold")
    client.post(
        f"/v1/visits/{held['id']}/legal-hold",
        headers=auth(stoken),
        json={"enabled": True, "reason": "litigation"},
    )
    erasure = client.post(
        "/v1/internal/dsr/requests",
        headers=auth(atoken),
        json={"type": "erasure", "visitId": held["id"]},
    )
    refused = client.post(
        f"/v1/internal/dsr/requests/{erasure.json()['id']}/fulfil",
        headers=auth(atoken),
    )
    assert refused.status_code == 409
    assert refused.json()["error"]["code"] == "LEGAL_HOLD"

    blocked = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "Vikram More",
            "mobile": "9876500001",
            "visitorType": "Guest",
            "purpose": "DSR blacklist",
            "hostId": "H03",
            "livePhotoKey": _upload(client, gtoken)["key"],
            "idType": "DL",
            "idNumber": "MH12X9999",
            "gateId": "G-MAIN",
        },
    )
    assert blocked.status_code == 403
    dsr_bl = client.post(
        "/v1/internal/dsr/requests",
        headers=auth(atoken),
        json={"type": "erasure", "subjectMobile": "9876500001"},
    )
    bl_refuse = client.post(
        f"/v1/internal/dsr/requests/{dsr_bl.json()['id']}/fulfil",
        headers=auth(atoken),
    )
    assert bl_refuse.status_code == 409
    assert bl_refuse.json()["error"]["code"] == "ACTIVE_BLACKLIST"


def test_export_history_masks_id_and_unmask_is_sh_plus_purpose(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")
    visit = _create_visit(
        client,
        gtoken,
        mobile="9822088808",
        visitorName="Export Mask",
        idNumber="123456789012",
    )

    masked = client.post(
        "/v1/exports",
        headers=auth(atoken),
        json={"scope": "history", "filters": {"days": 7}},
    )
    assert masked.status_code == 200
    assert "9012" in masked.text
    assert "123456789012" not in masked.text
    assert masked.headers.get("X-Export-Unmask") == "0"
    assert f"/v1/media/{visit['livePhotoKey']}" in masked.text

    admin_unmask = client.post(
        "/v1/exports",
        headers=auth(atoken),
        json={"scope": "history", "unmask": True, "purpose": "audit", "filters": {"days": 7}},
    )
    assert admin_unmask.status_code == 403

    sh_no_purpose = client.post(
        "/v1/exports",
        headers=auth(stoken),
        json={"scope": "history", "unmask": True, "filters": {"days": 7}},
    )
    assert sh_no_purpose.status_code == 400

    long_hist = client.post(
        "/v1/exports",
        headers=auth(atoken),
        json={"scope": "history", "filters": {"days": 45}},
    )
    assert long_hist.status_code == 400

    unmasked = client.post(
        "/v1/exports",
        headers=auth(stoken),
        json={
            "scope": "history",
            "unmask": True,
            "purpose": "DSR access pack",
            "filters": {"days": 7},
        },
    )
    assert unmasked.status_code == 200
    assert "123456789012" in unmasked.text
    assert unmasked.headers.get("X-Export-Unmask") == "1"


def test_a4_admin_or_sh_after_hours_host_blocked(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")
    put_week_hours(client, atoken, closed=True)
    visit = _create_visit(client, gtoken, mobile="9822088809", visitorName="A4 Living")
    assert visit["afterHours"] is True
    host = client.post(f"/v1/visits/{visit['id']}/approve", headers=auth(htoken))
    assert host.status_code == 403
    assert host.json()["error"]["code"] == "AFTER_HOURS_SH_REQUIRED"
    details = host.json()["error"].get("details") or {}
    assert details.get("allowedRoles") == ["admin", "security_head"]
    admin = client.post(
        f"/v1/visits/{visit['id']}/approve",
        headers=auth(atoken),
        json={"reason": "Admin night clearance"},
    )
    assert admin.status_code == 200
    assert admin.json()["status"] == "approved"

    visit2 = _create_visit(client, gtoken, mobile="9822088810", visitorName="A4 SH")
    sh = client.post(
        f"/v1/visits/{visit2['id']}/approve",
        headers=auth(stoken),
        json={"reason": "SH night clearance"},
    )
    assert sh.status_code == 200
    assert sh.json()["status"] == "approved"


def test_outbox_process_and_get_notifications(client):
    atoken = login(client, "admin", "admin123")
    htoken = login(client, "host", "host123")
    gtoken = login(client, "gate", "gate123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")
    visit = _create_visit(
        client,
        gtoken,
        mobile="9822088811",
        visitorName="Notify Host",
        consentAt="2026-09-16T10:00:00+05:30",
        consentVersion=VISIT_CONSENT_VERSION_DEFAULT,
    )
    host_notes = client.get("/v1/notifications", headers=auth(htoken))
    assert host_notes.status_code == 200
    assert any(n.get("visitId") == visit["id"] for n in host_notes.json()["data"])

    created = client.post(
        "/v1/internal/dsr/requests",
        headers=auth(atoken),
        json={"type": "access", "subjectMobile": "9822088811"},
    )
    assert created.status_code == 200
    pending = client.get("/v1/internal/notify-outbox", headers=auth(atoken))
    assert any(o.get("event") == "dsr.created" for o in pending.json()["data"])
    processed = client.post("/v1/internal/notify-outbox/process", headers=auth(atoken))
    assert processed.status_code == 200
    assert processed.json()["processed"] >= 1
    after = client.get("/v1/internal/notify-outbox", headers=auth(atoken))
    assert not any(o.get("event") == "dsr.created" for o in after.json()["data"])
    admin_notes = client.get("/v1/notifications", headers=auth(atoken))
    assert any(n.get("visitId") == visit["id"] for n in admin_notes.json()["data"])


def test_schools_list_and_create_protected_demo(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    listed = client.get("/v1/schools", headers=auth(atoken))
    assert listed.status_code == 200
    ids = {s["id"] for s in listed.json()["items"]}
    assert "SCH-DEMO-01" in ids
    assert "SCH-PRANAY-01" in ids
    assert client.get("/v1/schools", headers=auth(gtoken)).status_code == 403

    blocked = client.post(
        "/v1/schools",
        headers=auth(atoken),
        json={"name": "Overwrite Demo", "schoolCode": "DEMO"},
    )
    assert blocked.status_code == 403

    created = client.post(
        "/v1/schools",
        headers=auth(atoken),
        json={
            "name": "Living Test School",
            "schoolCode": "LIVING",
            "adminUsername": "living.admin",
            "adminPassword": "Living123!",
        },
    )
    assert created.status_code == 201, created.text
    assert created.json()["school"]["id"] == "SCH-LIVING-01"
    assert created.json()["admin"]["username"] == "living.admin"
    assert created.json()["admin"]["tempPassword"] == "Living123!"
    again = client.get("/v1/schools", headers=auth(atoken))
    assert any(s["id"] == "SCH-LIVING-01" for s in again.json()["items"])


def test_pickup_consent_on_create_allows_collector_photo(client):
    gtoken = login(client, "gate", "gate123")
    start = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-AARAV",
            "gateId": "G-MAIN",
            "pickupReason": "early",
            "collectorMobile": "9822011001",
            "collectorName": "Neha Mehta",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-09-16T10:00:00+05:30",
        },
    )
    assert start.status_code == 200, start.text
    assert start.json()["pickupConsentVersion"] == PICKUP_CONSENT_VERSION
    assert start.json()["pickupConsentAt"] == "2026-09-16T10:00:00+05:30"
    photo = _upload(
        client, gtoken, "collector_live_photo", pickupId=start.json()["id"]
    )
    assert photo["key"]


def test_persist_json_state_roundtrip(tmp_path):
    seed()
    store.add_notification(
        {
            "schoolId": "SCH-DEMO-01",
            "hostId": "H03",
            "event": "persist.roundtrip",
            "channel": "in_app",
            "title": "persist",
            "body": "ok",
        }
    )
    path = tmp_path / "state.json"
    save_state(path)
    raw = json.loads(path.read_text())
    assert raw["version"] == 1
    assert "school" in raw["state"]
    encoded = serialize_state(store.export_state())
    assert "||" in next(iter(encoded["campus_hours"]))
    store.reset()
    assert store.school() is None
    assert load_state(path) is True
    assert store.school()["id"] == "SCH-DEMO-01"
    assert store.get_user_by_username("admin")["role"] == "admin"
    notes = store.list_notifications("SCH-DEMO-01")
    assert any(n.get("event") == "persist.roundtrip" for n in notes)
    again = deserialize_state(raw["state"])
    assert ("SCH-DEMO-01", "mon") in again["campus_hours"]
