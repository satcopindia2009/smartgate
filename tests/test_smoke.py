"""Smoke tests for MVP stub happy paths."""
from __future__ import annotations

from tests.conftest import login


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_login_me_and_gates(client):
    token = login(client, "gate", "gate123")
    me = client.get("/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200
    assert me.json()["role"] == "gate"
    gates = client.get("/v1/gates", headers={"Authorization": f"Bearer {token}"})
    assert gates.status_code == 200
    names = {g["name"] for g in gates.json()["data"]}
    assert names == {"Main Gate", "Pedestrian Gate", "Staff Gate", "Bus Bay"}


def test_priya_pass_seed(client):
    token = login(client, "gate", "gate123")
    r = client.get("/v1/passes/P-4F21", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 200
    body = r.json()
    assert body["visitorName"] == "Priya Sharma"
    assert body["passId"] == "P-4F21"
    assert body["status"] == "inside"


def test_visit_lifecycle(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")

    up = client.post(
        "/v1/media/upload",
        headers={"Authorization": f"Bearer {gtoken}"},
        files={"file": ("p.jpg", b"fakejpeg", "image/jpeg")},
        data={"kind": "live_photo"},
    )
    assert up.status_code == 200
    key = up.json()["key"]

    create = client.post(
        "/v1/visits",
        headers={"Authorization": f"Bearer {gtoken}"},
        json={
            "visitorName": "Smoke Parent",
            "mobile": "9822088888",
            "visitorType": "Parent",
            "purpose": "Smoke test",
            "hostId": "H03",
            "livePhotoKey": key,
            "idType": "Aadhaar",
            "idNumber": "123456789012",
            "gateId": "G-MAIN",
        },
    )
    assert create.status_code == 200, create.text
    vid = create.json()["id"]
    assert create.json()["status"] == "pending"

    # Host cannot approve someone else's visit — create is H03 so OK
    ap = client.post(f"/v1/visits/{vid}/approve", headers={"Authorization": f"Bearer {htoken}"})
    assert ap.status_code == 200, ap.text
    assert ap.json()["status"] == "approved"
    pass_id = ap.json()["passId"]
    assert pass_id.startswith("P-")

    scan_in = client.post(
        "/v1/passes/scan",
        headers={"Authorization": f"Bearer {gtoken}"},
        json={"passId": pass_id, "action": "check_in", "gateId": "G-MAIN"},
    )
    assert scan_in.status_code == 200
    assert scan_in.json()["status"] == "inside"

    md = client.post(
        f"/v1/visits/{vid}/meeting-done",
        headers={"Authorization": f"Bearer {htoken}"},
    )
    assert md.status_code == 200
    assert md.json()["meetingDoneAt"]

    scan_out = client.post(
        "/v1/passes/scan",
        headers={"Authorization": f"Bearer {gtoken}"},
        json={"passId": pass_id, "action": "check_out"},
    )
    assert scan_out.status_code == 200
    assert scan_out.json()["status"] == "completed"


def test_host_cannot_approve_others(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")  # H03
    create = client.post(
        "/v1/visits",
        headers={"Authorization": f"Bearer {gtoken}"},
        json={
            "visitorName": "Other Host Visit",
            "mobile": "9822077777",
            "visitorType": "Guest",
            "purpose": "Accounts",
            "hostId": "H04",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "DL",
            "idNumber": "MH99XX",
            "gateId": "G-STAFF",
        },
    )
    vid = create.json()["id"]
    ap = client.post(f"/v1/visits/{vid}/approve", headers={"Authorization": f"Bearer {htoken}"})
    assert ap.status_code == 403


def test_blacklist_block_and_match(client):
    gtoken = login(client, "gate", "gate123")
    stoken = login(client, "security", "sh123")

    m = client.post(
        "/v1/blacklist/match",
        headers={"Authorization": f"Bearer {gtoken}"},
        json={"mobile": "9876500001"},
    )
    assert m.status_code == 200
    assert m.json()["hit"]["id"] == "BL-01"

    blocked = client.post(
        "/v1/visits",
        headers={"Authorization": f"Bearer {gtoken}"},
        json={
            "visitorName": "Vikram More",
            "mobile": "9876500001",
            "visitorType": "Guest",
            "purpose": "Should block",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "XXXX-XXXX-3321",
            "gateId": "G-MAIN",
        },
    )
    assert blocked.status_code == 403
    assert blocked.json()["error"]["code"] == "BLACKLIST_BLOCK"

    # SH can soft-deactivate
    patch = client.patch(
        "/v1/blacklist/BL-01",
        headers={"Authorization": f"Bearer {stoken}"},
        json={"active": False},
    )
    assert patch.status_code == 200
    assert patch.json()["active"] is False


def test_force_checkout_admin(client):
    atoken = login(client, "admin", "admin123")
    # Arjun is inside
    r = client.post(
        "/v1/visits/V-20260916-021/force-checkout",
        headers={"Authorization": f"Bearer {atoken}"},
        json={"reason": "school closing"},
    )
    assert r.status_code == 200
    assert r.json()["status"] == "force_completed"
    assert r.json()["checkoutType"] == "force"


def test_staff_admin_only(client):
    gtoken = login(client, "gate", "gate123")
    deny = client.post(
        "/v1/staff",
        headers={"Authorization": f"Bearer {gtoken}"},
        json={"name": "X", "roleTitle": "Y"},
    )
    assert deny.status_code == 403

    atoken = login(client, "admin", "admin123")
    ok = client.post(
        "/v1/staff",
        headers={"Authorization": f"Bearer {atoken}"},
        json={"name": "New Teacher", "roleTitle": "Teacher", "mobile": "9000111222"},
    )
    assert ok.status_code == 200
    assert ok.json()["name"] == "New Teacher"


def test_error_envelope(client):
    r = client.get("/v1/auth/me")
    assert r.status_code == 401
    body = r.json()
    assert "error" in body
    assert "code" in body["error"]
    assert "message" in body["error"]


def test_inside_board(client):
    token = login(client, "admin", "admin123")
    r = client.get("/v1/visits/inside", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 200
    ids = {v["id"] for v in r.json()["data"]}
    assert "V-20260916-014" in ids  # Priya
