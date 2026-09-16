"""Admin Hub reports / exports / notify-outbox — SCH-DEMO-01 seed."""
from __future__ import annotations

from app import store
from app.config import DEMO_TODAY, SCHOOL_ID, WATERMARK
from tests.conftest import auth, login


def _admin(client):
    return auth(login(client, "admin", "admin123"))


def _sh(client):
    return auth(login(client, "security", "sh123"))


def test_today_by_gate_seeded_aggregates(client):
    headers = _admin(client)
    r = client.get("/v1/reports/today-by-gate", headers=headers)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["meta"]["watermark"] == WATERMARK
    assert body["meta"]["schoolId"] == SCHOOL_ID
    assert body["meta"]["demoToday"] == DEMO_TODAY
    assert body["meta"]["from"] == DEMO_TODAY
    by_id = {row["gateId"]: row for row in body["data"]}
    assert set(by_id) == {"G-MAIN", "G-PED", "G-STAFF", "G-BUS"}

    main = by_id["G-MAIN"]
    assert main["gate"] == "Main Gate"
    assert main["checkIns"] >= 2  # Priya + Capt. Rao
    assert main["checkOuts"] >= 1  # Rao normal checkout
    assert main["stillInside"] >= 1  # Priya
    assert main["uniqueMobiles"] >= 2
    assert main["peakInside"] >= main["stillInside"]
    assert main["medianApprovalSec"] is not None

    staff = by_id["G-STAFF"]
    assert staff["checkIns"] >= 1  # Arjun
    assert staff["stillInside"] >= 1

    ped = by_id["G-PED"]
    assert ped["checkIns"] >= 1  # Neha
    assert ped["blacklistHits"] >= 1
    assert ped["stillInside"] >= 1

    bus = by_id["G-BUS"]
    assert bus["checkIns"] == 0
    assert bus["stillInside"] == 0


