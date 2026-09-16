"""Priority P2 Pickup & Custody — Hub P1–P6+H1."""
from __future__ import annotations

from datetime import date, timedelta

from app import store
from app.config import PICKUP_CONSENT_VERSION, SCHOOL_ID, WATERMARK
from tests.conftest import auth, login


def _upload_collector_photo(client, token, pickup_id=None):
    data = {"kind": "collector_live_photo"}
    if pickup_id:
        data["pickupId"] = pickup_id
    r = client.post(
        "/v1/media/upload",
        headers=auth(token),
        files={"file": ("c.jpg", b"collector", "image/jpeg")},
        data=data,
    )
    assert r.status_code == 200, r.text
    return r.json()["key"]


def _consent(client, token, pickup_id):
    r = client.post(
        f"/v1/pickups/{pickup_id}/consent",
        headers=auth(token),
        json={"pickupConsentVersion": PICKUP_CONSENT_VERSION},
    )
    assert r.status_code == 200, r.text
    return r.json()


def _start(client, token, **kwargs):
    body = {
        "studentId": "STU-AARAV",
        "gateId": "G-MAIN",
        "pickupReason": "early",
        **kwargs,
    }
    return client.post("/v1/pickups", headers=auth(token), json=body)


def test_seed_p6_aarav_kabir_separate_from_priya(client):
    token = login(client, "admin", "admin123")
    headers = auth(token)
    aarav = client.get("/v1/students/STU-AARAV", headers=headers)
    assert aarav.status_code == 200
    assert aarav.json()["name"] == "Aarav Mehta"
    assert aarav.json()["class"] == "5"
    assert aarav.json()["section"] == "B"
    assert aarav.json()["meta"]["watermark"] == WATERMARK

    listed = client.get("/v1/students?q=Aarav&class=5&section=B", headers=headers)
    assert listed.status_code == 200
    assert any(s["id"] == "STU-AARAV" for s in listed.json()["data"])

    people = client.get("/v1/students/STU-AARAV/authorized-pickup", headers=headers)
    names = {p["name"]: p for p in people.json()["data"]}
    assert names["Neha Mehta"]["relation"] == "parent"
    assert names["Rohan Mehta"]["relation"] == "relative"
    assert names["Neha Mehta"]["mobile"] == "9822011001"

    kabir = client.get("/v1/students/STU-KABIR/custody-flag", headers=headers)
    assert kabir.json()["flag"] == "court_order"
    assert "Rajesh" in kabir.json()["gateInstruction"]
    assert "APP-RAJESH" in kabir.json()["blockedPersonIds"]
    assert kabir.json()["allowedPersonIds"] == ["APP-SUNITA"]
    assert "courtPdf" not in kabir.json()
    assert "narrative" not in kabir.json()

    priya = client.get("/v1/visits/V-20260916-014", headers=headers)
    assert priya.json()["visitorName"] == "Priya Sharma"
    assert priya.json()["status"] == "inside"


