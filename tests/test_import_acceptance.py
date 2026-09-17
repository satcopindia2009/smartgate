"""AC-IMP-1..10 + F3/F6 from pickup-csv-import-design-2026-09-17."""
from __future__ import annotations

from tests.conftest import auth, login
from tests.test_pickup import _consent, _upload_collector_photo
from tests.test_school_roster import _csv_bytes, _person_row, _xlsx_bytes

from app import store
from app.config import PRANAY_SCHOOL_CODE, PRANAY_SCHOOL_ID, SCHOOL_ID
from app.pickup_match import is_list_eligible


def _pranay_row(**kwargs):
    row = _person_row(school_code=PRANAY_SCHOOL_CODE, **kwargs)
    return row


def test_ac_imp_1_creates_target_tenant_only(client):
    token = login(client, "pranay.admin", "pranay123")
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={"file": ("ok.csv", _csv_bytes([_pranay_row()]), "text/csv")},
    )
    assert r.status_code == 200, r.text
    assert r.json()["imported"] >= 1
    assert r.json()["failed"] == 0
    assert r.json()["schoolId"] == PRANAY_SCHOOL_ID
    listed = client.get("/v1/students", headers=auth(token))
    assert all(s["schoolId"] == PRANAY_SCHOOL_ID for s in listed.json()["data"])
    assert store.get_student("STU-AARAV")["schoolId"] == SCHOOL_ID


def test_ac_imp_2_per_row_errors_commit_valid(client):
    token = login(client, "pranay.admin", "pranay123")
    rows = [
        _pranay_row(student_external_id="IMP2-OK", student_name="Valid Kid"),
        _pranay_row(student_external_id="IMP2-BAD", person_name="X", relation="uncle"),
    ]
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={"file": ("mix.csv", _csv_bytes(rows), "text/csv")},
    )
    assert r.status_code == 200
    assert r.json()["imported"] >= 1
    assert r.json()["failed"] >= 1
    ids = {s["studentId"] for s in client.get("/v1/students", headers=auth(token)).json()["data"]}
    assert "IMP2-OK" in ids


