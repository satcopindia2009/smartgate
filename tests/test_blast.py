"""Priority P2 Emergency Blast — Hub B1–B6 REST + make-ready seed."""
from __future__ import annotations

from tests.conftest import auth, login, put_week_hours

PACK_INSIDE = {
    "vis_p7k88",
    "vis_p7m12",
    "vis_p7m19",
    "vis_p7m22",
    "vis_p7m28",
    "vis_p7m31",
}
EVAC = "tpl_evac_assembly"


def _inside_ids(client, token):
    r = client.get("/v1/visits/inside", headers=auth(token))
    assert r.status_code == 200, r.text
    return [v["id"] for v in r.json()["data"]]


def _confirm(client, token, template_id=EVAC, instruction=None, confirm=True):
    body = {"templateId": template_id, "confirm": confirm}
    if instruction is not None:
        body["instruction"] = instruction
    return client.post("/v1/emergency/blasts", headers=auth(token), json=body)


def test_seed_blast_enabled_template_and_inside_count(client):
    token = login(client, "security", "sh123")
    me = client.get("/v1/auth/me", headers=auth(token))
    assert me.json()["displayName"] == "Meera Kulkarni"
    assert me.json()["role"] == "security_head"

    cfg = client.get("/v1/schools/me/blast-config", headers=auth(token))
    assert cfg.status_code == 200
    assert cfg.json()["emergencyBlastEnabled"] is True
    assert cfg.json()["blastStaffLaneEnabled"] is False
    assert cfg.json()["blastChannelsVisitor"] == ["sms"]

    templates = client.get("/v1/emergency/blast-templates", headers=auth(token))
    assert templates.status_code == 200
    names = {t["name"] for t in templates.json()["data"]}
    assert "Evacuation — assembly ground" in names
    assert "Shelter in place" in names
    evac = next(t for t in templates.json()["data"] if t["id"] == EVAC)
    assert evac["channel"] == "sms"
    assert evac["active"] is True

    preview = client.get("/v1/emergency/blasts/preview", headers=auth(token))
    assert preview.status_code == 200
    inside = _inside_ids(client, token)
    assert preview.json()["insideCount"] == len(inside)
    assert preview.json()["insideCount"] >= 6
    assert "V-20260916-014" in inside  # Priya MVP walkthrough stays inside
    assert PACK_INSIDE.issubset(set(inside))
    assert "V-AH-VENDOR" not in inside  # pending escort Ravi stays out
    holiday = client.get("/v1/visits/V-AH-HOLIDAY", headers=auth(token))
    assert holiday.json()["passId"] == "P-7K88"
    assert holiday.json()["status"] == "approved"

    seeded = client.get("/v1/emergency/blasts/B-20260916-03", headers=auth(token))
    assert seeded.status_code == 200
    body = seeded.json()
    assert body["blast_id"] == "B-20260916-03"
    assert body["triggeredByUserId"] == "U-SH"
    assert body["templateId"] == EVAC
    assert body["insideCount"] == 6
    assert body["status"] == "partial"
    assert body["counts"]["sent"] == 5
    assert body["counts"]["failed"] == 1
    assert body["counts"]["skipped_hold"] == 1
    rec_visits = {r["visitId"] for r in body["recipients"]}
    assert rec_visits == PACK_INSIDE
    wa = next(r for r in body["recipients"] if r["channel"] == "whatsapp")
    assert wa["status"] == "skipped_hold"
    assert wa["visitId"] == "vis_p7k88"
    failed = next(r for r in body["recipients"] if r["status"] == "failed")
    assert failed["visitId"] == "vis_p7m31"
    assert failed["errorCode"] == "MOCK_PROVIDER_UNREACHABLE"
    assert failed["mobileMasked"] == "+91-9xxx-xx7799"


def test_preview_matches_inside_and_excludes_escort_staff(client):
    token = login(client, "admin", "admin123")
    inside = client.get("/v1/visits/inside", headers=auth(token)).json()["data"]
    preview = client.get(
        f"/v1/emergency/blasts/preview?templateId={EVAC}",
        headers=auth(token),
    )
    assert preview.status_code == 200
    body = preview.json()
    assert body["insideCount"] == len(inside)
    assert body["templateId"] == EVAC
    assert "assembly ground" in (body.get("instructionPreview") or "")
    assert body["channelsSummary"]["sms"]["planned"] == len(inside)
    assert body["channelsSummary"]["whatsapp"]["hold"] is True
    assert body["channelsSummary"]["staffLaneEnabled"] is False
    assert body["channelsSummary"]["whatsappHold"] is True

    staff = client.get("/v1/staff", headers=auth(token)).json()["data"]
    vikram = next(s for s in staff if s["id"] == "E01")
    assert vikram["name"] == "Vikram More"
    assert vikram["id"] not in {v["id"] for v in inside}