def test_happy_path_aarav_neha_release(client):
    gtoken = login(client, "gate", "gate123")
    start = _start(client, gtoken, collectorMobile="9822011001", collectorName="Neha Mehta")
    assert start.status_code == 200, start.text
    pickup = start.json()
    assert pickup["status"] == "Matching"
    assert pickup["matchMethod"] == "mobile"
    assert pickup["collectorPickupPersonId"] == "APP-NEHA"
    assert pickup["linkedVisitId"] is None
    assert pickup["id"].startswith("PK-")
    assert pickup["meta"]["watermark"] == WATERMARK
    assert "hostId" not in pickup
    assert "visitorType" not in pickup

    pid = pickup["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.status_code == 200, rel.text
    body = rel.json()
    assert body["status"] == "Released"
    assert body["releasedAt"]
    assert body["collectorLivePhotoRef"] == key
    assert body["linkedVisitId"] is None
    assert body["override"] is False
    assert "Aarav" in body["meta"]["gatePrompt"]
    assert body["meta"]["watermark"] == WATERMARK

    visits = store.list_visits(SCHOOL_ID)
    assert all(v["id"] != pid for v in visits)
    assert store.get_visit(pid) is None


def test_relative_rohan_manual_list_select(client):
    gtoken = login(client, "gate", "gate123")
    start = _start(
        client,
        gtoken,
        collectorPickupPersonId="APP-ROHAN",
        pickupReason="appointment",
    )
    assert start.status_code == 200, start.text
    assert start.json()["matchMethod"] == "manual_list_select"
    assert start.json()["collectorPickupPersonId"] == "APP-ROHAN"
    assert start.json()["collectorName"] == "Rohan Mehta"
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.status_code == 200, rel.text
    assert rel.json()["status"] == "Released"
    assert rel.json()["collectorPickupPersonId"] == "APP-ROHAN"


def test_id_last4_match_neha(client):
    gtoken = login(client, "gate", "gate123")
    start = _start(
        client,
        gtoken,
        collectorName="Someone",
        idType="DL",
        idLast4="1001",
    )
    assert start.status_code == 200
    assert start.json()["matchMethod"] == "id"
    assert start.json()["collectorPickupPersonId"] == "APP-NEHA"


def test_name_only_never_auto_matches(client):
    gtoken = login(client, "gate", "gate123")
    start = _start(client, gtoken, collectorName="Neha Mehta")
    assert start.status_code == 200
    assert start.json()["matchMethod"] == "none"
    assert start.json()["collectorPickupPersonId"] is None
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "BlockedNotAuthorized"


def test_not_authorized_unknown_mobile(client):
    gtoken = login(client, "gate", "gate123")
    start = _start(
        client,
        gtoken,
        collectorName="Stranger",
        collectorMobile="9000000000",
    )
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.status_code == 200
    assert rel.json()["status"] == "BlockedNotAuthorized"
    assert "not on the authorized pickup list" in rel.json()["meta"]["gatePrompt"]


def test_expired_and_inactive_treat_as_not_on_list(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    yesterday = (date.today() - timedelta(days=1)).isoformat()
    patch = client.patch(
        "/v1/students/STU-AARAV/authorized-pickup/APP-ROHAN",
        headers=auth(atoken),
        json={"effectiveTo": yesterday},
    )
    assert patch.status_code == 200

    start = _start(client, gtoken, collectorPickupPersonId="APP-ROHAN")
    assert start.json()["matchMethod"] == "none"
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "BlockedNotAuthorized"

    deact = client.patch(
        "/v1/students/STU-AARAV/authorized-pickup/APP-NEHA",
        headers=auth(atoken),
        json={"active": False},
    )
    assert deact.status_code == 200
    still = client.get(
        "/v1/students/STU-AARAV/authorized-pickup",
        headers=auth(atoken),
    )
    assert any(p["id"] == "APP-NEHA" and p["active"] is False for p in still.json()["data"])


def test_kabir_blocked_custody_and_sh_alert(client):
    gtoken = login(client, "gate", "gate123")
    start = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-KABIR",
            "gateId": "G-MAIN",
            "pickupReason": "early",
            "collectorMobile": "9822012002",
            "collectorName": "Rajesh Singh",
        },
    )
    assert start.status_code == 200
    assert start.json()["collectorPickupPersonId"] == "APP-RAJESH"
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.status_code == 200
    assert rel.json()["status"] == "BlockedCustody"
    assert rel.json()["custodyFlagSnapshot"] == "court_order"
    assert "Custody restriction" in rel.json()["meta"]["gatePrompt"]

    stoken = login(client, "security", "sh123")
    outbox = client.get("/v1/internal/notify-outbox", headers=auth(stoken))
    events = [o["event"] for o in outbox.json()["data"]]
    assert "pickup.blocked_custody" in events
    hit = next(o for o in outbox.json()["data"] if o["event"] == "pickup.blocked_custody")
    assert hit["pickupId"] == pid
    assert "in_app" in hit["channelHints"]


def test_h1_allow_list_blocks_unnamed_authorized_person(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    created = client.post(
        "/v1/students/STU-KABIR/authorized-pickup",
        headers=auth(atoken),
        json={
            "name": "Vikram Singh",
            "relation": "relative",
            "mobile": "9822012009",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-09-16T10:00:00+05:30",
        },
    )
    assert created.status_code == 200, created.text
    uncle_id = created.json()["id"]
    assert created.json()["blockedByCustody"] is True

    start = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-KABIR",
            "gateId": "G-PED",
            "pickupReason": "sick",
            "collectorPickupPersonId": uncle_id,
        },
    )
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "BlockedCustody"


