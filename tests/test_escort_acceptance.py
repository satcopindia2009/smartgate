"""Explicit Access Rules PRD acceptance: AC-B4a–f + locked edges."""
from __future__ import annotations

from tests.conftest import auth, login, put_week_hours
from tests.test_escort import _create_visit


def test_ac_b4a_vendor_rule_forces_escort_default_on(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    rules = client.get("/v1/access-rules/escort", headers=auth(atoken)).json()["data"]
    vendor = next(r for r in rules if r["visitorType"] == "Vendor")
    assert vendor["escortRequired"] is True
    assert vendor["allowedZones"] == ["reception", "admin"]

    created = _create_visit(client, gtoken, visitorName="AC-B4a Vendor", mobile="9822080401")
    assert created.json()["visitorType"] == "Vendor"
    assert created.json()["escortRequired"] is True
    assert created.json()["allowedZones"] == ["reception", "admin"]

    # Configurable: Admin can turn Vendor escort off (then new visits stamp false)
    client.put(
        "/v1/access-rules/escort",
        headers=auth(atoken),
        json=[
            {
                "visitorType": "Vendor",
                "escortRequired": False,
                "allowedZones": ["reception"],
            }
        ],
    )
    later = _create_visit(client, gtoken, visitorName="AC-B4a Off", mobile="9822080402")
    assert later.json()["escortRequired"] is False
    # Earlier visit stays sticky
    sticky = client.get(f"/v1/visits/{created.json()['id']}", headers=auth(atoken)).json()
    assert sticky["escortRequired"] is True


def test_ac_b4b_pass_shows_zone_labels_and_escort_name(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    created = _create_visit(client, gtoken, visitorName="AC-B4b Vendor", mobile="9822080403")
    vid = created.json()["id"]
    client.post(
        f"/v1/visits/{vid}/assign-escort",
        headers=auth(gtoken),
        json={"escortStaffId": "E01"},
    )
    ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(htoken))
    assert ap.status_code == 200, ap.text
    pass_id = ap.json()["passId"]
    badge = client.get(f"/v1/passes/{pass_id}", headers=auth(gtoken))
    assert badge.status_code == 200
    body = badge.json()
    assert body["escortRequired"] is True
    assert body["escortName"] == "Vikram More"
    assert body["allowedZoneLabels"] == ["Reception / Lobby", "Admin block"]
    assert "reception" not in body["allowedZoneLabels"]
    assert body["afterHours"] is False


def test_ac_b4c_history_stamped_no_silent_free_roam(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    created = _create_visit(client, gtoken, visitorName="AC-B4c History", mobile="9822080404")
    vid = created.json()["id"]
    row = client.get(f"/v1/visits/{vid}", headers=auth(atoken)).json()
    assert row["escortRequired"] is True
    assert row["allowedZones"] == ["reception", "admin"]
    assert "geoFence" not in row
    assert "beacon" not in row

    listed = client.get(
        "/v1/visits?dateFrom=2026-09-16&dateTo=2026-09-16&visitorType=Vendor",
        headers=auth(atoken),
    )
    assert listed.status_code == 200, listed.text
    ravi = next(v for v in listed.json()["data"] if v["id"] == "V-AH-VENDOR")
    assert ravi["escortRequired"] is True
    assert ravi["allowedZones"] == ["reception", "admin"]
    assert ravi["visitorName"] == "Ravi Deshmukh"
    assert all(v["escortRequired"] is True for v in listed.json()["data"])


def test_ac_b4d_no_geo_fence_or_beacon(client):
    gtoken = login(client, "gate", "gate123")
    for path in (
        "/v1/geo-fence",
        "/v1/geofence",
        "/v1/beacons",
        "/v1/patrol/checkpoints",
    ):
        r = client.get(path, headers=auth(gtoken))
        assert r.status_code == 404, path

    spec = client.get("/openapi.json")
    paths = spec.json().get("paths", {})
    joined = " ".join(paths)
    assert "geo-fence" not in joined
    assert "geofence" not in joined
    assert "beacon" not in joined


def test_ac_b4e_block_check_in_and_scan_without_escort_unless_waived(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    stoken = login(client, "security", "sh123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    created = _create_visit(client, gtoken, visitorName="AC-B4e Block", mobile="9822080405")
    vid = created.json()["id"]
    ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(htoken))
    assert ap.status_code == 200
    pass_id = ap.json()["passId"]

    blocked = client.post(f"/v1/visits/{vid}/check-in", headers=auth(gtoken))
    assert blocked.status_code == 403
    assert blocked.json()["error"]["code"] == "ESCORT_REQUIRED"
    assert (
        client.get(f"/v1/visits/{vid}", headers=auth(gtoken)).json()["status"]
        == "approved"
    )

    scan = client.post(
        "/v1/passes/scan",
        headers=auth(gtoken),
        json={"passId": pass_id, "action": "check_in", "gateId": "G-MAIN"},
    )
    assert scan.status_code == 403
    assert scan.json()["error"]["code"] == "ESCORT_REQUIRED"

    # Waive path — SH reason required; then check-in allowed without staff
    waived_visit = _create_visit(
        client, gtoken, visitorName="AC-B4e Waive", mobile="9822080406"
    )
    wvid = waived_visit.json()["id"]
    client.post(f"/v1/visits/{wvid}/approve", headers=auth(htoken))
    client.post(
        f"/v1/visits/{wvid}/waive-escort",
        headers=auth(stoken),
        json={"reason": "Escort staff on other gate"},
    )
    win = client.post(f"/v1/visits/{wvid}/check-in", headers=auth(gtoken))
    assert win.status_code == 200, win.text
    assert win.json()["status"] == "inside"
    assert win.json()["escortWaived"] is True
    assert win.json()["escortStaffId"] is None

    # Assign path — first visit can now check in
    client.post(
        f"/v1/visits/{vid}/assign-escort",
        headers=auth(gtoken),
        json={"escortStaffId": "E01"},
    )
    ok = client.post(f"/v1/visits/{vid}/check-in", headers=auth(gtoken))
    assert ok.status_code == 200
    assert ok.json()["status"] == "inside"
    assert ok.json()["escortStaffId"] == "E01"


def test_ac_b4f_checkout_clears_live_escort_history_retains(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    created = _create_visit(client, gtoken, visitorName="AC-B4f Clear", mobile="9822080407")
    vid = created.json()["id"]
    client.post(
        f"/v1/visits/{vid}/assign-escort",
        headers=auth(gtoken),
        json={"escortStaffId": "E01"},
    )
    ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(htoken))
    client.post(f"/v1/visits/{vid}/check-in", headers=auth(gtoken))
    out = client.post(f"/v1/visits/{vid}/check-out", headers=auth(gtoken))
    assert out.status_code == 200
    body = out.json()
    assert body["status"] == "completed"
    assert body["escortClearedAt"]
    assert body["escortStaffId"] == "E01"
    assert body["escortName"] == "Vikram More"
    assert body["allowedZones"] == ["reception", "admin"]

    history = client.get(
        f"/v1/visits/{vid}",
        headers=auth(atoken),
    ).json()
    assert history["escortClearedAt"] == body["escortClearedAt"]
    assert history["escortStaffId"] == "E01"
    assert history["escortName"] == "Vikram More"

    badge = client.get(f"/v1/passes/{ap.json()['passId']}", headers=auth(gtoken)).json()
    assert badge["escortName"] == "Vikram More"
    assert badge["escortRequired"] is True

    # Force-checkout also stamps escortClearedAt
    created2 = _create_visit(client, gtoken, visitorName="AC-B4f Force", mobile="9822080408")
    vid2 = created2.json()["id"]
    client.post(
        f"/v1/visits/{vid2}/assign-escort",
        headers=auth(gtoken),
        json={"escortStaffId": "E01"},
    )
    client.post(f"/v1/visits/{vid2}/approve", headers=auth(htoken))
    client.post(f"/v1/visits/{vid2}/check-in", headers=auth(gtoken))
    forced = client.post(
        f"/v1/visits/{vid2}/force-checkout",
        headers=auth(atoken),
        json={"reason": "Bell"},
    )
    assert forced.status_code == 200
    assert forced.json()["status"] == "force_completed"
    assert forced.json()["escortClearedAt"]
    assert forced.json()["escortStaffId"] == "E01"
    assert forced.json()["escortName"] == "Vikram More"
