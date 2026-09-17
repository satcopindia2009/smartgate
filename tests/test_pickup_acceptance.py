"""Explicit Pickup PRD acceptance: AC-D1, AC-D2, AC-D3, F3, F6."""
from __future__ import annotations

from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from app import store
from app.config import PICKUP_CONSENT_VERSION, SCHOOL_TZ
from tests.conftest import auth, login
from tests.test_pickup import _consent, _start, _upload_collector_photo


PROOF_FIELDS = (
    "studentId",
    "collectorName",
    "collectorMobile",
    "collectorRelation",
    "matchMethod",
    "gateId",
    "attemptedAt",
    "releasedAt",
    "collectorLivePhotoRef",
    "status",
)


def test_ac_d1_not_on_list_hard_block_only_sh_override_with_reason(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")

    start = _start(
        client,
        gtoken,
        collectorName="Not On List",
        collectorMobile="9000002222",
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
    assert rel.json()["releasedAt"] is None
    assert rel.json()["override"] is False

    assert client.post(
        f"/v1/pickups/{pid}/override",
        headers=auth(gtoken),
        json={"reason": "gate self-override forbidden"},
    ).status_code == 403
    assert client.post(
        f"/v1/pickups/{pid}/override",
        headers=auth(atoken),
        json={"reason": "admin is not SH"},
    ).status_code == 403
    assert client.post(
        f"/v1/pickups/{pid}/override",
        headers=auth(stoken),
        json={"reason": ""},
    ).status_code == 400

    ok = client.post(
        f"/v1/pickups/{pid}/override",
        headers=auth(stoken),
        json={"reason": "SH verified guardian on file"},
    )
    assert ok.status_code == 200
    assert ok.json()["status"] == "ReleasedWithOverride"
    assert ok.json()["override"] is True
    assert ok.json()["overrideByUserId"] == "U-SH"
    assert ok.json()["overrideReason"] == "SH verified guardian on file"
    assert ok.json()["releasedAt"]


def test_ac_d2_custody_blocked_ids_h1_allow_list_and_allowed_release(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")

    # blockedPersonIds + court_order → Rajesh
    blocked = client.post(
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
    pid = blocked.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "BlockedCustody"
    assert rel.json()["custodyFlagSnapshot"] == "court_order"

    # H1: on list but not in allowedPersonIds
    extra = client.post(
        "/v1/students/STU-KABIR/authorized-pickup",
        headers=auth(atoken),
        json={
            "name": "Amit Singh",
            "relation": "relative",
            "mobile": "9822012010",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-09-16T10:00:00+05:30",
        },
    )
    uncle = extra.json()["id"]
    h1 = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-KABIR",
            "gateId": "G-MAIN",
            "pickupReason": "sick",
            "collectorPickupPersonId": uncle,
        },
    )
    pid2 = h1.json()["id"]
    _consent(client, gtoken, pid2)
    key2 = _upload_collector_photo(client, gtoken, pid2)
    rel2 = client.post(
        f"/v1/pickups/{pid2}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key2},
    )
    assert rel2.json()["status"] == "BlockedCustody"

    # Named allow-list person with instruction present → Released
    ok = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-KABIR",
            "gateId": "G-MAIN",
            "pickupReason": "appointment",
            "collectorPickupPersonId": "APP-SUNITA",
        },
    )
    pid3 = ok.json()["id"]
    _consent(client, gtoken, pid3)
    key3 = _upload_collector_photo(client, gtoken, pid3)
    rel3 = client.post(
        f"/v1/pickups/{pid3}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key3},
    )
    assert rel3.json()["status"] == "Released"
    assert rel3.json()["collectorPickupPersonId"] == "APP-SUNITA"