def test_override_path_gate_request_sh_reason(client):
    gtoken = login(client, "gate", "gate123")
    stoken = login(client, "security", "sh123")
    start = _start(
        client,
        gtoken,
        collectorName="Stranger",
        collectorMobile="9000001111",
    )
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "BlockedNotAuthorized"

    gate_override = client.post(
        f"/v1/pickups/{pid}/override",
        headers=auth(gtoken),
        json={"reason": "gate cannot self-override"},
    )
    assert gate_override.status_code == 403

    req = client.post(f"/v1/pickups/{pid}/request-override", headers=auth(gtoken))
    assert req.status_code == 200
    assert req.json()["status"] == "BlockedNotAuthorized"
    assert req.json()["meta"]["overrideRequested"] is True

    blank = client.post(
        f"/v1/pickups/{pid}/override",
        headers=auth(stoken),
        json={"reason": "   "},
    )
    assert blank.status_code == 400
    assert blank.json()["error"]["code"] == "VALIDATION"

    ok = client.post(
        f"/v1/pickups/{pid}/override",
        headers=auth(stoken),
        json={"reason": "Verified by class teacher on call"},
    )
    assert ok.status_code == 200, ok.text
    assert ok.json()["status"] == "ReleasedWithOverride"
    assert ok.json()["override"] is True
    assert ok.json()["overrideByUserId"] == "U-SH"
    assert ok.json()["overrideReason"] == "Verified by class teacher on call"
    assert ok.json()["releasedAt"]

    outbox = client.get("/v1/internal/notify-outbox", headers=auth(stoken))
    events = [o["event"] for o in outbox.json()["data"]]
    assert "pickup.override_requested" in events
    assert "pickup.override_completed" in events

    listed = client.get(
        f"/v1/pickups?override=true&studentId=STU-AARAV",
        headers=auth(stoken),
    )
    assert any(p["id"] == pid for p in listed.json()["data"])


def test_consent_required_before_photo_and_release(client):
    gtoken = login(client, "gate", "gate123")
    start = _start(client, gtoken, collectorMobile="9822011001")
    pid = start.json()["id"]
    premature = client.post(
        "/v1/media/upload",
        headers=auth(gtoken),
        files={"file": ("c.jpg", b"x", "image/jpeg")},
        data={"kind": "collector_live_photo", "pickupId": pid},
    )
    assert premature.status_code == 400
    assert premature.json()["error"]["code"] == "CONSENT_REQUIRED"

    key = _upload_collector_photo(client, gtoken)  # no pickupId — blob ok
    no_consent = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert no_consent.status_code == 400
    assert no_consent.json()["error"]["code"] == "CONSENT_REQUIRED"


def test_court_order_empty_instruction_fail_closed(client):
    stoken = login(client, "security", "sh123")
    gtoken = login(client, "gate", "gate123")
    put = client.put(
        "/v1/students/STU-KABIR/custody-flag",
        headers=auth(stoken),
        json={
            "flag": "court_order",
            "gateInstruction": "",
            "blockedPersonIds": ["APP-RAJESH"],
            "allowedPersonIds": ["APP-SUNITA"],
        },
    )
    assert put.status_code == 200
    start = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-KABIR",
            "gateId": "G-MAIN",
            "pickupReason": "early",
            "collectorPickupPersonId": "APP-SUNITA",
        },
    )
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "BlockedCustody"