def test_confirm_required_and_template_only(client):
    token = login(client, "admin", "admin123")
    missing = client.post(
        "/v1/emergency/blasts",
        headers=auth(token),
        json={"templateId": EVAC},
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
    assert body["templateId"] == EVAC
    assert body["instruction"] == "Go to assembly ground now."
    assert body["insideCount"] == len(inside)
    assert body["status"] == "completed"
    visit_ids = {r["visitId"] for r in body["recipients"] if r.get("visitId")}
    assert visit_ids == inside
    assert all(r["channel"] == "sms" for r in body["recipients"])
    assert all(r["status"] == "sent" for r in body["recipients"])
    assert all(r["mobileMasked"].startswith("+91-") for r in body["recipients"])
    assert all("xxx" in r["mobileMasked"] for r in body["recipients"])
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
    assert tpl.status_code == 201, tpl.text
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
    seeded = client.get("/v1/emergency/blasts/B-20260916-03", headers=auth(token))
    assert seeded.json()["counts"]["failed"] == 1
    retried = client.post(
        "/v1/emergency/blasts/B-20260916-03/retry-failed",
        headers=auth(token),
    )
    assert retried.status_code == 200, retried.text
    failed = [r for r in retried.json()["recipients"] if r["status"] == "failed"]
    assert failed == []
    varun = next(r for r in retried.json()["recipients"] if r["visitId"] == "vis_p7m31")
    assert varun["status"] == "sent"
    assert varun["providerMessageId"]
    wa = next(r for r in retried.json()["recipients"] if r["channel"] == "whatsapp")
    assert wa["status"] == "skipped_hold"


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
    assert payload["templateId"] == EVAC
    assert payload["triggeredByUserId"] == "U-SH"
    assert "sms" in payload["channels"]
    assert payload["waHold"] is True
    for ref in payload["recipientRefs"]:
        assert "visitorName" not in ref
        assert "xxx" in ref["mobileMasked"]
        assert ref["visitId"]
    assert "Priya Sharma" not in str(payload)


def test_template_crud_and_draft_pending_confirm(client):
    token = login(client, "admin", "admin123")
    created = client.post(
        "/v1/emergency/blast-templates",
        headers=auth(token),
        json={
            "name": "All-clear",
            "instruction": "All-clear. Resume normal movement.",
            "channel": "sms",
        },
    )
    assert created.status_code == 201
    tid = created.json()["id"]
    got = client.get(f"/v1/emergency/blast-templates/{tid}", headers=auth(token))
    assert got.status_code == 200
    patched = client.patch(
        f"/v1/emergency/blast-templates/{tid}",
        headers=auth(token),
        json={"instruction": "All-clear from SH. Resume movement."},
    )
    assert patched.status_code == 200
    assert patched.json()["instruction"].startswith("All-clear from SH")

    pending = client.post(
        "/v1/emergency/blasts",
        headers=auth(token),
        json={"templateId": EVAC, "mode": "pending_confirm"},
    )
    assert pending.status_code == 200
    assert pending.json()["status"] == "pending_confirm"
    assert pending.json()["confirmAt"] is None
    bid = pending.json()["blastId"]
    confirmed = client.post(
        f"/v1/emergency/blasts/{bid}/confirm",
        headers=auth(token),
        json={"confirm": True},
    )
    assert confirmed.status_code == 200
    assert confirmed.json()["confirmAt"]
    assert confirmed.json()["status"] == "completed"

    preview_mode = client.post(
        "/v1/emergency/blasts",
        headers=auth(token),
        json={"templateId": EVAC, "mode": "preview"},
    )
    assert preview_mode.status_code == 200
    assert preview_mode.json()["insideCount"] == len(_inside_ids(client, token))

    gone = client.delete(f"/v1/emergency/blast-templates/{tid}", headers=auth(token))
    assert gone.status_code == 204
    listed = client.get("/v1/emergency/blast-templates?active=true", headers=auth(token))
    assert tid not in {t["id"] for t in listed.json()["data"]}