def test_range_by_gate_includes_rejects_and_force(client):
    headers = _admin(client)
    r = client.get(
        "/v1/reports/range-by-gate",
        headers=headers,
        params={"from": "2026-09-14", "to": "2026-09-16"},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["meta"]["watermark"] == WATERMARK
    assert body["meta"]["from"] == "2026-09-14"
    assert body["meta"]["to"] == "2026-09-16"
    by_id = {row["gateId"]: row for row in body["data"]}
    assert by_id["G-MAIN"]["rejects"] >= 2  # Rohit + Vikram (15 Sep)
    assert by_id["G-MAIN"]["blacklistHits"] >= 1  # Vikram
    assert by_id["G-PED"]["forceCheckouts"] >= 1  # Sneha 14 Sep
    assert by_id["G-STAFF"]["checkIns"] >= 1

    bad = client.get(
        "/v1/reports/range-by-gate",
        headers=headers,
        params={"from": "2026-09-16", "to": "2026-09-14"},
    )
    assert bad.status_code == 400
    assert bad.json()["error"]["code"] == "VALIDATION"

    host = auth(login(client, "host", "host123"))
    deny = client.get(
        "/v1/reports/range-by-gate",
        headers=host,
        params={"from": "2026-09-14", "to": "2026-09-16"},
    )
    assert deny.status_code == 403


def test_visitor_type_mix_seeded(client):
    headers = _sh(client)
    r = client.get(
        "/v1/reports/visitor-type-mix",
        headers=headers,
        params={"from": "2026-09-14", "to": "2026-09-16"},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["meta"]["watermark"] == WATERMARK
    mix = {row["visitorType"]: row["count"] for row in body["data"]}
    assert mix["Parent"] >= 2  # Priya + pending parent (today ts) / Ravi is Vendor
    assert mix["Vendor"] >= 3  # Arjun, Neha, after-hours Ravi
    assert mix["Official"] >= 1  # Rao
    assert mix["Guest"] >= 2  # Kiran + Rohit (+ Vikram)
    assert mix["Alumni"] >= 1  # Sneha
    assert sum(mix.values()) >= 8


def test_export_history_csv_and_audit(client):
    headers = _admin(client)
    r = client.post(
        "/v1/exports",
        headers=headers,
        json={"scope": "history", "filters": {"from": "2026-09-16", "to": "2026-09-16"}},
    )
    assert r.status_code == 200, r.text
    assert r.headers["content-type"].startswith("text/csv")
    assert r.headers["X-Export-Watermark"] == WATERMARK
    assert r.headers["X-Export-Scope"] == "history"
    assert r.headers["X-Export-Audit-Id"].startswith("EX-")
    assert int(r.headers["X-Export-Row-Count"]) >= 1
    lines = [ln for ln in r.text.splitlines() if ln.strip()]
    header = lines[0]
    assert header.startswith("visitId,visitorName,mobile")
    assert "Priya Sharma" in r.text
    assert "V-20260916-014" in r.text
    assert "P-4F21" in r.text
    # Range filter should exclude 14 Sep force-checkout
    assert "Sneha Iyer" not in r.text

    audits = store.list_exports()
    assert audits
    last = audits[-1]
    assert last["id"] == r.headers["X-Export-Audit-Id"]
    assert last["who"] == "U-ADMIN"
    assert last["scope"] == "history"
    assert last["schoolId"] == SCHOOL_ID
    assert last["rowCount"] == int(r.headers["X-Export-Row-Count"])
    assert last["delivery"] == "csv"
    assert last["filters"]["from"] == "2026-09-16"

    summary = client.post(
        "/v1/exports",
        headers=headers,
        json={"scope": "daily_gate_summary"},
    )
    assert summary.status_code == 200
    assert "checkIns" in summary.text
    assert "Main Gate" in summary.text
    assert "see /v1/reports/today-by-gate" not in summary.text

    host = auth(login(client, "host", "host123"))
    deny = client.post("/v1/exports", headers=host, json={"scope": "history"})
    assert deny.status_code == 403


def test_notify_outbox_status_filter(client):
    headers = _sh(client)
    pending = client.get("/v1/internal/notify-outbox", headers=headers)
    assert pending.status_code == 200, pending.text
    body = pending.json()
    assert body["meta"]["watermark"] == WATERMARK
    assert body["meta"]["status"] == "pending"
    assert body["meta"]["count"] == len(body["data"])
    assert body["data"]
    assert all(o["status"] == "pending" for o in body["data"])
    events = {o["event"] for o in body["data"]}
    assert "visit.pending" in events
    assert "pickup.override_requested" in events
    host_fyi = [
        o
        for o in body["data"]
        if o.get("payload", {}).get("hostFyi") is True
    ]
    assert host_fyi, "expected pending Host FYI (after-hours / holiday)"
    assert any(
        o.get("visitId") == "V-AH-VENDOR" and "security_head" in o["channelHints"]
        for o in body["data"]
    )
    assert any(o.get("pickupId") == "PK-20260916-OVR" for o in body["data"])

    sent = client.get("/v1/internal/notify-outbox?status=sent", headers=headers)
    assert sent.status_code == 200
    assert sent.json()["data"]
    assert all(o["status"] == "sent" for o in sent.json()["data"])
    sent_events = {o["event"] for o in sent.json()["data"]}
    assert "visit.approved" in sent_events
    assert "visit.checked_in" in sent_events

    failed = client.get("/v1/internal/notify-outbox?status=failed", headers=headers)
    assert failed.status_code == 200
    assert failed.json()["data"]
    assert all(o["status"] == "failed" for o in failed.json()["data"])

    all_rows = client.get("/v1/internal/notify-outbox?status=all", headers=headers)
    assert all_rows.status_code == 200
    statuses = {o["status"] for o in all_rows.json()["data"]}
    assert statuses == {"pending", "sent", "failed"}

    bad = client.get("/v1/internal/notify-outbox?status=blast", headers=headers)
    assert bad.status_code == 400
    assert bad.json()["error"]["code"] == "VALIDATION"

    host = auth(login(client, "host", "host123"))
    deny = client.get("/v1/internal/notify-outbox", headers=host)
    assert deny.status_code == 403