def test_roles_gate_admin_sh_host(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")

    deny_gate = client.post(
        "/v1/students",
        headers=auth(gtoken),
        json={"name": "X", "class": "1", "section": "A"},
    )
    assert deny_gate.status_code == 403

    deny_host = client.post(
        "/v1/students/STU-AARAV/authorized-pickup",
        headers=auth(htoken),
        json={
            "name": "Y",
            "relation": "other",
            "mobile": "9822011999",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-09-16T10:00:00+05:30",
        },
    )
    assert deny_host.status_code == 403

    deny_host_pickup = _start(client, htoken, collectorMobile="9822011001")
    assert deny_host_pickup.status_code == 403

    admin_court = client.put(
        "/v1/students/STU-KABIR/custody-flag",
        headers=auth(atoken),
        json={
            "flag": "court_order",
            "gateInstruction": "Admin cannot write court_order",
            "blockedPersonIds": ["APP-RAJESH"],
        },
    )
    assert admin_court.status_code == 403

    admin_restricted = client.put(
        "/v1/students/STU-AARAV/custody-flag",
        headers=auth(atoken),
        json={"flag": "restricted", "gateInstruction": "Watch list only", "blockedPersonIds": []},
    )
    assert admin_restricted.status_code == 200

    sh_court = client.put(
        "/v1/students/STU-KABIR/custody-flag",
        headers=auth(stoken),
        json={
            "flag": "court_order",
            "gateInstruction": "Release only to Sunita Singh (Mother).",
            "blockedPersonIds": ["APP-RAJESH"],
            "allowedPersonIds": ["APP-SUNITA"],
        },
    )
    assert sh_court.status_code == 200

    gate_flag = client.get("/v1/students/STU-KABIR/custody-flag", headers=auth(gtoken))
    assert gate_flag.status_code == 200
    assert gate_flag.json()["flag"] == "court_order"
    assert gate_flag.json()["blockedPersonIds"] is None
    assert gate_flag.json()["allowedPersonIds"] is None
    assert gate_flag.json()["gateInstruction"]


def test_pickup_not_visit_subtype_and_optional_link_visit(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    start = _start(
        client,
        gtoken,
        collectorMobile="9822011001",
        linkVisit=True,
    )
    pid = start.json()["id"]
    assert not pid.startswith("V-")
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "Released"
    vid = rel.json()["linkedVisitId"]
    assert vid
    assert vid.startswith("V-")
    visit = client.get(f"/v1/visits/{vid}", headers=auth(atoken))
    assert visit.status_code == 200
    assert visit.json()["status"] == "pending"
    assert visit.json()["visitorType"] == "Parent"
    assert "pickup" in visit.json()["purpose"].lower()

    missing = client.get(f"/v1/visits/{pid}", headers=auth(atoken))
    assert missing.status_code == 404
    pickup_as_visit_status = rel.json()["status"]
    assert pickup_as_visit_status not in {
        "pending",
        "approved",
        "rejected",
        "inside",
        "completed",
        "force_completed",
    }


def test_gate_history_own_attempts_and_exports(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    start = _start(client, gtoken, collectorMobile="9822011001")
    pid = start.json()["id"]
    listed_gate = client.get("/v1/pickups", headers=auth(gtoken))
    assert any(p["id"] == pid for p in listed_gate.json()["data"])

    no_purpose = client.post(
        "/v1/exports",
        headers=auth(atoken),
        json={"scope": "pickup_lists"},
    )
    assert no_purpose.status_code == 400
    assert no_purpose.json()["error"]["code"] == "VALIDATION"

    lists = client.post(
        "/v1/exports",
        headers=auth(atoken),
        json={"scope": "pickup_lists", "purpose": "QA demo audit AC-D9"},
    )
    assert lists.status_code == 200
    assert "Aarav Mehta" in lists.text
    assert "Neha Mehta" in lists.text

    events = client.post(
        "/v1/exports",
        headers=auth(atoken),
        json={"scope": "pickup_events", "purpose": "history"},
    )
    assert events.status_code == 200
    assert "overrideReason" in events.text
    assert pid in events.text


def test_media_pickup_kinds_and_student_not_found(client):
    gtoken = login(client, "gate", "gate123")
    live = client.post(
        "/v1/media/upload",
        headers=auth(gtoken),
        files={"file": ("c.jpg", b"x", "image/jpeg")},
        data={"kind": "collector_live_photo"},
    )
    listed = client.post(
        "/v1/media/upload",
        headers=auth(gtoken),
        files={"file": ("l.jpg", b"y", "image/jpeg")},
        data={"kind": "pickup_list_photo"},
    )
    assert live.status_code == 200
    assert live.json()["key"].startswith("media/collector_live_photo/")
    assert listed.status_code == 200
    assert listed.json()["key"].startswith("media/pickup_list_photo/")

    missing = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-NOPE",
            "gateId": "G-MAIN",
            "pickupReason": "early",
            "collectorName": "X",
        },
    )
    assert missing.status_code == 404

    terminal = _start(client, gtoken, collectorMobile="9822011001")
    pid = terminal.json()["id"]
    _consent(client, gtoken, pid)
    key = live.json()["key"]
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "Released"
    again = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert again.status_code == 409
    assert again.json()["error"]["code"] == "INVALID_STATE"
