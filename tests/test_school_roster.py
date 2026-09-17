"""School tenant create + locked student/authorized-pickup CSV/Excel import."""
from __future__ import annotations

import csv
import io

from openpyxl import Workbook

from app import store
from app.config import (
    BOOTSTRAP_TOKEN,
    PICKUP_CONSENT_VERSION,
    PRANAY_SCHOOL_CODE,
    PRANAY_SCHOOL_ID,
    PRANAY_SCHOOL_NAME,
    RESERVED_SCHOOL_ID,
    SCHOOL_ID,
)
from app.pickup_match import is_list_eligible
from tests.conftest import auth, login

LOCKED_HEADERS = [
    "school_code",
    "student_external_id",
    "student_name",
    "class",
    "section",
    "person_name",
    "relation",
    "mobile",
    "id_last4",
    "id_type",
    "effective_from",
    "effective_to",
    "custody_flag",
    "gate_instruction",
    "allowed_person_mobiles",
    "blocked_person_mobiles",
    "consent_version",
    "consent_at",
    "person_active",
    "legal_hold",
]


def _csv_bytes(rows: list[dict], headers: list[str] | None = None) -> bytes:
    headers = headers or LOCKED_HEADERS
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=headers, extrasaction="ignore")
    writer.writeheader()
    for row in rows:
        writer.writerow({h: row.get(h, "") for h in headers})
    return buf.getvalue().encode("utf-8")


def _xlsx_bytes(rows: list[dict], headers: list[str] | None = None) -> bytes:
    headers = headers or LOCKED_HEADERS
    wb = Workbook()
    ws = wb.active
    ws.append(headers)
    for row in rows:
        ws.append([row.get(h, "") for h in headers])
    out = io.BytesIO()
    wb.save(out)
    return out.getvalue()


def _person_row(**kwargs) -> dict:
    base = {
        "student_external_id": "4A-01",
        "student_name": "Diya Shah",
        "class": "4",
        "section": "A",
        "person_name": "Kavita Shah",
        "relation": "parent",
        "mobile": "9822013001",
        "id_last4": "3001",
        "id_type": "DL",
        "consent_version": PICKUP_CONSENT_VERSION,
        "consent_at": "2026-06-01T10:00:00+05:30",
        "person_active": "true",
        "legal_hold": "false",
        "custody_flag": "none",
    }
    base.update(kwargs)
    return base


def _create_school(client, token: str | None = None, **body):
    headers = {}
    if token:
        headers.update(auth(token))
    payload = {"name": "Viren International School", **body}
    return client.post("/v1/schools", json=payload, headers=headers)


def _new_school_admin(client):
    atoken = login(client, "admin", "admin123")
    r = _create_school(
        client,
        atoken,
        slug="viren-is",
        adminPassword="viren-admin",
        securityHeadPassword="viren-sh",
    )
    assert r.status_code == 200, r.text
    body = r.json()
    token = login(
        client,
        body["credentials"]["admin"]["username"],
        body["credentials"]["admin"]["password"],
    )
    return body, token


