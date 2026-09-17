"""Pickup-gate Pranay defaults: living API, Asha/Rohan Shah, isolated demo."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = (ROOT / "pickup-gate" / "config.js").read_text(encoding="utf-8")
FIXTURES = (ROOT / "pickup-gate" / "fixtures.js").read_text(encoding="utf-8")
INDEX = (ROOT / "pickup-gate" / "index.html").read_text(encoding="utf-8")
README = (ROOT / "pickup-gate" / "README.md").read_text(encoding="utf-8")
KIOSK_API = (
    ROOT / "kiosk/app/src/main/java/com/satcop/smartvisitor/kiosk/data/api/ApiConfig.kt"
).read_text(encoding="utf-8")
PICKUP_STORY = (
    ROOT / "kiosk/app/src/main/java/com/satcop/smartvisitor/kiosk/data/fixture/PickupStory.kt"
).read_text(encoding="utf-8")

NEW_API = "https://replacing-spyware-yes-due.trycloudflare.com/v1"


def test_living_api_and_pranay_login():
    assert NEW_API in CONFIG
    assert NEW_API in KIOSK_API
    assert "pensions-usb-loops-direction" not in CONFIG
    assert "weed-pumps-laura-upc" not in CONFIG
    assert 'gateUser: "pranay.gate"' in CONFIG
    assert 'gatePass: "PranayGate@2026"' in CONFIG
    assert "pranay123" not in CONFIG.split("not pranay123")[-1]
    assert "SCH-PRANAY-01" in CONFIG
    assert "PRANAY" in CONFIG
    assert "Pranay School Pune" in CONFIG


def test_pranay_students_not_aarav_as_default_story():
    assert "Asha Patil" in FIXTURES
    assert "PS-S-001" in FIXTURES
    assert "Rohan Shah" in FIXTURES
    assert "PS-S-002" in FIXTURES
    assert "Ramesh Patil" in FIXTURES
    assert "Smita Patil" in FIXTURES
    assert "Asha Patil" in INDEX
    assert "python3 -m http.server 8769" in README
    assert "PranayGate@2026" in README
    assert "Aarav Mehta" in FIXTURES
    assert "SCH-DEMO-01" in FIXTURES


def test_visitor_kiosk_login_unchanged():
    assert 'GATE_USERNAME = "gate"' in KIOSK_API
    assert 'GATE_PASSWORD = "gate123"' in KIOSK_API
    assert 'GATE_PASS_PRANAY = "PranayGate@2026"' in PICKUP_STORY
    assert 'STUDENT_ASHA = "Asha Patil"' in PICKUP_STORY
