"""Contract hardening: auth, state machine, blacklist, pass scan, validation envelope."""
from __future__ import annotations

import jwt

from app import store
from app.config import JWT_ALG, JWT_SECRET, SCHOOL_ID, WATERMARK
from app.util import now_iso
from tests.conftest import auth, login


def test_gate_login_returns_gate_ids_and_jwt_claims(client):
    r = client.post("/v1/auth/login", json={"username": "gate", "password": "gate123"})
    assert r.status_code == 200
    body = r.json()
    assert body["meta"]["watermark"] == WATERMARK
    assert body["user"]["role"] == "gate"
    assert body["user"]["schoolId"] == SCHOOL_ID
    assert body["user"]["gateIds"] == ["G-MAIN", "G-PED", "G-STAFF", "G-BUS"]
    claims = jwt.decode(body["accessToken"], JWT_SECRET, algorithms=[JWT_ALG])
    assert claims["gateIds"] == body["user"]["gateIds"]
    assert claims["schoolId"] == SCHOOL_ID
    assert claims["role"] == "gate"
    assert claims.get("userId") == claims["sub"] == "U-GATE"


def test_staff_list_matches_contract_fields(client):
    token = login(client, "gate", "gate123")
    r = client.get("/v1/staff?active=true", headers=auth(token))
    assert r.status_code == 200
    assert r.json()["meta"]["watermark"] == WATERMARK
    anita = next(s for s in r.json()["data"] if s["id"] == "H03")
    assert anita["schoolId"] == SCHOOL_ID
    assert anita["name"] == "Anita Joshi"
    assert anita["roleTitle"] == "Primary Coordinator"
    assert anita["mobile"] == "9000000003"
    assert anita["userId"] == "U-HOST"
    assert anita["active"] is True
    for field in ("id", "schoolId", "name", "roleTitle", "mobile", "userId", "active"):
        assert field in anita


def test_host_list_and_inside_scoped_to_self(client):
    htoken = login(client, "host", "host123")
    listed = client.get(
        "/v1/visits?dateFrom=2026-09-16&dateTo=2026-09-16",
        headers=auth(htoken),
    )
    assert listed.status_code == 200
    ids = {v["id"] for v in listed.json()["data"]}
    assert "V-20260916-014" in ids  # Priya → H03
    assert "V-20260916-021" not in ids  # Arjun → H04
    inside = client.get("/v1/visits/inside", headers=auth(htoken))
    inside_ids = {v["id"] for v in inside.json()["data"]}
    assert "V-20260916-014" in inside_ids
    assert "V-20260916-021" not in inside_ids
    other = client.get("/v1/visits/V-20260916-021", headers=auth(htoken))
    assert other.status_code == 404


def test_unauthorized_host_approve_and_reject(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    create = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "Wrong Host",
            "mobile": "9822012345",
            "visitorType": "Guest",
            "purpose": "Accounts",
            "hostId": "H04",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "DL",
            "idNumber": "MH99XX",
            "gateId": "G-STAFF",
        },
    )
    assert create.status_code == 200, create.text
    vid = create.json()["id"]
    ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(htoken))
    assert ap.status_code == 403
    assert ap.json()["error"]["code"] == "FORBIDDEN"
    rj = client.post(
        f"/v1/visits/{vid}/reject",
        headers=auth(htoken),
        json={"reason": "not mine"},
    )
    assert rj.status_code == 403
    gate_ap = client.post(f"/v1/visits/{vid}/approve", headers=auth(gtoken))
    assert gate_ap.status_code == 403


def test_force_checkout_requires_reason(client):
    atoken = login(client, "admin", "admin123")
    htoken = login(client, "host", "host123")
    headers = auth(atoken)

    missing = client.post("/v1/visits/V-20260916-021/force-checkout", headers=headers, json={})
    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION"
    assert "reason" in missing.json()["error"]["message"].lower()

    blank = client.post(
        "/v1/visits/V-20260916-021/force-checkout",
        headers=headers,
        json={"reason": "   "},
    )
    assert blank.status_code == 400
    assert blank.json()["error"]["code"] == "VALIDATION"

    nobody = client.post("/v1/visits/V-20260916-021/force-checkout", headers=headers)
    assert nobody.status_code == 400
    assert nobody.json()["error"]["code"] == "VALIDATION"

    host_try = client.post(
        "/v1/visits/V-20260916-021/force-checkout",
        headers=auth(htoken),
        json={"reason": "nope"},
    )
    assert host_try.status_code == 403

    ok = client.post(
        "/v1/visits/V-20260916-021/force-checkout",
        headers=headers,
        json={"reason": "school closing bell"},
    )
    assert ok.status_code == 200
    assert ok.json()["status"] == "force_completed"
    assert ok.json()["checkoutType"] == "force"
    assert ok.json()["forceCheckoutReason"] == "school closing bell"
    assert ok.json()["forceCheckoutByUserId"] == "U-ADMIN"
    assert ok.json()["meta"]["watermark"] == WATERMARK