def test_create_school_not_demo_and_credentials(client):
    token = login(client, "admin", "admin123")
    r = _create_school(
        client,
        token,
        name="North Campus School",
        timezone="Asia/Kolkata",
        adminPassword="set-admin",
        securityHeadPassword="set-sh",
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["schoolId"] != RESERVED_SCHOOL_ID
    assert body["schoolId"] != PRANAY_SCHOOL_ID
    assert body["name"] == "North Campus School"
    assert {g["id"] for g in body["gates"]} == {"G-MAIN", "G-PED", "G-STAFF", "G-BUS"}
    hours = {h["weekday"]: h for h in body["hours"]}
    assert hours["mon"]["openTime"] == "08:00"
    assert hours["sat"]["closed"] is True
    assert body["holidayCount"] == 0
    assert body["emergencyBlastEnabled"] is False
    ntoken = login(client, body["credentials"]["admin"]["username"], "set-admin")
    me = client.get("/v1/schools/me", headers=auth(ntoken))
    assert me.json()["schoolId"] == body["schoolId"]
    escort = client.get("/v1/access-rules/escort", headers=auth(ntoken))
    vendor = next(x for x in escort.json()["data"] if x["visitorType"] == "Vendor")
    assert vendor["escortRequired"] is True


def test_pranay_ops_seed_locked_ids(client):
    token = login(client, "pranay.admin", "pranay123")
    me = client.get("/v1/schools/me", headers=auth(token))
    assert me.status_code == 200
    body = me.json()
    assert body["schoolId"] == PRANAY_SCHOOL_ID
    assert body["schoolCode"] == PRANAY_SCHOOL_CODE
    assert body["name"] == PRANAY_SCHOOL_NAME
    assert {g["id"] for g in body["gates"]} == {
        "PS-G-MAIN",
        "PS-G-PED",
        "PS-G-STAFF",
        "PS-G-BUS",
    }
    sh = login(client, "pranay.sh", "pranay123")
    assert client.get("/v1/auth/me", headers=auth(sh)).json()["schoolId"] == PRANAY_SCHOOL_ID
    gate = login(client, "pranay.gate", "pranay123")
    gates = client.get("/v1/gates", headers=auth(gate))
    assert {g["id"] for g in gates.json()["data"]} == {
        "PS-G-MAIN",
        "PS-G-PED",
        "PS-G-STAFF",
        "PS-G-BUS",
    }
    # demo untouched
    demo = login(client, "admin", "admin123")
    dme = client.get("/v1/schools/me", headers=auth(demo))
    assert dme.json()["schoolId"] == SCHOOL_ID
    assert store.get_student("STU-AARAV")["schoolId"] == SCHOOL_ID


def test_cannot_create_or_overwrite_demo_or_pranay_pune(client):
    token = login(client, "admin", "admin123")
    reserved = _create_school(client, token, slug="demo-01", name="Hijack Demo")
    assert reserved.status_code == 400
    assert "reserved" in reserved.json()["error"]["message"].lower()
    bad = _create_school(
        client, token, name="Nope", slug="pranay-pune-01", schoolCode="PRANAY-PUNE"
    )
    assert bad.status_code == 400
    assert "SCH-PRANAY-PUNE-01" in bad.json()["error"]["message"]
    as_code = _create_school(
        client, token, name="Alias School", schoolCode="SCH-PRANAY-PUNE-01"
    )
    assert as_code.status_code == 400
    assert "SCH-PRANAY-01" in as_code.json()["error"]["message"]
    exists = _create_school(
        client, token, name=PRANAY_SCHOOL_NAME, schoolCode=PRANAY_SCHOOL_CODE
    )
    assert exists.status_code == 409
    demo = store.get_school(SCHOOL_ID)
    assert demo["name"] == "Demo International School"
    assert store.get_school(PRANAY_SCHOOL_ID)["name"] == PRANAY_SCHOOL_NAME
    assert store.get_school("SCH-PRANAY-PUNE-01") is None
    assert {g["id"] for g in store.list_gates(PRANAY_SCHOOL_ID)} == {
        "PS-G-MAIN",
        "PS-G-PED",
        "PS-G-STAFF",
        "PS-G-BUS",
    }


def test_bootstrap_token_creates_school_without_jwt(client):
    r = client.post(
        "/v1/schools",
        json={"name": "Bootstrap School", "slug": "boot-01"},
        headers={"X-Bootstrap-Token": BOOTSTRAP_TOKEN},
    )
    assert r.status_code == 200, r.text
    assert r.json()["schoolId"] == "SCH-BOOT-01"
    assert r.json()["schoolId"] not in {RESERVED_SCHOOL_ID, PRANAY_SCHOOL_ID}


def test_demo_seed_intact_after_new_school(client):
    token = login(client, "admin", "admin123")
    created = _create_school(client, token, name="Other School")
    assert created.status_code == 200
    gtoken = login(client, "gate", "gate123")
    gates = client.get("/v1/gates", headers=auth(gtoken))
    assert {g["id"] for g in gates.json()["data"]} == {"G-MAIN", "G-PED", "G-STAFF", "G-BUS"}
    priya = client.get("/v1/passes/P-4F21", headers=auth(gtoken))
    assert priya.json()["visitorName"] == "Priya Sharma"
    students = client.get("/v1/students?q=Aarav", headers=auth(token))
    assert any(s["id"] == "STU-AARAV" for s in students.json()["data"])
    hours = client.get("/v1/access-rules/hours", headers=auth(token))
    sat = next(h for h in hours.json()["data"] if h["weekday"] == "sat")
    assert sat["openTime"] == "08:00"
    holidays = client.get("/v1/access-rules/holidays", headers=auth(token))
    assert any(h["id"] == "HOL-DIWALI" for h in holidays.json()["data"])


def test_gate_and_host_forbidden_create_and_import(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    for tok in (gtoken, htoken):
        assert _create_school(client, tok).status_code == 403
        imp = client.post(
            "/v1/schools/me/roster/import?mode=commit",
            headers=auth(tok),
            files={"file": ("roster.csv", _csv_bytes([_person_row()]), "text/csv")},
        )
        assert imp.status_code == 403


def test_ac_imp_csv_happy_path_upsert_and_audit(client):
    school, token = _new_school_admin(client)
    rows = [
        _person_row(),
        _person_row(
            person_name="Amit Shah",
            relation="guardian",
            mobile="+91 9822013002",
            id_type="Aadhaar",
            id_last4="7890",
        ),
        _person_row(
            student_external_id="4A-02",
            student_name="Arnav Iyer",
            person_name="Neel Iyer",
            mobile="9822013003",
        ),
    ]
    r = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(token),
        files={"file": ("roster.csv", _csv_bytes(rows), "text/csv")},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["mode"] == "commit"
    assert body["dryRun"] is False
    assert body["created"] == 5  # 2 students + 3 people
    assert body["errors"] == []
    assert body["audit"]["importedByUserId"]
    assert body["audit"]["filenames"] == ["roster.csv"]
    assert body["audit"]["created"] == 5
    listed = client.get("/v1/students", headers=auth(token))
    assert {s["studentId"] for s in listed.json()["data"]} == {"4A-01", "4A-02"}
    diya = next(s for s in listed.json()["data"] if s["studentId"] == "4A-01")
    people = client.get(f"/v1/students/{diya['id']}/authorized-pickup", headers=auth(token))
    assert {p["mobile"] for p in people.json()["data"]} == {"9822013001", "9822013002"}

    rows[0]["person_name"] = "Kavita Shah Updated"
    rows[0]["student_name"] = "Diya Shah Updated"
    again = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(token),
        files={"file": ("roster.csv", _csv_bytes(rows), "text/csv")},
    )
    assert again.json()["updated"] >= 2
    listed2 = client.get("/v1/students", headers=auth(token))
    diya2 = next(s for s in listed2.json()["data"] if s["studentId"] == "4A-01")
    assert diya2["name"] == "Diya Shah Updated"
    demo = client.get("/v1/students?q=Aarav", headers=auth(login(client, "admin", "admin123")))
    assert any(s["id"] == "STU-AARAV" for s in demo.json()["data"])
    assert school["schoolId"] != SCHOOL_ID


def test_ac_imp_validate_dry_run_does_not_write(client):
    _school, token = _new_school_admin(client)
    r = client.post(
        "/v1/schools/me/roster/import?mode=validate",
        headers=auth(token),
        files={"file": ("roster.csv", _csv_bytes([_person_row()]), "text/csv")},
    )
    assert r.status_code == 200, r.text
    assert r.json()["dryRun"] is True
    assert r.json()["created"] >= 1
    listed = client.get("/v1/students", headers=auth(token))
    assert listed.json()["data"] == []


def test_ac_imp_validation_errors_and_school_code_mismatch(client):
    school, token = _new_school_admin(client)
    rows = [
        _person_row(student_external_id="9C-01", student_name="Ok Student"),
        _person_row(student_external_id="", student_name="No Id"),
        _person_row(
            school_code=RESERVED_SCHOOL_ID,
            student_external_id="9C-02",
            student_name="Wrong School",
        ),
        _person_row(student_external_id="9C-01", person_name="Ghost", relation="uncle"),
        _person_row(student_external_id="9C-01", person_name="Bad Mobile", mobile="123"),
    ]
    r = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(token),
        files={"file": ("roster.csv", _csv_bytes(rows), "text/csv")},
    )
    assert r.status_code == 200, r.text
    messages = " ".join(e["message"] for e in r.json()["errors"])
    assert "JWT" in messages or "school_code" in messages
    assert "relation" in messages
    assert "mobile" in messages.lower()
    listed = client.get("/v1/students", headers=auth(token))
    assert {s["studentId"] for s in listed.json()["data"]} == {"9C-01"}
    assert listed.json()["data"][0]["schoolId"] == school["schoolId"]


