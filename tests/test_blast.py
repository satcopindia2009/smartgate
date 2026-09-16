"""Priority P2 Emergency Blast — Hub B1–B6 REST + seed."""
from __future__ import annotations

from tests.conftest import auth, login, put_week_hours


def _inside_ids(client, token):
    r = client.get("/v1/visits/inside", headers=auth(token))
    assert r.status_code == 200, r.text
    return [v["id"] for v in r.json()["data"]]


def _confirm(client, token, template_id="T-EVAC-01", instruction=None, confirm=True):
    body = {"templateId": template_id, "confirm": confirm}
    if instruction is not None:
        body["instruction"] = instruction
    return client.post("/v1/emergency/blasts", headers=auth(token), json=body)


def test_seed_blast_enabled_template_and_inside_count(client):
    token = login(client, "security", "sh123")
    cfg = client.get("/v1/schools/me/blast-config", headers=auth(token))
    assert cfg.status_code == 200
    assert cfg.json()["emergencyBlastEnabled"] is True
    assert cfg.json()["blastStaffLaneEnabled"] is False
    assert cfg.json()["blastChannelsVisitor"] == ["sms"]

    templates = client.get("/v1/emergency/blast-templates", headers=auth(token))
    assert templates.status_code == 200
    names = {t["name"] for t in templates.json()["data"]}
    assert "Evacuation — assembly ground" in names
    evac = next(t for t in templates.json()["data"] if t["id"] == "T-EVAC-01")
    assert evac["channel"] == "sms"
    assert evac["active"] is True

    preview = client.get("/v1/emergency/blasts/preview", headers=auth(token))
    assert preview.status_code == 200
    assert preview.json()["insideCount"] > 0
    inside = _inside_ids(client, token)
    assert preview.json()["insideCount"] == len(inside)
    assert "V-20260916-014" in inside  # existing Priya inside seed

    seeded = client.get("/v1/emergency/blasts/B-20260916-03", headers=auth(token))
    assert seeded.status_code == 200
    assert seeded.json()["triggeredByUserId"] == "U-SH"
    assert seeded.json()["templateId"] == "T-EVAC-01"
    assert seeded.json()["insideCount"] == len(inside)


def test_preview_matches_inside_and_excludes_escort_staff(client):
    token = login(client, "admin", "admin123")
    inside = client.get("/v1/visits/inside", headers=auth(token)).json()["data"]
    preview = client.get(
        "/v1/emergency/blasts/preview?templateId=T-EVAC-01",
        headers=auth(token),
    )
    assert preview.status_code == 200
    body = preview.json()
    assert body["insideCount"] == len(inside)
    assert body["templateId"] == "T-EVAC-01"
    assert "assembly ground" in body["instructionPreview"]
    assert body["channelsSummary"]["sms"]["planned"] == len(inside)
    assert body["channelsSummary"]["whatsapp"]["hold"] is True
    assert body["channelsSummary"]["staff"]["enabled"] is False

    staff = client.get("/v1/staff", headers=auth(token)).json()["data"]
    vikram = next(s for s in staff if s["id"] == "E01")
    assert vikram["name"] == "Vikram More"
    assert vikram["id"] not in {v["id"] for v in inside}


def test_confirm_required_and_template_only(client):
    token = login(client, "admin", "admin123")
    missing = client.post(
        "/v1/emergency/blasts",
        headers=auth(token),
        json={"templateId": "T-EVAC-01"},
    )
    assert missing.status_code == 400
    assert missing.json()["error"]["code"] == "VALIDATION"

    denied = _confirm(client, token, confirm=False)
    assert denied.status_code == 400
    assert denied.json()["error"]["code"] == "VALIDATION"
    assert "confirm" in denied.json()["error"]["message"].lower()

    no_tpl = client.post(
        "/v1/emergency/blasts",
        headers=auth(token),
        json={"confirm": True, "instruction": "Ad-hoc free text is not allowed"},
    )
    assert no_tpl.status_code == 400

    unknown = _confirm(client, token, template_id="T-NOPE")
    assert unknown.status_code == 404