def test_ac_d3_released_event_searchable_admin_history(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    start = _start(
        client,
        gtoken,
        collectorMobile="9822011001",
        collectorName="Neha Mehta",
        pickupReason="early",
    )
    pid = start.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "Released"
    body = rel.json()
    for field in PROOF_FIELDS:
        assert body.get(field), f"missing proof field {field}"
    assert body["studentId"] == "STU-AARAV"
    assert body["collectorName"] == "Neha Mehta"
    assert body["collectorMobile"] == "9822011001"
    assert body["collectorRelation"] == "parent"
    assert body["matchMethod"] == "mobile"
    assert body["gateId"] == "G-MAIN"

    today = datetime.now(ZoneInfo(SCHOOL_TZ)).date().isoformat()
    listed = client.get(
        f"/v1/pickups?dateFrom={today}&dateTo={today}"
        f"&studentId=STU-AARAV&gateId=G-MAIN&status=Released&q=Neha",
        headers=auth(atoken),
    )
    assert listed.status_code == 200
    ids = {p["id"] for p in listed.json()["data"]}
    assert pid in ids
    hit = next(p for p in listed.json()["data"] if p["id"] == pid)
    assert hit["collectorLivePhotoRef"] == key
    assert hit["releasedAt"]
    assert hit["collectorRelation"] == "parent"

    got = client.get(f"/v1/pickups/{pid}", headers=auth(atoken))
    assert got.status_code == 200
    assert got.json()["id"] == pid
    assert got.json()["status"] == "Released"

    miss_status = client.get(
        f"/v1/pickups?studentId=STU-AARAV&status=BlockedCustody",
        headers=auth(atoken),
    )
    assert pid not in {p["id"] for p in miss_status.json()["data"]}


def test_ac_f3_expired_effective_to_treated_as_not_on_list(client):
    atoken = login(client, "admin", "admin123")
    gtoken = login(client, "gate", "gate123")
    yesterday = (date.today() - timedelta(days=1)).isoformat()
    patch = client.patch(
        "/v1/students/STU-AARAV/authorized-pickup/APP-ROHAN",
        headers=auth(atoken),
        json={"effectiveTo": yesterday},
    )
    assert patch.status_code == 200

    by_id = _start(client, gtoken, collectorPickupPersonId="APP-ROHAN")
    assert by_id.json()["matchMethod"] == "none"
    assert by_id.json()["collectorPickupPersonId"] is None

    by_mobile = _start(client, gtoken, collectorMobile="9822011002", collectorName="Rohan Mehta")
    assert by_mobile.json()["matchMethod"] == "none"
    pid = by_mobile.json()["id"]
    _consent(client, gtoken, pid)
    key = _upload_collector_photo(client, gtoken, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.json()["status"] == "BlockedNotAuthorized"


def test_ac_f6_court_order_blank_instruction_rejected_and_blocks_release(client):
    stoken = login(client, "security", "sh123")
    gtoken = login(client, "gate", "gate123")

    rejected = client.put(
        "/v1/students/STU-KABIR/custody-flag",
        headers=auth(stoken),
        json={
            "flag": "court_order",
            "gateInstruction": "",
            "blockedPersonIds": ["APP-RAJESH"],
            "allowedPersonIds": ["APP-SUNITA"],
        },
    )
    assert rejected.status_code == 400
    assert rejected.json()["error"]["code"] == "VALIDATION"
    assert "F6" in rejected.json()["error"]["message"] or "gateInstruction" in rejected.json()[
        "error"
    ]["message"]

    # Seed flag still has instruction — Sunita can release
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
    assert rel.json()["status"] == "Released"

    # Injected empty instruction still fail-closes every release
    flag = store.get_custody_flag("STU-KABIR")
    flag["gateInstruction"] = "  "
    store.put_custody_flag(flag)
    start2 = client.post(
        "/v1/pickups",
        headers=auth(gtoken),
        json={
            "studentId": "STU-KABIR",
            "gateId": "G-MAIN",
            "pickupReason": "early",
            "collectorPickupPersonId": "APP-SUNITA",
        },
    )
    pid2 = start2.json()["id"]
    _consent(client, gtoken, pid2)
    key2 = _upload_collector_photo(client, gtoken, pid2)
    rel2 = client.post(
        f"/v1/pickups/{pid2}/release",
        headers=auth(gtoken),
        json={"collectorLivePhotoRef": key2},
    )
    assert rel2.json()["status"] == "BlockedCustody"


def test_seed_mother_is_neha_mehta_not_priya_singh(client):
    token = login(client, "admin", "admin123")
    people = client.get(
        "/v1/students/STU-AARAV/authorized-pickup",
        headers=auth(token),
    ).json()["data"]
    parents = [p for p in people if p["relation"] == "parent"]
    assert [p["name"] for p in parents] == ["Neha Mehta"]
    assert all(p["name"] != "Priya Singh" for p in people)
    kabir = client.get("/v1/students/STU-KABIR", headers=auth(token)).json()
    assert kabir["name"] == "Kabir Singh"
    assert client.get("/v1/students/STU-KABIR/custody-flag", headers=auth(token)).json()[
        "flag"
    ] == "court_order"