def test_ac_imp_3_f6_blank_court_order_no_fail_open(client):
    sh = login(client, "pranay.sh", "pranay123")
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(sh),
        files={
            "file": (
                "f6.csv",
                _csv_bytes(
                    [
                        _pranay_row(
                            student_external_id="IMP3-F6",
                            student_name="F6 Kid",
                            custody_flag="court_order",
                            gate_instruction="",
                        )
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert any("F6" in e["message"] for e in r.json()["errors"])
    listed = client.get("/v1/students", headers=auth(sh)).json()["data"]
    kid = next((s for s in listed if s["studentId"] == "IMP3-F6"), None)
    if kid:
        flag = client.get(f"/v1/students/{kid['id']}/custody-flag", headers=auth(sh)).json()
        assert flag["flag"] != "court_order" or flag.get("gateInstruction")


def test_ac_imp_4_rejects_court_doc_columns(client):
    token = login(client, "pranay.admin", "pranay123")
    from tests.test_school_roster import LOCKED_HEADERS

    r = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={
            "file": (
                "court.csv",
                _csv_bytes([_pranay_row()], LOCKED_HEADERS + ["case_narrative"]),
                "text/csv",
            )
        },
    )
    assert r.status_code == 400
    assert "Court-document" in r.json()["error"]["message"]


def test_ac_imp_5_gate_pickup_match_imported_mobile(client):
    admin = login(client, "pranay.admin", "pranay123")
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(admin),
        files={
            "file": (
                "d1.csv",
                _csv_bytes(
                    [
                        _pranay_row(
                            student_external_id="IMP5-01",
                            student_name="Imported Child",
                            person_name="Imported Parent",
                            mobile="9822018111",
                            person_active="Y",
                        )
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert r.status_code == 200, r.text
    assert r.json()["failed"] == 0
    stu = next(
        s
        for s in client.get("/v1/students", headers=auth(admin)).json()["data"]
        if s["studentId"] == "IMP5-01"
    )
    gate = login(client, "pranay.gate", "pranay123")
    start = client.post(
        "/v1/pickups",
        headers=auth(gate),
        json={
            "studentId": stu["id"],
            "gateId": "PS-G-MAIN",
            "pickupReason": "early",
            "collectorMobile": "9822018111",
            "collectorName": "Imported Parent",
        },
    )
    assert start.status_code == 200, start.text
    body = start.json()
    assert body["status"] == "Matching"
    assert body["matchMethod"] == "mobile"
    assert body["linkedVisitId"] is None
    assert "visitorType" not in body
    pid = body["id"]
    _consent(client, gate, pid)
    key = _upload_collector_photo(client, gate, pid)
    rel = client.post(
        f"/v1/pickups/{pid}/release",
        headers=auth(gate),
        json={"collectorLivePhotoRef": key},
    )
    assert rel.status_code == 200, rel.text
    assert rel.json()["status"] == "Released"
    assert rel.json()["linkedVisitId"] is None
    assert store.get_visit(pid) is None


def test_ac_imp_6_host_forbidden(client):
    host = login(client, "host", "host123")
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(host),
        files={"file": ("x.csv", _csv_bytes([_person_row()]), "text/csv")},
    )
    assert r.status_code == 403


def test_ac_imp_7_audit_who_when_filename_counts(client):
    token = login(client, "pranay.admin", "pranay123")
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={"file": ("audit-file.csv", _csv_bytes([_pranay_row(student_external_id="IMP7")]), "text/csv")},
    )
    audit = r.json()["audit"]
    assert audit["importedByUserId"] == "U-PRANAY-ADMIN"
    assert audit["importedAt"]
    assert "audit-file.csv" in audit["filenames"]
    assert "created" in audit and "errorCount" in audit


def test_ac_imp_8_xlsx_first_sheet(client):
    token = login(client, "pranay.admin", "pranay123")
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={
            "file": (
                "sheet.xlsx",
                _xlsx_bytes([_pranay_row(student_external_id="IMP8", student_name="Xlsx Kid")]),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        },
    )
    assert r.status_code == 200, r.text
    names = {s["name"] for s in client.get("/v1/students?q=Xlsx", headers=auth(token)).json()["data"]}
    assert "Xlsx Kid" in names


def test_ac_imp_9_validate_writes_nothing(client):
    token = login(client, "pranay.admin", "pranay123")
    r = client.post(
        "/v1/students/import:validate",
        headers=auth(token),
        files={"file": ("dry.csv", _csv_bytes([_pranay_row(student_external_id="IMP9")]), "text/csv")},
    )
    assert r.status_code == 200
    assert r.json()["dryRun"] is True
    ids = {s["studentId"] for s in client.get("/v1/students", headers=auth(token)).json()["data"]}
    assert "IMP9" not in ids


def test_ac_imp_10_demo_seed_intact(client):
    token = login(client, "pranay.admin", "pranay123")
    client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={"file": ("p.csv", _csv_bytes([_pranay_row(student_external_id="IMP10")]), "text/csv")},
    )
    demo = login(client, "admin", "admin123")
    aarav = client.get("/v1/students/STU-AARAV", headers=auth(demo))
    assert aarav.json()["name"] == "Aarav Mehta"
    assert store.get_student("STU-KABIR")["legalHold"] is True
    people = client.get("/v1/students/STU-AARAV/authorized-pickup", headers=auth(demo))
    assert any(p["id"] == "APP-NEHA" for p in people.json()["data"])
    priya = client.get("/v1/visits/V-20260916-014", headers=auth(demo))
    assert priya.json()["visitorName"] == "Priya Sharma"


def test_ac_imp_f3_expired_effective_to_not_on_list(client):
    token = login(client, "pranay.admin", "pranay123")
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={
            "file": (
                "f3.csv",
                _csv_bytes(
                    [
                        _pranay_row(
                            student_external_id="IMP-F3",
                            student_name="F3 Kid",
                            person_name="Expired Relative",
                            mobile="9822018222",
                            effective_from="2020-01-01",
                            effective_to="2020-12-31",
                            person_active="Y",
                        )
                    ]
                ),
                "text/csv",
            )
        },
    )
    assert r.status_code == 200, r.text
    stu = next(
        s
        for s in client.get("/v1/students", headers=auth(token)).json()["data"]
        if s["studentId"] == "IMP-F3"
    )
    people = client.get(f"/v1/students/{stu['id']}/authorized-pickup", headers=auth(token))
    person = people.json()["data"][0]
    assert is_list_eligible(person) is False
    gate = login(client, "pranay.gate", "pranay123")
    start = client.post(
        "/v1/pickups",
        headers=auth(gate),
        json={
            "studentId": stu["id"],
            "gateId": "PS-G-MAIN",
            "pickupReason": "early",
            "collectorMobile": "9822018222",
        },
    )
    assert start.status_code == 200
    assert start.json()["matchMethod"] in (None, "none") or start.json()["collectorPickupPersonId"] is None


def test_ac_imp_custody_conflict_fails_student(client):
    sh = login(client, "pranay.sh", "pranay123")
    rows = [
        _pranay_row(
            student_external_id="IMP-CF",
            student_name="Conflict Kid",
            person_name="Mom",
            mobile="9822018333",
            custody_flag="restricted",
            gate_instruction="A",
        ),
        _pranay_row(
            student_external_id="IMP-CF",
            student_name="Conflict Kid",
            person_name="Dad",
            mobile="9822018334",
            custody_flag="court_order",
            gate_instruction="Release only to Mom",
        ),
    ]
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(sh),
        files={"file": ("cf.csv", _csv_bytes(rows), "text/csv")},
    )
    assert any("Conflicting custody" in e["message"] for e in r.json()["errors"])
    listed = client.get("/v1/students", headers=auth(sh)).json()["data"]
    kid = next(s for s in listed if s["studentId"] == "IMP-CF")
    flag = client.get(f"/v1/students/{kid['id']}/custody-flag", headers=auth(sh)).json()
    assert flag["flag"] == "none"


def test_ac_imp_template_download(client):
    from app.roster_import import LOCKED_HEADERS, TEMPLATE_CSV

    token = login(client, "pranay.admin", "pranay123")
    r = client.get("/v1/students/import/template", headers=auth(token))
    assert r.status_code == 200
    header = r.text.splitlines()[0]
    assert header == ",".join(LOCKED_HEADERS)
    assert r.text == TEMPLATE_CSV
    assert "studentId" not in header
    assert "personName" not in header
    assert "pickupConsentVersion" not in header
    host = login(client, "host", "host123")
    assert client.get("/v1/students/import/template", headers=auth(host)).status_code == 403


def test_header_lock_rejects_thinner_camelcase_template(client):
    token = login(client, "pranay.admin", "pranay123")
    thinner = [
        "schoolId",
        "studentId",
        "name",
        "class",
        "section",
        "personName",
        "relation",
        "mobile",
        "idLast4",
        "idType",
        "effectiveFrom",
        "effectiveTo",
        "custodyFlag",
        "gateInstruction",
        "allowedPersonMobiles",
        "blockedPersonMobiles",
        "pickupConsentVersion",
        "pickupConsentAt",
        "active",
        "legalHold",
    ]
    row = {
        "schoolId": PRANAY_SCHOOL_CODE,
        "studentId": "THIN-01",
        "name": "Thin Kid",
        "class": "1",
        "section": "A",
        "personName": "Thin Parent",
        "relation": "parent",
        "mobile": "9822017001",
        "idLast4": "7001",
        "idType": "DL",
        "effectiveFrom": "",
        "effectiveTo": "",
        "custodyFlag": "none",
        "gateInstruction": "",
        "allowedPersonMobiles": "",
        "blockedPersonMobiles": "",
        "pickupConsentVersion": "pickup_notice_en_hi_v1",
        "pickupConsentAt": "2026-06-01",
        "active": "Y",
        "legalHold": "N",
    }
    r = client.post(
        "/v1/students/import:commit",
        headers=auth(token),
        files={"file": ("thin.csv", _csv_bytes([row], thinner), "text/csv")},
    )
    assert r.status_code == 400, r.text
    msg = r.json()["error"]["message"]
    assert "Thinner" in msg or "student_external_id" in msg
    assert store.get_student_by_roster_id(PRANAY_SCHOOL_ID, "THIN-01") is None
    assert store.get_student("STU-AARAV")["schoolId"] == SCHOOL_ID
