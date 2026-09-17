"""Explicit Access Rules PRD acceptance: AC-C4a–e + locked edges."""
from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

from app.after_hours import CAMPUS_ZONE, is_outside_window
from app.config import CAMPUS_TZ
from tests.conftest import WEEKDAYS, auth, login, put_week_hours
from tests.test_after_hours import _create_visit


def test_ac_c4a_outside_hours_or_holiday_cannot_reach_approved_without_sh(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")

    put_week_hours(client, atoken, closed=True)
    outside = _create_visit(
        client, gtoken, visitorName="AC-C4a Outside", mobile="9822080201"
    )
    assert outside.json()["afterHours"] is True
    assert outside.json()["policyTrigger"] == "outside_hours"
    host_ap = client.post(
        f"/v1/visits/{outside.json()['id']}/approve", headers=auth(htoken)
    )
    assert host_ap.status_code == 403
    assert host_ap.json()["error"]["code"] == "AFTER_HOURS_SH_REQUIRED"
    assert (
        client.get(f"/v1/visits/{outside.json()['id']}", headers=auth(htoken)).json()[
            "status"
        ]
        == "pending"
    )
    sh_ap = client.post(
        f"/v1/visits/{outside.json()['id']}/approve",
        headers=auth(stoken),
        json={"reason": "SH night clearance"},
    )
    assert sh_ap.status_code == 200
    assert sh_ap.json()["status"] == "approved"
    assert sh_ap.json()["decidedByUserId"] == "U-SH"

    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")
    today = datetime.now(CAMPUS_ZONE).date().isoformat()
    client.post(
        "/v1/access-rules/holidays",
        headers=auth(atoken),
        json={"date": today, "label": "AC-C4a holiday"},
    )
    holiday = _create_visit(
        client, gtoken, visitorName="AC-C4a Holiday", mobile="9822080202"
    )
    assert holiday.json()["afterHours"] is True
    assert holiday.json()["policyTrigger"] == "holiday"
    host_h = client.post(
        f"/v1/visits/{holiday.json()['id']}/approve", headers=auth(htoken)
    )
    assert host_h.status_code == 403
    assert host_h.json()["error"]["code"] == "AFTER_HOURS_SH_REQUIRED"
    assert (
        client.get(f"/v1/visits/{holiday.json()['id']}", headers=auth(htoken)).json()[
            "status"
        ]
        == "pending"
    )


def test_ac_c4b_visit_carries_after_hours_and_policy_trigger(client):
    atoken = login(client, "admin", "admin123")
    vendor = client.get("/v1/visits/V-AH-VENDOR", headers=auth(atoken))
    assert vendor.json()["afterHours"] is True
    assert vendor.json()["policyTrigger"] == "outside_hours"
    assert vendor.json()["afterHoursEvaluatedAt"]
    assert "after_hours" not in vendor.json()
    assert "policy_trigger" not in vendor.json()

    holiday = client.get("/v1/visits/V-AH-HOLIDAY", headers=auth(atoken))
    assert holiday.json()["afterHours"] is True
    assert holiday.json()["policyTrigger"] == "holiday"

    listed = client.get(
        "/v1/visits?dateFrom=2026-09-16&dateTo=2026-09-16&afterHours=true",
        headers=auth(atoken),
    )
    assert any(v["id"] == "V-AH-VENDOR" for v in listed.json()["data"])
    assert all(v["afterHours"] is True and v.get("policyTrigger") for v in listed.json()["data"])

    history = client.get(
        "/v1/visits?dateFrom=2026-10-20&dateTo=2026-10-20&policyTrigger=holiday",
        headers=auth(atoken),
    )
    assert any(v["id"] == "V-AH-HOLIDAY" for v in history.json()["data"])


def test_ac_c4c_admin_and_sh_edit_hours_and_holidays_without_eng_deploy(client):
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")
    gtoken = login(client, "gate", "gate123")

    put_week_hours(client, atoken, open_time="08:00", close_time="17:00")
    admin_hours = client.get("/v1/access-rules/hours", headers=auth(gtoken)).json()["data"]
    assert admin_hours[0]["closeTime"] == "17:00"
    assert admin_hours[0]["updatedByUserId"] == "U-ADMIN"

    put_week_hours(client, stoken, open_time="08:00", close_time="18:00")
    sh_hours = client.get("/v1/access-rules/hours", headers=auth(gtoken)).json()["data"]
    assert sh_hours[0]["closeTime"] == "18:00"
    assert sh_hours[0]["updatedByUserId"] == "U-SH"
    assert all(row["timezone"] == CAMPUS_TZ for row in sh_hours)

    created = client.post(
        "/v1/access-rules/holidays",
        headers=auth(stoken),
        json={"date": "2026-12-25", "label": "Christmas"},
    )
    assert created.status_code == 200, created.text
    hid = created.json()["id"]
    listed = client.get("/v1/access-rules/holidays", headers=auth(atoken))
    dates = {h["date"] for h in listed.json()["data"]}
    assert "2026-10-20" in dates  # seed Diwali
    assert "2026-12-25" in dates
    assert client.delete(f"/v1/access-rules/holidays/{hid}", headers=auth(atoken)).status_code == 200

    gate_put = client.put(
        "/v1/access-rules/hours",
        headers=auth(gtoken),
        json=[
            {
                "weekday": wd,
                "timezone": "Asia/Kolkata",
                "closed": True,
                "openTime": None,
                "closeTime": None,
                "overnight": False,
            }
            for wd in WEEKDAYS
        ],
    )
    assert gate_put.status_code == 403


def test_ac_c4d_host_approve_alone_never_clears_after_hours_gate(client):
    htoken = login(client, "host", "host123")
    stoken = login(client, "security", "sh123")
    before = client.get("/v1/visits/V-AH-VENDOR", headers=auth(stoken)).json()
    assert before["status"] == "pending"
    assert before["afterHours"] is True

    ap = client.post("/v1/visits/V-AH-VENDOR/approve", headers=auth(htoken))
    assert ap.status_code == 403
    assert ap.json()["error"]["code"] == "AFTER_HOURS_SH_REQUIRED"

    after = client.get("/v1/visits/V-AH-VENDOR", headers=auth(stoken)).json()
    assert after["status"] == "pending"
    assert after["passId"] is None
    assert after["decidedByUserId"] is None
    assert after["afterHours"] is True
    assert after["policyTrigger"] == before["policyTrigger"]
    assert after["afterHoursEvaluatedAt"] == before["afterHoursEvaluatedAt"]


def test_ac_c4e_sticky_in_hours_register_later_overrun_does_not_require_sh(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    created = _create_visit(
        client, gtoken, visitorName="AC-C4e Sticky", mobile="9822080203"
    )
    vid = created.json()["id"]
    evaluated = created.json()["afterHoursEvaluatedAt"]
    assert created.json()["afterHours"] is False
    assert created.json()["policyTrigger"] is None

    put_week_hours(client, atoken, closed=True)
    ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(htoken))
    assert ap.status_code == 200, ap.text
    assert ap.json()["status"] == "approved"
    assert ap.json()["afterHours"] is False
    assert ap.json()["policyTrigger"] is None
    assert ap.json()["afterHoursEvaluatedAt"] == evaluated
    assert ap.json()["decidedByUserId"] == "U-HOST"


def test_edge_close_exclusive_open_inclusive():
    row = {
        "closed": False,
        "openTime": "08:00",
        "closeTime": "18:00",
        "overnight": False,
    }
    tz = ZoneInfo(CAMPUS_TZ)
    assert is_outside_window(row, datetime(2026, 9, 16, 8, 0, tzinfo=tz)) is False
    assert is_outside_window(row, datetime(2026, 9, 16, 18, 0, tzinfo=tz)) is True


def test_edge_holiday_plus_in_hours_clock_still_after_hours(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")
    today = datetime.now(CAMPUS_ZONE).date().isoformat()
    client.post(
        "/v1/access-rules/holidays",
        headers=auth(atoken),
        json={"date": today, "label": "In-hours clock holiday"},
    )
    created = _create_visit(
        client, gtoken, visitorName="Holiday In Hours Clock", mobile="9822080204"
    )
    assert created.json()["afterHours"] is True
    assert created.json()["policyTrigger"] == "holiday"
