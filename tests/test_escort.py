"""Priority P2 escort / zones — Hub B4 REST + visit stamps."""
from __future__ import annotations

from tests.conftest import auth, login, put_week_hours


def _create_visit(client, token, **kwargs):
    body = {
        "visitorName": "Escort Test Visitor",
        "mobile": "9822080301",
        "visitorType": "Vendor",
        "purpose": "Escort zone test",
        "hostId": "H03",
        "livePhotoKey": "media/live_photo/arjun",
        "idType": "DL",
        "idNumber": "MH12ESC0301",
        "gateId": "G-MAIN",
        **kwargs,
    }
    return client.post("/v1/visits", headers=auth(token), json=body)


def test_seed_zones_fixed_keys_and_vikram_escort_staff(client):
    token = login(client, "admin", "admin123")
    zones = client.get("/v1/zones", headers=auth(token))
    assert zones.status_code == 200
    keys = [z["key"] for z in zones.json()["data"]]
    assert keys == [
        "reception",
        "admin",
        "classroom",
        "sports",
        "lab",
        "restricted",
        "parking",
    ]
    labels = {z["key"]: z["label"] for z in zones.json()["data"]}
    assert labels["reception"] == "Reception / Lobby"
    assert labels["restricted"].startswith("Restricted")

    staff = client.get("/v1/staff?active=true", headers=auth(token))
    vikram = next(s for s in staff.json()["data"] if s["id"] == "E01")
    assert vikram["name"] == "Vikram More"
    assert vikram["active"] is True


def test_seed_escort_defaults_vendor_on(client):
    token = login(client, "gate", "gate123")
    r = client.get("/v1/access-rules/escort", headers=auth(token))
    assert r.status_code == 200
    by_type = {row["visitorType"]: row for row in r.json()["data"]}
    assert set(by_type) == {"Parent", "Vendor", "Guest", "Official", "Alumni"}
    assert by_type["Vendor"]["escortRequired"] is True
    assert by_type["Vendor"]["allowedZones"] == ["reception", "admin"]
    assert by_type["Parent"]["escortRequired"] is False
    assert by_type["Parent"]["allowedZones"] == ["reception"]
    assert by_type["Guest"]["escortRequired"] is False
    assert by_type["Official"]["allowedZones"] == ["reception", "admin"]
    assert by_type["Alumni"]["allowedZones"] == ["reception"]


