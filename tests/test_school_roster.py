"""School tenant create + student/authorized-pickup CSV/Excel import."""
from __future__ import annotations

import csv
import io

from openpyxl import Workbook

from app import store
from app.config import BOOTSTRAP_TOKEN, PICKUP_CONSENT_VERSION, RESERVED_SCHOOL_ID, SCHOOL_ID
from tests.conftest import auth, login

STUDENT_HEADERS = [
    "schoolId",
    "studentId",
    "name",
    "class",
    "section",
    "active",
    "enrollmentEndedAt",
    "legalHold",
]
PICKUP_HEADERS = [
    "schoolId",
    "studentId",
    "personName",
    "relation",
    "mobile",
    "idType",
    "idNumber",
    "idLast4",
    "active",
    "effectiveFrom",
    "effectiveTo",
    "pickupConsentVersion",
    "pickupConsentAt",
]


def _csv_bytes(headers: list[str], rows: list[dict]) -> bytes:
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=headers, extrasaction="ignore")
    writer.writeheader()
    for row in rows:
        writer.writerow({h: row.get(h, "") for h in headers})
    return buf.getvalue().encode("utf-8")


def _xlsx_bytes(headers: list[str], rows: list[dict]) -> bytes:
    wb = Workbook()
    ws = wb.active
    ws.append(headers)
    for row in rows:
        ws.append([row.get(h, "") for h in headers])
    out = io.BytesIO()
    wb.save(out)
    return out.getvalue()


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
    assert body["schoolId"] != SCHOOL_ID
    assert body["name"] == "North Campus School"
    assert body["timezone"] == "Asia/Kolkata"
    assert {g["id"] for g in body["gates"]} == {"G-MAIN", "G-PED", "G-STAFF", "G-BUS"}
    assert all(g["schoolId"] == body["schoolId"] for g in body["gates"])
    hours = {h["weekday"]: h for h in body["hours"]}
    assert hours["mon"]["openTime"] == "08:00"
    assert hours["fri"]["closeTime"] == "18:00"
    assert hours["sat"]["closed"] is True
    assert hours["sun"]["closed"] is True
    assert body["holidayCount"] == 0
    assert body["emergencyBlastEnabled"] is False
    creds = body["credentials"]
    assert creds["admin"]["password"] == "set-admin"
    assert creds["security_head"]["password"] == "set-sh"
    assert creds["admin"]["username"] != "admin"

    ntoken = login(client, creds["admin"]["username"], creds["admin"]["password"])
    me = client.get("/v1/schools/me", headers=auth(ntoken))
    assert me.status_code == 200
    assert me.json()["schoolId"] == body["schoolId"]
    assert "credentials" not in me.json()

    escort = client.get("/v1/access-rules/escort", headers=auth(ntoken))
    assert escort.status_code == 200
    vendor = next(x for x in escort.json()["data"] if x["visitorType"] == "Vendor")
    assert vendor["escortRequired"] is True
    assert vendor["allowedZones"] == ["reception", "admin"]


def test_bootstrap_token_creates_school_without_jwt(client):
    r = client.post(
        "/v1/schools",
        json={"name": "Bootstrap School", "slug": "boot-01"},
        headers={"X-Bootstrap-Token": BOOTSTRAP_TOKEN},
    )
    assert r.status_code == 200, r.text
    assert r.json()["schoolId"] == "SCH-BOOT-01"
    assert r.json()["schoolId"] != RESERVED_SCHOOL_ID


def test_cannot_create_or_overwrite_demo_school(client):
    token = login(client, "admin", "admin123")
    reserved = _create_school(client, token, slug="demo-01", name="Hijack Demo")
    assert reserved.status_code == 400
    assert reserved.json()["error"]["code"] == "VALIDATION"
    assert "reserved" in reserved.json()["error"]["message"].lower()

    demo = store.get_school(SCHOOL_ID)
    assert demo is not None
    assert demo["name"] == "Demo International School"
    assert demo["id"] == RESERVED_SCHOOL_ID


