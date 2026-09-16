"""Explicit Emergency Blast PRD acceptance: AC-E3a–d (+ e/f/g)."""
from __future__ import annotations

from tests.conftest import auth, login
from tests.test_blast import _confirm, _inside_ids


def test_ac_e3a_recipients_are_currently_inside_only(client):
    token = login(client, "security", "sh123")
    inside = set(_inside_ids(client, token))
    assert inside
    # Completed / pending / approved-not-in are not inside
    for vid in ("V-20260916-008", "V-20260916-040", "V-20260916-041", "V-AH-VENDOR"):
        row = client.get(f"/v1/visits/{vid}", headers=auth(token))
        assert row.status_code == 200
        assert row.json()["status"] != "inside"
        assert vid not in inside

    preview = client.get("/v1/emergency/blasts/preview", headers=auth(token))
    assert preview.json()["insideCount"] == len(inside)

    blast = _confirm(client, token)
    rec = {r["visitId"] for r in blast.json()["recipients"] if r.get("visitId")}
    assert rec == inside
    assert "V-20260916-008" not in rec  # Capt. Rao completed
    assert "V-AH-VENDOR" not in rec  # after-hours pending, not inside
    assert "E01" not in rec  # escort staff out


def test_ac_e3b_admin_sh_confirm_only_gate_host_forbidden(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    atoken = login(client, "admin", "admin123")
    stoken = login(client, "security", "sh123")

    assert client.get("/v1/emergency/blasts/preview", headers=auth(gtoken)).status_code == 403
    assert _confirm(client, htoken).status_code == 403

    no_confirm = client.post(
        "/v1/emergency/blasts",
        headers=auth(atoken),
        json={"templateId": "T-EVAC-01", "confirm": False},
    )
    assert no_confirm.status_code == 400

    ok = _confirm(client, stoken)
    assert ok.status_code == 200
    assert ok.json()["triggeredByUserId"] == "U-SH"
    assert ok.json()["confirmAt"]


def test_ac_e3c_full_audit_and_retry_failed_only(client):
    token = login(client, "admin", "admin123")
    created = _confirm(client, token, instruction="Assembly ground — move now.")
    assert created.status_code == 200
    body = created.json()
    for field in (
        "blastId",
        "triggeredByUserId",
        "triggeredAt",
        "templateId",
        "instruction",
        "insideCount",
        "recipientCount",
        "confirmAt",
        "recipients",
        "counts",
    ):
        assert field in body
    assert body["instruction"] == "Assembly ground — move now."
    assert body["triggeredByUserId"] == "U-ADMIN"
    assert body["insideCount"] == body["recipientCount"] == len(body["recipients"])
    for rec in body["recipients"]:
        assert rec["blastId"] == body["blastId"]
        assert rec["status"]
        assert rec["mobileMasked"]
        assert rec["channel"]

    fetched = client.get(f"/v1/emergency/blasts/{body['blastId']}", headers=auth(token))
    assert fetched.status_code == 200
    assert fetched.json()["blastId"] == body["blastId"]
    assert fetched.json()["instruction"] == body["instruction"]


def test_ac_e3d_wa_hold_sms_primary(client):
    token = login(client, "security", "sh123")
    sms = _confirm(client, token)
    assert all(r["channel"] == "sms" for r in sms.json()["recipients"])
    assert all(r["status"] == "sent" for r in sms.json()["recipients"])
    assert all((r.get("providerMessageId") or "").startswith("mock-sms-") for r in sms.json()["recipients"])

    tpl = client.post(
        "/v1/emergency/blast-templates",
        headers=auth(token),
        json={
            "name": "WA HOLD",
            "instruction": "Do not send live WhatsApp.",
            "channel": "whatsapp",
        },
    )
    wa = _confirm(client, token, template_id=tpl.json()["id"])
    assert all(r["status"] == "skipped_hold" for r in wa.json()["recipients"])
    assert wa.json()["counts"]["skipped_hold"] == len(wa.json()["recipients"])


def test_ac_e3e_disabled_hidden(client):
    token = login(client, "admin", "admin123")
    client.patch(
        "/v1/schools/me/blast-config",
        headers=auth(token),
        json={"emergencyBlastEnabled": False},
    )
    assert client.get("/v1/emergency/blasts/preview", headers=auth(token)).status_code == 404
    assert _confirm(client, token).status_code == 404


def test_ac_e3f_no_auto_checkout(client):
    token = login(client, "security", "sh123")
    ids = _inside_ids(client, token)
    _confirm(client, token)
    still = _inside_ids(client, token)
    assert still == ids
    for vid in ids:
        row = client.get(f"/v1/visits/{vid}", headers=auth(token)).json()
        assert row["status"] == "inside"
        assert row.get("timeOut") is None
        assert row.get("checkoutType") is None


def test_ac_e3g_masked_mobile_no_board_pii_in_sms_outbox(client):
    token = login(client, "admin", "admin123")
    created = _confirm(client, token)
    payload = next(
        o["payload"]
        for o in client.get("/v1/internal/notify-outbox", headers=auth(token)).json()["data"]
        if o.get("event") == "emergency.blast"
        and o.get("payload", {}).get("blastId") == created.json()["blastId"]
    )
    assert "visitorName" not in payload
    blob = str(payload)
    assert "Priya Sharma" not in blob
    assert "Arjun Kale" not in blob
    assert "Neha Salunkhe" not in blob
    for ref in payload["recipientRefs"]:
        assert set(ref) <= {"visitId", "mobileMasked", "channel"}
        assert ref["mobileMasked"].startswith("******")