def test_patch_zone_label_only_admin_sh(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    deny = client.patch(
        "/v1/zones/reception",
        headers=auth(gtoken),
        json={"label": "Front desk"},
    )
    assert deny.status_code == 403

    ok = client.patch(
        "/v1/zones/reception",
        headers=auth(atoken),
        json={"label": "Front lobby"},
    )
    assert ok.status_code == 200, ok.text
    assert ok.json()["key"] == "reception"
    assert ok.json()["label"] == "Front lobby"

    unknown = client.patch(
        "/v1/zones/cafeteria",
        headers=auth(atoken),
        json={"label": "Canteen"},
    )
    assert unknown.status_code == 404


def test_put_escort_restricted_force_and_gate_forbidden(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    deny = client.put(
        "/v1/access-rules/escort",
        headers=auth(gtoken),
        json=[
            {
                "visitorType": "Guest",
                "escortRequired": False,
                "allowedZones": ["reception"],
            }
        ],
    )
    assert deny.status_code == 403

    forced = client.put(
        "/v1/access-rules/escort",
        headers=auth(atoken),
        json=[
            {
                "visitorType": "Guest",
                "escortRequired": False,
                "allowedZones": ["reception", "restricted"],
            }
        ],
    )
    assert forced.status_code == 200, forced.text
    guest = next(r for r in forced.json()["data"] if r["visitorType"] == "Guest")
    assert guest["escortRequired"] is True
    assert "restricted" in guest["allowedZones"]

    bad = client.put(
        "/v1/access-rules/escort",
        headers=auth(atoken),
        json=[
            {
                "visitorType": "Alumni",
                "escortRequired": False,
                "allowedZones": ["cafeteria"],
            }
        ],
    )
    assert bad.status_code == 400
    assert bad.json()["error"]["code"] == "VALIDATION"


def test_visit_create_stamps_escort_from_rule_contractor_maps_vendor(client):
    gtoken = login(client, "gate", "gate123")
    vendor = _create_visit(client, gtoken, visitorName="Stamp Vendor", mobile="9822080302")
    assert vendor.status_code == 200, vendor.text
    body = vendor.json()
    assert body["escortRequired"] is True
    assert body["allowedZones"] == ["reception", "admin"]
    assert body["escortStaffId"] is None
    assert body["escortWaived"] is False
    assert body["afterHours"] in (True, False)

    contractor = _create_visit(
        client,
        gtoken,
        visitorName="Mapped Contractor",
        mobile="9822080303",
        visitorType="Contractor",
    )
    assert contractor.status_code == 200, contractor.text
    assert contractor.json()["visitorType"] == "Vendor"
    assert contractor.json()["escortRequired"] is True
    assert contractor.json()["allowedZones"] == ["reception", "admin"]

    parent = _create_visit(
        client,
        gtoken,
        visitorName="Stamp Parent",
        mobile="9822080304",
        visitorType="Parent",
        livePhotoKey="media/live_photo/priya",
        idType="Aadhaar",
        idNumber="111122223334",
    )
    assert parent.json()["escortRequired"] is False
    assert parent.json()["allowedZones"] == ["reception"]


def test_assign_escort_gate_host_suggest_sh_waive(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    stoken = login(client, "security", "sh123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")

    created = _create_visit(client, gtoken, visitorName="Assign Path", mobile="9822080305")
    vid = created.json()["id"]

    host_suggest = client.post(
        f"/v1/visits/{vid}/assign-escort",
        headers=auth(htoken),
        json={"escortStaffId": "E01"},
    )
    assert host_suggest.status_code == 200, host_suggest.text
    assert host_suggest.json()["escortSuggestedByHost"] == "E01"
    assert host_suggest.json()["escortStaffId"] is None

    admin_deny = client.post(
        f"/v1/visits/{vid}/assign-escort",
        headers=auth(atoken),
        json={"escortStaffId": "E01"},
    )
    assert admin_deny.status_code == 403

    assigned = client.post(
        f"/v1/visits/{vid}/assign-escort",
        headers=auth(gtoken),
        json={"escortStaffId": "E01"},
    )
    assert assigned.status_code == 200
    assert assigned.json()["escortStaffId"] == "E01"
    assert assigned.json()["escortName"] == "Vikram More"
    assert assigned.json()["escortSuggestedByHost"] == "E01"

    other = _create_visit(client, gtoken, visitorName="Waive Path", mobile="9822080306")
    oid = other.json()["id"]
    gate_waive = client.post(
        f"/v1/visits/{oid}/waive-escort",
        headers=auth(gtoken),
        json={"reason": "Gate cannot waive"},
    )
    assert gate_waive.status_code == 403
    blank = client.post(
        f"/v1/visits/{oid}/waive-escort",
        headers=auth(stoken),
        json={"reason": "   "},
    )
    assert blank.status_code == 400
    waived = client.post(
        f"/v1/visits/{oid}/waive-escort",
        headers=auth(stoken),
        json={"reason": "No escort available — SH override"},
    )
    assert waived.status_code == 200
    assert waived.json()["escortWaived"] is True
    assert waived.json()["escortWaiveReason"] == "No escort available — SH override"


def test_ravi_vendor_assignable_keep_p7k88_meera_priya(client):
    gtoken = login(client, "gate", "gate123")
    stoken = login(client, "security", "sh123")
    ravi = client.get("/v1/visits/V-AH-VENDOR", headers=auth(stoken)).json()
    assert ravi["visitorName"] == "Ravi Deshmukh"
    assert ravi["visitorType"] == "Vendor"
    assert ravi["escortRequired"] is True
    assert ravi["allowedZones"] == ["reception", "admin"]
    assert ravi["escortStaffId"] is None
    assert ravi["status"] == "pending"
    assert ravi["afterHours"] is True

    assigned = client.post(
        "/v1/visits/V-AH-VENDOR/assign-escort",
        headers=auth(gtoken),
        json={"escortStaffId": "E01"},
    )
    assert assigned.status_code == 200
    assert assigned.json()["escortStaffId"] == "E01"
    assert assigned.json()["escortName"] == "Vikram More"
    assert assigned.json()["afterHours"] is True
    assert assigned.json()["status"] == "pending"

    holiday = client.get("/v1/visits/V-AH-HOLIDAY", headers=auth(stoken)).json()
    assert holiday["visitorName"] == "Deepak Nair"
    assert holiday["hostId"] == "H01"
    assert holiday["passId"] == "P-7K88"
    assert holiday["escortRequired"] is False
    assert holiday["allowedZones"] == ["reception"]

    badge = client.get("/v1/passes/P-7K88", headers=auth(gtoken)).json()
    assert badge["passId"] == "P-7K88"
    assert badge["hostName"] == "Meera Kulkarni"
    assert badge["escortRequired"] is False
    assert badge["allowedZoneLabels"] == ["Reception / Lobby"]
    assert badge["afterHours"] is True

    priya = client.get("/v1/visits/V-20260916-014", headers=auth(stoken)).json()
    assert priya["visitorName"] == "Priya Sharma"
    assert priya["status"] == "inside"
    assert priya["passId"] == "P-4F21"
    assert priya["escortRequired"] is False
    priya_pass = client.get("/v1/passes/P-4F21", headers=auth(gtoken)).json()
    assert priya_pass["visitorName"] == "Priya Sharma"
    assert priya_pass["escortRequired"] is False


def test_reject_clears_host_escort_suggestion(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    put_week_hours(client, atoken, open_time="00:00", close_time="23:59")
    created = _create_visit(
        client,
        gtoken,
        visitorName="Reject Suggest",
        mobile="9822080307",
        visitorType="Parent",
        livePhotoKey="media/live_photo/priya",
        idType="Aadhaar",
        idNumber="111122223335",
    )
    vid = created.json()["id"]
    client.post(
        f"/v1/visits/{vid}/assign-escort",
        headers=auth(htoken),
        json={"escortStaffId": "E01"},
    )
    rejected = client.post(
        f"/v1/visits/{vid}/reject",
        headers=auth(htoken),
        json={"reason": "Reschedule"},
    )
    assert rejected.status_code == 200
    assert rejected.json()["escortSuggestedByHost"] is None