def test_invalid_pass_scan(client):
    gtoken = login(client, "gate", "gate123")
    headers = auth(gtoken)

    unknown = client.post(
        "/v1/passes/scan",
        headers=headers,
        json={"passId": "P-NOPE", "action": "check_in"},
    )
    assert unknown.status_code == 404
    assert unknown.json()["error"]["code"] == "PASS_NOT_FOUND"

    missing = client.post(
        "/v1/passes/scan",
        headers=headers,
        json={"action": "check_in"},
    )
    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION"

    already_in = client.post(
        "/v1/passes/scan",
        headers=headers,
        json={"passId": "P-4F21", "action": "check_in"},
    )
    assert already_in.status_code == 409
    assert already_in.json()["error"]["code"] == "INVALID_STATE"

    checkout_approved = client.post(
        "/v1/passes/scan",
        headers=headers,
        json={"passId": "P-C101", "action": "check_out"},
    )
    assert checkout_approved.status_code == 409
    assert checkout_approved.json()["error"]["code"] == "INVALID_STATE"


def test_seed_approved_pass_scan_check_in(client):
    gtoken = login(client, "gate", "gate123")
    scan = client.post(
        "/v1/passes/scan",
        headers=auth(gtoken),
        json={"passId": "P-C101", "action": "check_in", "gateId": "G-MAIN"},
    )
    assert scan.status_code == 200, scan.text
    assert scan.json()["status"] == "inside"
    assert scan.json()["timeIn"]
    assert scan.json()["gateInId"] == "G-MAIN"
    assert scan.json()["id"] == "V-20260916-041"


def test_pass_scan_by_token(client):
    gtoken = login(client, "gate", "gate123")
    badge = client.get("/v1/passes/P-C101", headers=auth(gtoken))
    token = badge.json()["qrToken"]
    scan = client.post(
        "/v1/passes/scan",
        headers=auth(gtoken),
        json={"token": token, "action": "check_in", "gateId": "G-MAIN"},
    )
    assert scan.status_code == 200
    assert scan.json()["status"] == "inside"


def test_blacklist_match_rules(client):
    gtoken = login(client, "gate", "gate123")
    headers = auth(gtoken)

    e164 = client.post(
        "/v1/blacklist/match",
        headers=headers,
        json={"mobile": "+91 98765 00001"},
    )
    assert e164.status_code == 200
    assert e164.json()["hit"]["id"] == "BL-01"

    by_id = client.post(
        "/v1/blacklist/match",
        headers=headers,
        json={"idType": "Aadhaar", "idNumber": "XXXX XXXX 3321"},
    )
    assert by_id.json()["hit"]["id"] == "BL-01"

    empty = client.post("/v1/blacklist/match", headers=headers, json={})
    assert empty.status_code == 200
    assert empty.json()["hit"] is None

    # Name-only is not a match field — inactive / expired skipped
    store.put_blacklist(
        {
            "id": "BL-EXP",
            "schoolId": SCHOOL_ID,
            "name": "Expired Block",
            "mobile": "9000007777",
            "idType": None,
            "idNumber": None,
            "reason": "old",
            "severity": "Block",
            "active": True,
            "addedBy": "Security Head",
            "addedAt": now_iso(),
            "expiresOn": "2020-01-01",
            "notes": None,
            "photoUrl": None,
        }
    )
    expired = client.post(
        "/v1/blacklist/match",
        headers=headers,
        json={"mobile": "9000007777"},
    )
    assert expired.json()["hit"] is None


