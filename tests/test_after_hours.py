"""Priority P2 after-hours / holiday Access Rules — Hub A1–A6 + AC-C4."""
from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

from app.after_hours import CAMPUS_ZONE, evaluate_after_hours, is_outside_window
from app import store
from app.config import CAMPUS_TZ, SCHOOL_ID, WATERMARK
from tests.conftest import WEEKDAYS, auth, login, put_week_hours


def _create_visit(client, token, **kwargs):
    body = {
        "visitorName": "After Hours Test",
        "mobile": "9822080101",
        "visitorType": "Guest",
        "purpose": "Hours test",
        "hostId": "H03",
        "livePhotoKey": "media/live_photo/priya",
        "idType": "Aadhaar",
        "idNumber": "111122223333",
        "gateId": "G-MAIN",
        **kwargs,
    }
    return client.post("/v1/visits", headers=auth(token), json=body)


def test_a1_seed_hours_seven_weekdays_asia_kolkata(client):
    token = login(client, "admin", "admin123")
    r = client.get("/v1/access-rules/hours", headers=auth(token))
    assert r.status_code == 200
    rows = r.json()["data"]
    assert [row["weekday"] for row in rows] == list(WEEKDAYS)
    assert all(row["timezone"] == CAMPUS_TZ for row in rows)
    assert all(row["schoolId"] == SCHOOL_ID for row in rows)
    by_wd = {row["weekday"]: row for row in rows}
    assert by_wd["mon"]["openTime"] == "08:00"
    assert by_wd["mon"]["closeTime"] == "18:00"
    assert by_wd["mon"]["closed"] is False
    assert by_wd["sun"]["closed"] is True
    assert by_wd["sat"]["closeTime"] == "13:00"
    assert r.json()["meta"]["timezone"] == CAMPUS_TZ