def test_gate_and_host_forbidden(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    for token in (gtoken, htoken):
        prev = client.get("/v1/emergency/blasts/preview", headers=auth(token))
        assert prev.status_code == 403
        created = _confirm(client, token)
        assert created.status_code == 403
        templates = client.get("/v1/emergency/blast-templates", headers=auth(token))
        assert templates.status_code == 403
        cfg = client.get("/v1/schools/me/blast-config", headers=auth(token))
        assert cfg.status_code == 403


def test_admin_and_sh_can_confirm_sms_mock(client):
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")
    inside = set(_inside_ids(client, atoken))
    created = _confirm(client, stoken, instruction="Go to assembly ground now.")
    assert created.status_code == 200, created.text
    body = created.json()
    assert body["triggeredByUserId"] == "U-SH"
    assert body["confirmAt"]
    assert body["templateId"] == "T-EVAC-01"
    assert body["instruction"] == "Go to assembly ground now."
    assert body["insideCount"] == len(inside)
    assert body["status"] == "completed"
    visit_ids = {r["visitId"] for r in body["recipients"] if r.get("visitId")}
    assert visit_ids == inside
    assert all(r["channel"] == "sms" for r in body["recipients"])
    assert all(r["status"] == "sent" for r in body["recipients"])
    assert all(r["mobileMasked"].startswith("******") for r in body["recipients"])
    assert all("visitorName" not in r for r in body["recipients"])

    admin_ok = _confirm(client, atoken)
    assert admin_ok.status_code == 200
    assert admin_ok.json()["triggeredByUserId"] == "U-ADMIN"


def test_whatsapp_skipped_hold_no_live_send(client):
    token = login(client, "admin", "admin123")
    tpl = client.post(
        "/v1/emergency/blast-templates",
        headers=auth(token),
        json={
            "name": "WA hold sample",
            "instruction": "This must not go to WhatsApp.",
            "channel": "whatsapp",
            "active": True,
        },
    )
    assert tpl.status_code == 200, tpl.text
    created = _confirm(client, token, template_id=tpl.json()["id"])
    assert created.status_code == 200
    recipients = created.json()["recipients"]
    assert recipients
    assert all(r["channel"] == "whatsapp" for r in recipients)
    assert all(r["status"] == "skipped_hold" for r in recipients)
    assert all(r.get("providerMessageId") is None for r in recipients)
    assert all(r.get("errorCode") == "WA_HOLD" for r in recipients)
    assert created.json()["counts"]["skipped_hold"] == len(recipients)
    assert created.json()["counts"]["sent"] == 0


def test_blast_does_not_change_visit_status(client):
    token = login(client, "security", "sh123")
    before = {
        v["id"]: (v["status"], v.get("timeOut"), v.get("checkoutType"))
        for v in client.get("/v1/visits/inside", headers=auth(token)).json()["data"]
    }
    assert before
    created = _confirm(client, token)
    assert created.status_code == 200
    after_inside = client.get("/v1/visits/inside", headers=auth(token)).json()["data"]
    after = {
        v["id"]: (v["status"], v.get("timeOut"), v.get("checkoutType"))
        for v in after_inside
    }
    assert after == before
    for vid in before:
        row = client.get(f"/v1/visits/{vid}", headers=auth(token)).json()
        assert row["status"] == "inside"
        assert row.get("timeOut") is None


def test_after_hours_visitor_in_escort_staff_out(client):
    gtoken = login(client, "gate", "gate123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")
    put_week_hours(client, atoken, closed=True)
    created = client.post(
        "/v1/visits",
        headers=auth(gtoken),
        json={
            "visitorName": "Leela Iyer",
            "mobile": "9822098809",
            "visitorType": "Parent",
            "purpose": "After-hours blast audience",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "888800009999",
            "gateId": "G-MAIN",
        },
    )
    assert created.status_code == 200, created.text
    assert created.json()["afterHours"] is True
    vid = created.json()["id"]
    approved = client.post(
        f"/v1/visits/{vid}/approve",
        headers=auth(stoken),
        json={"reason": "Night parent verified"},
    )
    assert approved.status_code == 200
    checked = client.post(
        f"/v1/visits/{vid}/check-in",
        headers=auth(gtoken),
        json={"gateId": "G-MAIN"},
    )
    assert checked.status_code == 200
    assert checked.json()["status"] == "inside"

    preview = client.get("/v1/emergency/blasts/preview", headers=auth(stoken))
    inside = set(_inside_ids(client, stoken))
    assert vid in inside
    assert preview.json()["insideCount"] == len(inside)

    blast = _confirm(client, stoken)
    rec_visits = {r["visitId"] for r in blast.json()["recipients"] if r.get("visitId")}
    assert vid in rec_visits
    assert "E01" not in rec_visits
    staff = client.get("/v1/staff", headers=auth(atoken)).json()["data"]
    escort_ids = {s["id"] for s in staff if "escort" in (s.get("roleTitle") or "").lower()}
    assert "E01" in escort_ids
    assert not escort_ids.intersection(rec_visits)


def test_retry_failed_only(client):
    token = login(client, "admin", "admin123")
    created = _confirm(client, token)
    blast_id = created.json()["blastId"]
    recipients = created.json()["recipients"]
    assert recipients
    target = recipients[0]
    from app import store

    rows = store.list_blast_recipients(blast_id)
    hit = next(r for r in rows if r["visitId"] == target["visitId"] and r["channel"] == "sms")
    hit["status"] = "failed"
    hit["errorCode"] = "MOCK_FAIL"
    hit["providerMessageId"] = None
    store.put_blast_recipient(hit)
    blast = store.get_blast(blast_id)
    blast["status"] = "partial"
    store.put_blast(blast)

    retried = client.post(
        f"/v1/emergency/blasts/{blast_id}/retry-failed",
        headers=auth(token),
    )
    assert retried.status_code == 200, retried.text
    by_visit = {
        r["visitId"]: r for r in retried.json()["recipients"] if r.get("visitId")
    }
    assert by_visit[target["visitId"]]["status"] == "sent"
    assert by_visit[target["visitId"]]["providerMessageId"]
    others = [r for vid, r in by_visit.items() if vid != target["visitId"]]
    assert all(r["status"] == "sent" for r in others)
    assert retried.json()["counts"]["failed"] == 0


def test_disabled_school_hides_preview_and_confirm(client):
    token = login(client, "admin", "admin123")
    off = client.patch(
        "/v1/schools/me/blast-config",
        headers=auth(token),
        json={"emergencyBlastEnabled": False},
    )
    assert off.status_code == 200
    assert off.json()["emergencyBlastEnabled"] is False
    preview = client.get("/v1/emergency/blasts/preview", headers=auth(token))
    assert preview.status_code == 404
    created = _confirm(client, token)
    assert created.status_code == 404
    retry = client.post(
        "/v1/emergency/blasts/B-20260916-03/retry-failed",
        headers=auth(token),
    )
    assert retry.status_code == 404
    # Audit GET remains (B5)
    seeded = client.get("/v1/emergency/blasts/B-20260916-03", headers=auth(token))
    assert seeded.status_code == 200


def test_outbox_emergency_blast_no_name_list(client):
    token = login(client, "security", "sh123")
    created = _confirm(client, token)
    blast_id = created.json()["blastId"]
    outbox = client.get("/v1/internal/notify-outbox", headers=auth(token))
    events = [o for o in outbox.json()["data"] if o.get("event") == "emergency.blast"]
    match = next(o for o in events if o.get("payload", {}).get("blastId") == blast_id)
    payload = match["payload"]
    assert payload["insideCount"] == created.json()["insideCount"]
    assert payload["templateId"] == "T-EVAC-01"
    assert payload["triggeredByUserId"] == "U-SH"
    assert "sms" in payload["channels"]
    assert payload["waHold"] is True
    for ref in payload["recipientRefs"]:
        assert "visitorName" not in ref
        assert ref["mobileMasked"].startswith("******")
        assert ref["visitId"]
    assert "Priya Sharma" not in str(payload)


def test_template_crud_admin_sh(client):
    token = login(client, "admin", "admin123")
    created = client.post(
        "/v1/emergency/blast-templates",
        headers=auth(token),
        json={
            "name": "Shelter in place",
            "instruction": "Stay in classrooms. Await all-clear.",
            "channel": "sms",
        },
    )
    assert created.status_code == 200
    tid = created.json()["id"]
    patched = client.patch(
        f"/v1/emergency/blast-templates/{tid}",
        headers=auth(token),
        json={"instruction": "Stay put. Await all-clear from SH."},
    )
    assert patched.status_code == 200
    assert patched.json()["instruction"].startswith("Stay put")
    assert patched.json()["updatedByUserId"] == "U-ADMIN"