def test_blacklist_alert_allows_create_block_override_is_sh_only(client):
    gtoken = login(client, "gate", "gate123")
    stoken = login(client, "security", "sh123")
    atoken = login(client, "admin", "admin123")

    alert = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "Neha Repeat",
            "mobile": "9876500002",
            "visitorType": "Vendor",
            "purpose": "Alert should flag not block",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/neha",
            "idType": "DL",
            "idNumber": "MH12-XXXX-8890",
            "gateId": "G-PED",
        },
    )
    assert alert.status_code == 200, alert.text
    assert alert.json()["blacklistHit"] is True
    assert alert.json()["blacklistId"] == "BL-02"
    assert alert.json()["status"] == "pending"

    gate_override = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "Vikram More",
            "mobile": "9876500001",
            "visitorType": "Guest",
            "purpose": "Gate cannot override",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "XXXXXXXX3321",
            "gateId": "G-MAIN",
            "blacklistOverride": True,
        },
    )
    assert gate_override.status_code == 403
    assert gate_override.json()["error"]["code"] == "FORBIDDEN"

    admin_write = client.post(
        "/v1/blacklist",
        headers=auth(atoken),
        json={"name": "X", "mobile": "9000001111", "reason": "no", "severity": "Alert"},
    )
    assert admin_write.status_code == 403

    sh_override = client.post(
        "/v1/visits",
        headers=auth(stoken),
        json={
            "visitorName": "Vikram More",
            "mobile": "+919876500001",
            "visitorType": "Guest",
            "purpose": "SH override",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "XXXX-XXXX-3321",
            "gateId": "G-MAIN",
            "blacklistOverride": True,
        },
    )
    assert sh_override.status_code == 200, sh_override.text
    assert sh_override.json()["status"] == "pending"
    assert sh_override.json()["blacklistHit"] is True
    assert sh_override.json()["blacklistOverrideByUserId"] == "U-SH"


def test_cannot_approve_block_hit_without_override(client):
    htoken = login(client, "host", "host123")
    ts = now_iso()
    store.put_visit(
        {
            "id": "V-BLOCK-PEND",
            "schoolId": SCHOOL_ID,
            "visitorName": "Vikram More",
            "mobile": "9876500001",
            "visitorType": "Guest",
            "purpose": "Pending block",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "XXXX-XXXX-3321",
            "idImageKey": None,
            "vehicleNumber": None,
            "accompanyingCount": 0,
            "notes": "",
            "signatureKey": None,
            "gateId": "G-MAIN",
            "registeredByUserId": "U-GATE",
            "status": "pending",
            "rejectReason": None,
            "decidedAt": None,
            "decidedByUserId": None,
            "passId": None,
            "qrToken": None,
            "timeIn": None,
            "timeOut": None,
            "gateInId": None,
            "gateOutId": None,
            "checkoutType": None,
            "forceCheckoutReason": None,
            "forceCheckoutByUserId": None,
            "blacklistHit": True,
            "blacklistId": "BL-01",
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "createdAt": ts,
            "updatedAt": ts,
        }
    )
    ap = client.post("/v1/visits/V-BLOCK-PEND/approve", headers=auth(htoken))
    assert ap.status_code == 403
    assert ap.json()["error"]["code"] == "BLACKLIST_BLOCK"