def test_a1_put_hours_admin_and_gate_forbidden(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    closed_week = [
        {
            "weekday": wd,
            "timezone": "Asia/Kolkata",
            "closed": True,
            "openTime": None,
            "closeTime": None,
            "overnight": False,
        }
        for wd in WEEKDAYS
    ]
    deny = client.put(
        "/v1/access-rules/hours",
        headers=auth(gtoken),
        json=closed_week,
    )
    assert deny.status_code == 403
    host_deny = client.put(
        "/v1/access-rules/hours",
        headers=auth(htoken),
        json=closed_week,
    )
    assert host_deny.status_code == 403

    put_week_hours(client, atoken, open_time="07:30", close_time="16:00")
    rows = client.get("/v1/access-rules/hours", headers=auth(gtoken)).json()["data"]
    assert rows[0]["openTime"] == "07:30"
    assert rows[0]["closeTime"] == "16:00"
    assert rows[0]["updatedByUserId"] == "U-ADMIN"


def test_a1_overnight_requires_confirm(client):
    atoken = login(client, "admin", "admin123")
    days = []
    for wd in WEEKDAYS:
        days.append(
            {
                "weekday": wd,
                "timezone": "Asia/Kolkata",
                "openTime": "22:00",
                "closeTime": "06:00",
                "closed": False,
                "overnight": False,
            }
        )
    bad = client.put("/v1/access-rules/hours", headers=auth(atoken), json=days)
    assert bad.status_code == 400
    assert bad.json()["error"]["code"] == "VALIDATION"
    assert "overnight" in bad.json()["error"]["message"].lower()

    for row in days:
        row["overnight"] = True
    ok = client.put("/v1/access-rules/hours", headers=auth(atoken), json=days)
    assert ok.status_code == 200, ok.text
    assert ok.json()["data"][0]["overnight"] is True


def test_a2_holiday_crud_no_csv(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    listed = client.get("/v1/access-rules/holidays", headers=auth(atoken))
    dates = {h["date"]: h for h in listed.json()["data"]}
    assert "2026-10-20" in dates
    assert dates["2026-10-20"]["label"] == "Diwali"
    assert dates["2026-10-20"]["id"] == "HOL-DIWALI"

    ranged = client.get(
        "/v1/access-rules/holidays?from=2026-10-01&to=2026-10-10",
        headers=auth(gtoken),
    )
    assert {h["date"] for h in ranged.json()["data"]} == {"2026-10-02"}

    created = client.post(
        "/v1/access-rules/holidays",
        headers=auth(atoken),
        json={"date": "2026-11-14", "label": "Children's Day"},
    )
    assert created.status_code == 200, created.text
    hid = created.json()["id"]
    assert created.json()["date"] == "2026-11-14"
    assert created.json()["meta"]["watermark"] == WATERMARK

    dup = client.post(
        "/v1/access-rules/holidays",
        headers=auth(atoken),
        json={"date": "2026-11-14"},
    )
    assert dup.status_code == 400

    gate_write = client.post(
        "/v1/access-rules/holidays",
        headers=auth(gtoken),
        json={"date": "2026-12-25", "label": "Christmas"},
    )
    assert gate_write.status_code == 403

    deleted = client.delete(f"/v1/access-rules/holidays/{hid}", headers=auth(atoken))
    assert deleted.status_code == 200
    assert deleted.json()["deleted"] is True
    gone = client.delete(f"/v1/access-rules/holidays/{hid}", headers=auth(atoken))
    assert gone.status_code == 404


def test_a3_c4_in_hours_host_approve_ok(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    created = _create_visit(client, gtoken, visitorName="In Hours Parent", visitorType="Parent")
    assert created.status_code == 200, created.text
    body = created.json()
    assert body["afterHours"] is False
    assert body["policyTrigger"] is None
    assert body["afterHoursEvaluatedAt"]
    assert body["status"] == "pending"

    ap = client.post(f"/v1/visits/{body['id']}/approve", headers=auth(htoken))
    assert ap.status_code == 200, ap.text
    assert ap.json()["status"] == "approved"
    assert ap.json()["afterHours"] is False
    assert ap.json()["passId"].startswith("P-")


def test_a3_a5_outside_hours_sticky(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, closed=True)

    created = _create_visit(
        client,
        gtoken,
        visitorName="Late Vendor",
        visitorType="Vendor",
        mobile="9822080102",
        hostId="H04",
    )
    assert created.status_code == 200, created.text
    body = created.json()
    assert body["afterHours"] is True
    assert body["policyTrigger"] == "outside_hours"
    assert "+05:30" in body["afterHoursEvaluatedAt"] or "Asia" in CAMPUS_TZ
    assert datetime.fromisoformat(body["afterHoursEvaluatedAt"]).tzinfo is not None

    outbox = [o for o in store.list_outbox() if o.get("visitId") == body["id"]]
    pending = next(o for o in outbox if o["event"] == "visit.pending")
    assert pending["payload"]["afterHours"] is True
    assert pending["payload"]["policyTrigger"] == "outside_hours"
    assert pending["payload"]["hostFyi"] is True
    assert "security_head" in pending["channelHints"]
    assert "in_app" in pending["channelHints"]


def test_a3_a5_holiday_sticky_wins_inside_clock(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")
    today = datetime.now(CAMPUS_ZONE).date().isoformat()
    client.post(
        "/v1/access-rules/holidays",
        headers=auth(atoken),
        json={"date": today, "label": "Test holiday"},
    )

    created = _create_visit(
        client,
        gtoken,
        visitorName="Holiday Parent",
        visitorType="Parent",
        mobile="9822080103",
    )
    assert created.status_code == 200, created.text
    body = created.json()
    assert body["afterHours"] is True
    assert body["policyTrigger"] == "holiday"


def test_a5_holiday_plus_outside_is_both(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, closed=True)
    today = datetime.now(CAMPUS_ZONE).date().isoformat()
    if not store.holiday_on_date(SCHOOL_ID, today):
        client.post(
            "/v1/access-rules/holidays",
            headers=auth(atoken),
            json={"date": today, "label": "Closed holiday"},
        )

    created = _create_visit(
        client,
        gtoken,
        visitorName="Both Trigger",
        mobile="9822080104",
    )
    assert created.json()["afterHours"] is True
    assert created.json()["policyTrigger"] == "both"


def test_a4_c4d_host_approve_noop_when_after_hours(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, closed=True)

    created = _create_visit(client, gtoken, visitorName="Host Noop", mobile="9822080105")
    vid = created.json()["id"]
    assert created.json()["afterHours"] is True

    ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(htoken))
    assert ap.status_code == 403
    assert ap.json()["error"]["code"] == "AFTER_HOURS_SH_REQUIRED"

    admin_ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(atoken))
    assert admin_ap.status_code == 403
    assert admin_ap.json()["error"]["code"] == "AFTER_HOURS_SH_REQUIRED"

    gate_ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(gtoken))
    assert gate_ap.status_code == 403

    again = client.get(f"/v1/visits/{vid}", headers=auth(htoken))
    assert again.json()["status"] == "pending"
    assert again.json()["afterHours"] is True
    assert again.json()["passId"] is None
    assert again.json()["decidedByUserId"] is None


def test_a6_sh_approve_requires_reason_then_succeeds(client):
    gtoken = login(client, "gate", "gate123")
    stoken = login(client, "security", "sh123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, closed=True)

    created = _create_visit(client, gtoken, visitorName="SH Reason", mobile="9822080106")
    vid = created.json()["id"]
    sticky = created.json()["afterHoursEvaluatedAt"]
    trigger = created.json()["policyTrigger"]

    missing = client.post(f"/v1/visits/{vid}/approve", headers=auth(stoken))
    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION"
    assert "reason" in missing.json()["error"]["message"].lower()

    blank = client.post(
        f"/v1/visits/{vid}/approve",
        headers=auth(stoken),
        json={"reason": "   "},
    )
    assert blank.status_code == 400

    still = client.get(f"/v1/visits/{vid}", headers=auth(stoken))
    assert still.json()["status"] == "pending"

    ok = client.post(
        f"/v1/visits/{vid}/approve",
        headers=auth(stoken),
        json={"reason": "Verified vendor night call"},
    )
    assert ok.status_code == 200, ok.text
    assert ok.json()["status"] == "approved"
    assert ok.json()["decidedByUserId"] == "U-SH"
    assert ok.json()["afterHoursApproveReason"] == "Verified vendor night call"
    assert ok.json()["afterHours"] is True
    assert ok.json()["policyTrigger"] == trigger
    assert ok.json()["afterHoursEvaluatedAt"] == sticky
    assert ok.json()["passId"].startswith("P-")


def test_a6_sh_reject_requires_reason(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    stoken = login(client, "security", "sh123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, closed=True)

    created = _create_visit(client, gtoken, visitorName="SH Reject", mobile="9822080107")
    vid = created.json()["id"]

    host_rj = client.post(
        f"/v1/visits/{vid}/reject",
        headers=auth(htoken),
        json={"reason": "I am the host"},
    )
    assert host_rj.status_code == 403
    assert host_rj.json()["error"]["code"] == "AFTER_HOURS_SH_REQUIRED"
    assert client.get(f"/v1/visits/{vid}", headers=auth(stoken)).json()["status"] == "pending"

    no_reason = client.post(f"/v1/visits/{vid}/reject", headers=auth(stoken), json={})
    assert no_reason.status_code == 400
    assert no_reason.json()["error"]["code"] == "VALIDATION"

    rejected = client.post(
        f"/v1/visits/{vid}/reject",
        headers=auth(stoken),
        json={"reason": "Campus closed — reschedule"},
    )
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "rejected"
    assert rejected.json()["rejectReason"] == "Campus closed — reschedule"
    assert rejected.json()["decidedByUserId"] == "U-SH"


def test_a3_c4e_sticky_not_recomputed_on_approve(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    created = _create_visit(client, gtoken, visitorName="Sticky In Hours", mobile="9822080108")
    vid = created.json()["id"]
    evaluated = created.json()["afterHoursEvaluatedAt"]
    assert created.json()["afterHours"] is False

    # Close campus after registration — Approve must not flip to SH-only
    put_week_hours(client, atoken, closed=True)
    today = datetime.now(CAMPUS_ZONE).date().isoformat()
    client.post(
        "/v1/access-rules/holidays",
        headers=auth(atoken),
        json={"date": today, "label": "Added after create"},
    )

    ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(htoken))
    assert ap.status_code == 200, ap.text
    assert ap.json()["status"] == "approved"
    assert ap.json()["afterHours"] is False
    assert ap.json()["policyTrigger"] is None
    assert ap.json()["afterHoursEvaluatedAt"] == evaluated


def test_c4b_list_and_inside_filters(client):
    atoken = login(client, "admin", "admin123")
    listed = client.get(
        "/v1/visits?dateFrom=2026-09-16&dateTo=2026-09-16&afterHours=true",
        headers=auth(atoken),
    )
    assert listed.status_code == 200
    ids = {v["id"] for v in listed.json()["data"]}
    assert "V-AH-VENDOR" in ids
    assert "V-20260916-014" not in ids  # Priya in-hours seed
    assert all(v["afterHours"] is True for v in listed.json()["data"])

    trigger = client.get(
        "/v1/visits?dateFrom=2026-10-20&dateTo=2026-10-20&policyTrigger=holiday",
        headers=auth(atoken),
    )
    t_ids = {v["id"] for v in trigger.json()["data"]}
    assert "V-AH-HOLIDAY" in t_ids
    assert all(v["policyTrigger"] == "holiday" for v in trigger.json()["data"])

    inside = client.get("/v1/visits/inside?afterHours=true", headers=auth(atoken))
    assert inside.status_code == 200
    assert all(v.get("afterHours") for v in inside.json()["data"])


def test_demo_after_hours_vendor_and_holiday_parent_not_priya(client):
    token = login(client, "security", "sh123")
    vendor = client.get("/v1/visits/V-AH-VENDOR", headers=auth(token))
    assert vendor.status_code == 200
    assert vendor.json()["visitorName"] == "Ravi Kulkarni"
    assert vendor.json()["visitorType"] == "Vendor"
    assert vendor.json()["status"] == "pending"
    assert vendor.json()["afterHours"] is True
    assert vendor.json()["policyTrigger"] == "outside_hours"
    assert vendor.json()["visitorName"] != "Priya Sharma"

    holiday = client.get("/v1/visits/V-AH-HOLIDAY", headers=auth(token))
    assert holiday.json()["visitorName"] == "Meera Shah"
    assert holiday.json()["visitorType"] == "Parent"
    assert holiday.json()["afterHours"] is True
    assert holiday.json()["policyTrigger"] == "holiday"

    priya = client.get("/v1/visits/V-20260916-014", headers=auth(token))
    assert priya.json()["visitorName"] == "Priya Sharma"
    assert priya.json()["status"] == "inside"
    assert priya.json()["afterHours"] is False


def test_c4e1_close_exclusive_and_overnight_eval():
    row = {
        "closed": False,
        "openTime": "08:00",
        "closeTime": "18:00",
        "overnight": False,
    }
    tz = ZoneInfo(CAMPUS_TZ)
    assert is_outside_window(row, datetime(2026, 9, 16, 8, 0, tzinfo=tz)) is False
    assert is_outside_window(row, datetime(2026, 9, 16, 17, 59, tzinfo=tz)) is False
    assert is_outside_window(row, datetime(2026, 9, 16, 18, 0, tzinfo=tz)) is True
    assert is_outside_window(row, datetime(2026, 9, 16, 7, 59, tzinfo=tz)) is True

    night = {
        "closed": False,
        "openTime": "22:00",
        "closeTime": "06:00",
        "overnight": True,
    }
    assert is_outside_window(night, datetime(2026, 9, 16, 22, 0, tzinfo=tz)) is False
    assert is_outside_window(night, datetime(2026, 9, 16, 23, 30, tzinfo=tz)) is False
    assert is_outside_window(night, datetime(2026, 9, 17, 5, 59, tzinfo=tz)) is False
    assert is_outside_window(night, datetime(2026, 9, 17, 6, 0, tzinfo=tz)) is True
    assert is_outside_window(night, datetime(2026, 9, 16, 21, 59, tzinfo=tz)) is True


def test_evaluate_uses_asia_kolkata():
    ev = evaluate_after_hours(SCHOOL_ID, datetime(2026, 9, 16, 10, 0, tzinfo=CAMPUS_ZONE))
    # Seed hours 08:00-18:00 weekday, no holiday on 2026-09-16
    assert ev["afterHours"] is False
    evening = evaluate_after_hours(
        SCHOOL_ID, datetime(2026, 9, 16, 19, 30, tzinfo=CAMPUS_ZONE)
    )
    assert evening["afterHours"] is True
    assert evening["policyTrigger"] == "outside_hours"
    diwali = evaluate_after_hours(
        SCHOOL_ID, datetime(2026, 10, 20, 10, 30, tzinfo=CAMPUS_ZONE)
    )
    assert diwali["afterHours"] is True
    assert diwali["policyTrigger"] == "holiday"