def test_demo_seed_intact_after_new_school(client):
    token = login(client, "admin", "admin123")
    created = _create_school(client, token, name="Other School")
    assert created.status_code == 200
    assert created.json()["schoolId"] != SCHOOL_ID

    gtoken = login(client, "gate", "gate123")
    gates = client.get("/v1/gates", headers=auth(gtoken))
    assert {g["name"] for g in gates.json()["data"]} == {
        "Main Gate",
        "Pedestrian Gate",
        "Staff Gate",
        "Bus Bay",
    }
    priya = client.get("/v1/passes/P-4F21", headers=auth(gtoken))
    assert priya.status_code == 200
    assert priya.json()["visitorName"] == "Priya Sharma"
    students = client.get("/v1/students?q=Aarav", headers=auth(token))
    assert students.status_code == 200
    names = {s["name"] for s in students.json()["data"]}
    assert "Aarav Mehta" in names
    assert store.get_student("STU-AARAV")["schoolId"] == SCHOOL_ID
    assert store.get_student("STU-KABIR")["legalHold"] is True
    hours = client.get("/v1/access-rules/hours", headers=auth(token))
    sat = next(h for h in hours.json()["data"] if h["weekday"] == "sat")
    assert sat["closed"] is False
    assert sat["openTime"] == "08:00"
    holidays = client.get("/v1/access-rules/holidays", headers=auth(token))
    assert any(h["id"] == "HOL-DIWALI" for h in holidays.json()["data"])


def test_gate_and_host_forbidden_create_and_import(client):
    gtoken = login(client, "gate", "gate123")
    htoken = login(client, "host", "host123")
    for tok in (gtoken, htoken):
        create = _create_school(client, tok)
        assert create.status_code == 403
        assert create.json()["error"]["code"] == "FORBIDDEN"
        imp = client.post(
            "/v1/schools/me/roster/import",
            headers=auth(tok),
            files={"students": ("students.csv", _csv_bytes(STUDENT_HEADERS, []), "text/csv")},
        )
        assert imp.status_code == 403