def test_invalid_state_transitions(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")

    # pending cannot check-in / meeting-done / force-checkout
    pending = "V-20260916-040"
    assert client.post(f"/v1/visits/{pending}/check-in", headers=auth(gtoken)).status_code == 409
    md = client.post(f"/v1/visits/{pending}/meeting-done", headers=auth(htoken))
    assert md.status_code == 409
    assert client.post(
        f"/v1/visits/{pending}/force-checkout",
        headers=auth(atoken),
        json={"reason": "too early"},
    ).status_code == 409

    # reject requires reason; then cannot approve
    no_reason = client.post(f"/v1/visits/{pending}/reject", headers=auth(htoken), json={})
    assert no_reason.status_code == 400
    assert no_reason.json()["error"]["code"] == "VALIDATION"

    rejected = client.post(
        f"/v1/visits/{pending}/reject",
        headers=auth(htoken),
        json={"reason": "Host busy"},
    )
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "rejected"
    assert rejected.json()["rejectReason"] == "Host busy"
    assert client.post(f"/v1/visits/{pending}/approve", headers=auth(htoken)).status_code == 409


def test_meeting_done_does_not_change_status(client):
    htoken = login(client, "host", "host123")
    r = client.post("/v1/visits/V-20260916-014/meeting-done", headers=auth(htoken))
    assert r.status_code == 200
    assert r.json()["status"] == "inside"
    assert r.json()["meetingDoneAt"]


def test_check_in_via_visit_endpoint(client):
    gtoken = login(client, "gate", "gate123")
    r = client.post(
        "/v1/visits/V-20260916-041/check-in",
        headers=auth(gtoken),
        json={"gateId": "G-MAIN"},
    )
    assert r.status_code == 200
    assert r.json()["status"] == "inside"
    out = client.post("/v1/visits/V-20260916-041/check-out", headers=auth(gtoken))
    assert out.status_code == 200
    assert out.json()["status"] == "completed"
    assert out.json()["checkoutType"] == "normal"


def test_gate_user_scoped_to_assigned_gates(client):
    store.put_user(
        {
            "id": "U-GATE-PED",
            "username": "gateped",
            "password": "gate123",
            "schoolId": SCHOOL_ID,
            "role": "gate",
            "staffId": "G01",
            "gateIds": ["G-PED"],
            "displayName": "Pedestrian only",
            "phone": "9000000011",
            "email": "gateped@demo.school",
            "active": True,
        }
    )
    token = login(client, "gateped", "gate123")
    gates = client.get("/v1/gates", headers=auth(token))
    assert {g["id"] for g in gates.json()["data"]} == {"G-PED"}

    denied = client.post(
        "/v1/visits",
        headers=auth(token),
        json={
            "visitorName": "Wrong Gate",
            "mobile": "9822090000",
            "visitorType": "Guest",
            "purpose": "Should fail",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "111122223333",
            "gateId": "G-MAIN",
        },
    )
    assert denied.status_code == 403
    assert denied.json()["error"]["code"] == "FORBIDDEN"


def test_media_kind_and_id_image_key(client):
    gtoken = login(client, "gate", "gate123")
    bad = client.post(
        "/v1/media/upload",
        headers=auth(gtoken),
        files={"file": ("x.jpg", b"xx", "image/jpeg")},
        data={"kind": "face_scan"},
    )
    assert bad.status_code == 400
    assert bad.json()["error"]["code"] == "VALIDATION"

    live = client.post(
        "/v1/media/upload",
        headers=auth(gtoken),
        files={"file": ("p.jpg", b"live", "image/jpeg")},
        data={"kind": "live_photo"},
    )
    ident = client.post(
        "/v1/media/upload",
        headers=auth(gtoken),
        files={"file": ("id.jpg", b"idimg", "image/jpeg")},
        data={"kind": "id_image"},
    )
    assert live.status_code == 200 and ident.status_code == 200
    assert live.json()["key"].startswith("media/live_photo/")
    assert ident.json()["key"].startswith("media/id_image/")
    assert "url" in live.json()

    missing_id = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "No ID",
            "mobile": "9822000011",
            "visitorType": "Guest",
            "purpose": "Need id",
            "hostId": "H03",
            "livePhotoKey": live.json()["key"],
            "idType": "Aadhaar",
            "gateId": "G-MAIN",
        },
    )
    assert missing_id.status_code == 400
    assert missing_id.json()["error"]["code"] == "VALIDATION"

    ok = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "ID Image Only",
            "mobile": "9822000022",
            "visitorType": "Guest",
            "purpose": "ID photo",
            "hostId": "H03",
            "livePhotoKey": live.json()["key"],
            "idType": "Aadhaar",
            "idImageKey": ident.json()["key"],
            "gateId": "G-MAIN",
        },
    )
    assert ok.status_code == 200, ok.text
    assert ok.json()["idImageKey"] == ident.json()["key"]


def test_invalid_mobile_and_date_range(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    bad_mobile = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "Bad Mobile",
            "mobile": "12345",
            "visitorType": "Guest",
            "purpose": "x",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Other",
            "idNumber": "1",
            "gateId": "G-MAIN",
        },
    )
    assert bad_mobile.status_code == 400
    assert bad_mobile.json()["error"]["code"] == "VALIDATION"

    wide = client.get(
        "/v1/visits?dateFrom=2026-01-01&dateTo=2026-06-02",
        headers=auth(atoken),
    )
    assert wide.status_code == 400
    assert wide.json()["error"]["code"] == "VALIDATION"


def test_demo_watermark_on_seed_visit(client):
    token = login(client, "admin", "admin123")
    r = client.get("/v1/visits/V-20260916-014", headers=auth(token))
    assert r.status_code == 200
    assert r.json()["visitorName"] == "Priya Sharma"
    assert r.json()["hostId"] == "H03"
    assert r.json()["gateId"] == "G-MAIN"
    assert r.json()["passId"] == "P-4F21"
    assert r.json()["meta"]["watermark"] == WATERMARK
    school = store.school()
    assert school["id"] == SCHOOL_ID