def test_ac_imp_reject_court_doc_columns(client):
    _school, token = _new_school_admin(client)
    headers = LOCKED_HEADERS + ["court_doc"]
    r = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(token),
        files={"file": ("bad.csv", _csv_bytes([_person_row()], headers), "text/csv")},
    )
    assert r.status_code == 400
    assert r.json()["error"]["code"] == "VALIDATION"
    assert "Court-document" in r.json()["error"]["message"]


def test_ac_imp_f6_and_f3_and_custody_mobiles(client):
    atoken = login(client, "admin", "admin123")
    created = _create_school(
        client,
        atoken,
        slug="f6-school",
        adminPassword="f6-admin",
        securityHeadPassword="f6-sh",
    )
    assert created.status_code == 200, created.text
    admin = login(client, created.json()["credentials"]["admin"]["username"], "f6-admin")
    sh = login(client, created.json()["credentials"]["security_head"]["username"], "f6-sh")
    f6 = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(sh),
        files={
            "file": (
                "f6.csv",
                _csv_bytes(
                    [
                        _person_row(
                            student_external_id="3A-09",
                            student_name="Kabir Import",
                            custody_flag="court_order",
                            gate_instruction="",
                        )
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert f6.status_code == 200, f6.text
    assert any("F6" in e["message"] for e in f6.json()["errors"])

    ok = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(sh),
        files={
            "file": (
                "ok.csv",
                _csv_bytes(
                    [
                        _person_row(
                            student_external_id="3A-09",
                            student_name="Kabir Import",
                            person_name="Sunita Import",
                            mobile="9822012001",
                            custody_flag="court_order",
                            gate_instruction="Release only to Sunita Import",
                            allowed_person_mobiles="9822012001",
                            blocked_person_mobiles="9822012002",
                        ),
                        _person_row(
                            student_external_id="3A-09",
                            student_name="Kabir Import",
                            person_name="Rajesh Import",
                            mobile="9822012002",
                            relation="parent",
                            custody_flag="court_order",
                            gate_instruction="Release only to Sunita Import",
                            allowed_person_mobiles="9822012001",
                            blocked_person_mobiles="9822012002",
                        ),
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert ok.status_code == 200, ok.text
    assert ok.json()["errors"] == []
    listed = client.get("/v1/students", headers=auth(admin))
    kabir = next(s for s in listed.json()["data"] if s["studentId"] == "3A-09")
    flag = client.get(f"/v1/students/{kabir['id']}/custody-flag", headers=auth(sh))
    assert flag.json()["flag"] == "court_order"
    assert flag.json()["gateInstruction"].startswith("Release only")
    people = client.get(f"/v1/students/{kabir['id']}/authorized-pickup", headers=auth(sh))
    by_mobile = {p["mobile"]: p for p in people.json()["data"]}
    assert flag.json()["allowedPersonIds"] == [by_mobile["9822012001"]["id"]]
    assert flag.json()["blockedPersonIds"] == [by_mobile["9822012002"]["id"]]

    admin_court = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(admin),
        files={
            "file": (
                "admin.csv",
                _csv_bytes(
                    [
                        _person_row(
                            student_external_id="3A-09",
                            student_name="Kabir Import",
                            custody_flag="court_order",
                            gate_instruction="Admin cannot write court_order",
                        )
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert any("security_head" in e["message"] for e in admin_court.json()["errors"])

    expired = client.post(
        "/v1/schools/me/roster/import?mode=commit",
        headers=auth(admin),
        files={
            "file": (
                "f3.csv",
                _csv_bytes(
                    [
                        _person_row(
                            student_external_id="5B-17",
                            student_name="Aarav Import",
                            person_name="Expired Uncle",
                            mobile="9822011999",
                            relation="relative",
                            effective_from="2020-01-01",
                            effective_to="2020-12-31",
                        )
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert expired.status_code == 200, expired.text
    listed2 = client.get("/v1/students", headers=auth(admin))
    aarav = next(s for s in listed2.json()["data"] if s["studentId"] == "5B-17")
    people2 = client.get(f"/v1/students/{aarav['id']}/authorized-pickup", headers=auth(admin))
    uncle = next(p for p in people2.json()["data"] if p["mobile"] == "9822011999")
    assert is_list_eligible(uncle) is False


def test_ac_imp_xlsx_and_pranay_import_does_not_touch_demo(client):
    token = login(client, "pranay.admin", "pranay123")
    r = client.post(
        "/v1/students/import?mode=commit",
        headers=auth(token),
        files={
            "file": (
                "students.xlsx",
                _xlsx_bytes(
                    [
                        {
                            "school_code": PRANAY_SCHOOL_CODE,
                            "student_external_id": "2B-11",
                            "student_name": "Excel Kid",
                            "class": "2",
                            "section": "B",
                            "legal_hold": False,
                        }
                    ]
                ),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        },
    )
    assert r.status_code == 200, r.text
    assert r.json()["created"] == 1
    p = client.post(
        "/v1/students/authorized-pickup/import?mode=commit",
        headers=auth(token),
        files={
            "file": (
                "pickup.csv",
                _csv_bytes(
                    [
                        _person_row(
                            school_code=PRANAY_SCHOOL_CODE,
                            student_external_id="2B-11",
                            student_name="Excel Kid",
                            section="B",
                            person_name="Excel Parent",
                            mobile="9822015001",
                            **{"class": "2"},
                        )
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert p.status_code == 200, p.text
    listed = client.get("/v1/students?q=Excel", headers=auth(token))
    assert listed.json()["data"][0]["schoolId"] == PRANAY_SCHOOL_ID
    demo = login(client, "admin", "admin123")
    aarav = client.get("/v1/students?q=Aarav", headers=auth(demo))
    assert any(s["id"] == "STU-AARAV" for s in aarav.json()["data"])
    assert store.get_student("STU-AARAV")["name"] == "Aarav Mehta"
    assert store.get_student("STU-KABIR")["legalHold"] is True


def test_ac_imp_colon_validate_commit_and_pranay_school_code(client):
    token = login(client, "pranay.admin", "pranay123")
    row = _person_row(
        school_code=PRANAY_SCHOOL_CODE,
        student_external_id="7C-01",
        student_name="Colon Kid",
        person_name="Colon Parent",
        mobile="9822016001",
    )
    dry = client.post(
        "/v1/students/import:validate",
        headers=auth(token),
        files={"file": ("roster.csv", _csv_bytes([row]), "text/csv")},
    )
    assert dry.status_code == 200, dry.text
    assert dry.json()["dryRun"] is True
    assert dry.json()["imported"] >= 1
    assert dry.json()["schoolId"] == PRANAY_SCHOOL_ID
    assert dry.json()["school_code"] == PRANAY_SCHOOL_CODE
    assert client.get("/v1/students?q=Colon", headers=auth(token)).json()["data"] == []

    committed = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={"file": ("roster.csv", _csv_bytes([row]), "text/csv")},
    )
    assert committed.status_code == 200, committed.text
    assert committed.json()["dryRun"] is False
    assert committed.json()["imported"] >= 1
    assert committed.json()["failed"] == 0
    assert {e.get("code") for e in committed.json()["errors"]} <= {None} or committed.json()["errors"] == []
    listed = client.get("/v1/students?q=Colon", headers=auth(token))
    assert listed.json()["data"][0]["schoolId"] == PRANAY_SCHOOL_ID

    bad = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={
            "file": (
                "bad.csv",
                _csv_bytes([_person_row(school_code="DEMO", student_external_id="7C-02")]),
                "text/csv",
            )
        },
    )
    assert bad.status_code == 200, bad.text
    assert bad.json()["failed"] >= 1
    assert any("PRANAY" in e["message"] for e in bad.json()["errors"])
    pune = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={
            "file": (
                "pune.csv",
                _csv_bytes(
                    [_person_row(school_code="SCH-PRANAY-PUNE-01", student_external_id="7C-03")]
                ),
                "text/csv",
            )
        },
    )
    assert pune.status_code == 200, pune.text
    assert any("SCH-PRANAY-PUNE-01" in e["message"] for e in pune.json()["errors"])
    assert store.get_student("STU-AARAV")["schoolId"] == SCHOOL_ID


def test_security_head_can_create_and_import(client):
    sh = login(client, "security", "sh123")
    r = _create_school(client, sh, name="SH Created School", slug="sh-created")
    assert r.status_code == 200, r.text
    ntoken = login(
        client,
        r.json()["credentials"]["security_head"]["username"],
        r.json()["credentials"]["security_head"]["password"],
    )
    imp = client.post(
        "/v1/students/import?mode=commit",
        headers=auth(ntoken),
        files={
            "file": (
                "students.csv",
                _csv_bytes(
                    [
                        {
                            "student_external_id": "1A-01",
                            "student_name": "SH Import",
                            "class": "1",
                            "section": "A",
                        }
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert imp.status_code == 200, imp.text
    assert imp.json()["created"] == 1