def test_csv_happy_path_and_upsert(client):
    school, token = _new_school_admin(client)
    sid = school["schoolId"]
    students = [
        {
            "studentId": "4A-01",
            "name": "Diya Shah",
            "class": "4",
            "section": "A",
            "active": "true",
            "legalHold": "false",
        },
        {
            "studentId": "4A-02",
            "name": "Arnav Iyer",
            "class": "4",
            "section": "A",
            "active": "1",
            "legalHold": "0",
        },
    ]
    pickup = [
        {
            "studentId": "4A-01",
            "personName": "Kavita Shah",
            "relation": "parent",
            "mobile": "9822013001",
            "idType": "DL",
            "idLast4": "3001",
            "active": "true",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-06-01T10:00:00+05:30",
        },
        {
            "studentId": "4A-01",
            "personName": "Amit Shah",
            "relation": "guardian",
            "mobile": "+91 9822013002",
            "idType": "Aadhaar",
            "idNumber": "123456789012",
            "active": "yes",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-06-01T10:00:00+05:30",
        },
    ]
    r = client.post(
        "/v1/schools/me/roster/import",
        headers=auth(token),
        files={
            "students": ("students.csv", _csv_bytes(STUDENT_HEADERS, students), "text/csv"),
            "pickup": ("pickup.csv", _csv_bytes(PICKUP_HEADERS, pickup), "text/csv"),
        },
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["created"] == 4
    assert body["updated"] == 0
    assert body["errors"] == []
    assert body["students"]["created"] == 2
    assert body["pickup"]["created"] == 2

    listed = client.get("/v1/students", headers=auth(token))
    roster_ids = {s["studentId"] for s in listed.json()["data"]}
    assert roster_ids == {"4A-01", "4A-02"}
    assert all(s["schoolId"] == sid for s in listed.json()["data"])
    diya = next(s for s in listed.json()["data"] if s["studentId"] == "4A-01")
    people = client.get(f"/v1/students/{diya['id']}/authorized-pickup", headers=auth(token))
    mobiles = {p["mobile"] for p in people.json()["data"]}
    assert mobiles == {"9822013001", "9822013002"}

    students[0]["name"] = "Diya Shah Updated"
    pickup[0]["personName"] = "Kavita Shah Updated"
    again = client.post(
        "/v1/schools/me/roster/import",
        headers=auth(token),
        files={
            "students": ("students.csv", _csv_bytes(STUDENT_HEADERS, students), "text/csv"),
            "pickup": ("pickup.csv", _csv_bytes(PICKUP_HEADERS, pickup), "text/csv"),
        },
    )
    assert again.status_code == 200, again.text
    assert again.json()["updated"] == 4
    assert again.json()["created"] == 0
    listed2 = client.get("/v1/students", headers=auth(token))
    diya2 = next(s for s in listed2.json()["data"] if s["studentId"] == "4A-01")
    assert diya2["name"] == "Diya Shah Updated"
    people2 = client.get(f"/v1/students/{diya2['id']}/authorized-pickup", headers=auth(token))
    kavita = next(p for p in people2.json()["data"] if p["mobile"] == "9822013001")
    assert kavita["name"] == "Kavita Shah Updated"

    demo = client.get("/v1/students?q=Aarav", headers=auth(login(client, "admin", "admin123")))
    assert any(s["id"] == "STU-AARAV" for s in demo.json()["data"])


def test_csv_validation_errors(client):
    school, token = _new_school_admin(client)
    students = [
        {"studentId": "9C-01", "name": "Ok Student", "class": "9", "section": "C", "active": "true"},
        {"studentId": "", "name": "No Id", "class": "9", "section": "C"},
        {
            "schoolId": RESERVED_SCHOOL_ID,
            "studentId": "9C-02",
            "name": "Wrong School",
            "class": "9",
            "section": "C",
        },
        {"studentId": "9C-03", "name": "Bad Active", "class": "9", "section": "C", "active": "maybe"},
    ]
    pickup = [
        {
            "studentId": "9C-01",
            "personName": "Parent Ok",
            "relation": "parent",
            "mobile": "9822014001",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-06-01T10:00:00+05:30",
        },
        {
            "studentId": "MISSING",
            "personName": "Ghost",
            "relation": "parent",
            "mobile": "9822014002",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-06-01T10:00:00+05:30",
        },
        {
            "studentId": "9C-01",
            "personName": "Bad Rel",
            "relation": "uncle",
            "mobile": "9822014003",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-06-01T10:00:00+05:30",
        },
        {
            "studentId": "9C-01",
            "personName": "Bad Mobile",
            "relation": "sibling",
            "mobile": "123",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-06-01T10:00:00+05:30",
        },
    ]
    r = client.post(
        "/v1/schools/me/roster/import",
        headers=auth(token),
        files={
            "students": ("students.csv", _csv_bytes(STUDENT_HEADERS, students), "text/csv"),
            "pickup": ("pickup.csv", _csv_bytes(PICKUP_HEADERS, pickup), "text/csv"),
        },
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["students"]["created"] == 1
    assert body["pickup"]["created"] == 1
    student_rows = {e["row"] for e in body["students"]["errors"]}
    pickup_rows = {e["row"] for e in body["pickup"]["errors"]}
    assert 3 in student_rows
    assert 4 in student_rows
    assert 5 in student_rows
    assert 3 in pickup_rows
    assert 4 in pickup_rows
    assert 5 in pickup_rows
    assert any("JWT" in e["message"] for e in body["students"]["errors"])
    assert any("relation" in e["message"] for e in body["pickup"]["errors"])
    assert any("mobile" in e["message"].lower() for e in body["pickup"]["errors"])
    listed = client.get("/v1/students", headers=auth(token))
    assert {s["studentId"] for s in listed.json()["data"]} == {"9C-01"}
    assert listed.json()["data"][0]["schoolId"] == school["schoolId"]


def test_xlsx_and_split_import_endpoints(client):
    _school, token = _new_school_admin(client)
    xrows = [
        {
            "studentId": "2B-11",
            "name": "Excel Kid",
            "class": "2",
            "section": "B",
            "active": True,
            "legalHold": False,
        }
    ]
    r = client.post(
        "/v1/students/import",
        headers=auth(token),
        files={"file": ("students.xlsx", _xlsx_bytes(STUDENT_HEADERS, xrows), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
    )
    assert r.status_code == 200, r.text
    assert r.json()["created"] == 1
    pickup_rows = [
        {
            "studentId": "2B-11",
            "personName": "Excel Parent",
            "relation": "parent",
            "mobile": "9822015001",
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": "2026-07-01T09:00:00+05:30",
        }
    ]
    p = client.post(
        "/v1/students/authorized-pickup/import",
        headers=auth(token),
        files={"file": ("pickup.csv", _csv_bytes(PICKUP_HEADERS, pickup_rows), "text/csv")},
    )
    assert p.status_code == 200, p.text
    assert p.json()["pickup"]["created"] == 1
    listed = client.get("/v1/students?q=Excel", headers=auth(token))
    assert listed.json()["data"][0]["name"] == "Excel Kid"


def test_security_head_can_create_and_import(client):
    sh = login(client, "security", "sh123")
    r = _create_school(client, sh, name="SH Created School", slug="sh-created")
    assert r.status_code == 200, r.text
    ntoken = login(
        client,
        r.json()["credentials"]["security_head"]["username"],
        r.json()["credentials"]["security_head"]["password"],
    )
    students = [
        {"studentId": "1A-01", "name": "SH Import", "class": "1", "section": "A", "active": "true"}
    ]
    imp = client.post(
        "/v1/students/import",
        headers=auth(ntoken),
        files={"file": ("students.csv", _csv_bytes(STUDENT_HEADERS, students), "text/csv")},
    )
    assert imp.status_code == 200, imp.text
    assert imp.json()["created"] == 1
