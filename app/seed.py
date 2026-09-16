"""Seed Demo International School from admin-mvp-fixtures shapes."""
from __future__ import annotations

from app import store
from app.config import SCHOOL_ID, SCHOOL_TZ, WATERMARK
from app.util import gen_qr_token, now_iso


# Documented demo passwords (README + smoke tests)
DEMO_PASSWORDS = {
    "gate": "gate123",
    "host": "host123",
    "admin": "admin123",
    "security": "sh123",
}


def seed() -> None:
    store.reset()
    ts = now_iso()

    store.set_school(
        {
            "id": SCHOOL_ID,
            "name": "Demo International School",
            "timezone": SCHOOL_TZ,
            "overdueHoursDefault": 4,
            "config": {"hostNotifyChannels": ["in_app"]},
            # Demo/SCH-DEMO-01 only — new schools default OFF (AC-E3e)
            "emergencyBlastEnabled": True,
            "blastStaffLaneEnabled": False,
            "blastChannelsVisitor": ["sms"],
            "blastChannelsStaff": ["in_app", "push"],
            "blastConfigUpdatedByUserId": "U-ADMIN",
            "blastConfigUpdatedAt": "2026-09-16T09:00:00+05:30",
        }
    )

    gates = [
        ("G-MAIN", "Main Gate"),
        ("G-PED", "Pedestrian Gate"),
        ("G-STAFF", "Staff Gate"),
        ("G-BUS", "Bus Bay"),
    ]
    for gid, name in gates:
        store.put_gate(
            {"id": gid, "schoolId": SCHOOL_ID, "name": name, "active": True}
        )

    staff_rows = [
        ("H01", "Meera Kulkarni", "Principal", "9000000001", None),
        ("H02", "Rahul Deshpande", "Admin Officer", "9000000002", "U-HOST-H02"),
        ("H03", "Anita Joshi", "Primary Coordinator", "9000000003", "U-HOST"),
        ("H04", "Sanjay Patil", "Accounts", "9000000004", None),
        ("G01", "Gate — Ramesh", "Guard", "9000000010", "U-GATE"),
        # Escort staff (distinct from blacklist visitor Vikram More / BL-01)
        ("E01", "Vikram More", "Escort staff", "9000000015", None),
    ]
    for sid, name, role_title, mobile, user_id in staff_rows:
        store.put_staff(
            {
                "id": sid,
                "schoolId": SCHOOL_ID,
                "name": name,
                "roleTitle": role_title,
                "mobile": mobile,
                "userId": user_id,
                "active": True,
            }
        )

    users = [
        {
            "id": "U-GATE",
            "username": "gate",
            "password": DEMO_PASSWORDS["gate"],
            "schoolId": SCHOOL_ID,
            "role": "gate",
            "staffId": "G01",
            "gateIds": ["G-MAIN", "G-PED", "G-STAFF", "G-BUS"],
            "displayName": "Gate — Ramesh",
            "phone": "9000000010",
            "email": "gate@demo.school",
            "active": True,
        },
        {
            "id": "U-HOST",
            "username": "host",
            "password": DEMO_PASSWORDS["host"],
            "schoolId": SCHOOL_ID,
            "role": "host",
            "staffId": "H03",
            "gateIds": None,
            "displayName": "Anita Joshi",
            "phone": "9000000003",
            "email": "anita.joshi@demo.school",
            "active": True,
        },
        {
            "id": "U-HOST-H02",
            "username": "rahul",
            "password": DEMO_PASSWORDS["host"],
            "schoolId": SCHOOL_ID,
            "role": "host",
            "staffId": "H02",
            "gateIds": None,
            "displayName": "Rahul Deshpande",
            "phone": "9000000002",
            "email": "rahul@demo.school",
            "active": True,
        },
        {
            "id": "U-ADMIN",
            "username": "admin",
            "password": DEMO_PASSWORDS["admin"],
            "schoolId": SCHOOL_ID,
            "role": "admin",
            "staffId": "H02",
            "gateIds": None,
            "displayName": "Office Admin",
            "phone": "9000000099",
            "email": "admin@demo.school",
            "active": True,
        },
        {
            "id": "U-SH",
            "username": "security",
            "password": DEMO_PASSWORDS["security"],
            "schoolId": SCHOOL_ID,
            "role": "security_head",
            "staffId": None,
            "gateIds": None,
            "displayName": "Meera — Security Head",
            "phone": "9000000098",
            "email": "security@demo.school",
            "active": True,
        },
    ]
    for u in users:
        store.put_user(u)

    from app.escort import seed_school_defaults

    seed_school_defaults(SCHOOL_ID, updated_by="U-ADMIN")

    # Blacklist from fixtures
    store.put_blacklist(
        {
            "id": "BL-01",
            "schoolId": SCHOOL_ID,
            "name": "Vikram More",
            "mobile": "9876500001",
            "idType": "Aadhaar",
            "idNumber": "XXXX-XXXX-3321",
            "reason": "Prior incident — unauthorized campus entry",
            "severity": "Block",
            "active": True,
            "addedBy": "Security Head",
            "addedAt": "2026-08-12T11:20:00+05:30",
            "expiresOn": None,
            "notes": "Do not issue pass without SH override",
            "photoUrl": "/demo/photos/vikram-more.jpg",
        }
    )
    store.put_blacklist(
        {
            "id": "BL-02",
            "schoolId": SCHOOL_ID,
            "name": "Neha Salunkhe",
            "mobile": "9876500002",
            "idType": "DL",
            "idNumber": "MH12-XXXX-8890",
            "reason": "Repeat after-hours vendor; watch only",
            "severity": "Alert",
            "active": True,
            "addedBy": "Security Head",
            "addedAt": "2026-09-01T09:05:00+05:30",
            "expiresOn": "2026-12-31",
            "notes": "Alert banner + gate ack",
            "photoUrl": None,
        }
    )

    # Seed media stubs
    for key in (
        "media/live_photo/priya",
        "media/live_photo/arjun",
        "media/live_photo/neha",
        "media/live_photo/rao",
    ):
        store.put_media(
            {
                "key": key,
                "schoolId": SCHOOL_ID,
                "kind": "live_photo",
                "visitId": None,
                "contentType": "image/jpeg",
                "createdAt": ts,
                "createdByUserId": "U-GATE",
                "bytes": b"",
            }
        )

    # Walkthrough: Priya Sharma → Anita Joshi → Main Gate → P-4F21 (inside)
    priya_token = gen_qr_token()
    priya = {
        "id": "V-20260916-014",
        "schoolId": SCHOOL_ID,
        "visitorName": "Priya Sharma",
        "mobile": "9822011122",
        "visitorType": "Parent",
        "purpose": "PTM follow-up, Class 4B",
        "hostId": "H03",
        "livePhotoKey": "media/live_photo/priya",
        "idType": "Aadhaar",
        "idNumber": "XXXX1234",
        "idImageKey": None,
        "vehicleNumber": None,
        "accompanyingCount": 0,
        "notes": "",
        "signatureKey": None,
        "gateId": "G-MAIN",
        "registeredByUserId": "U-GATE",
        "status": "inside",
        "rejectReason": None,
        "decidedAt": "2026-09-16T14:08:00+05:30",
        "decidedByUserId": "U-HOST",
        "passId": "P-4F21",
        "qrToken": priya_token,
        "timeIn": "2026-09-16T14:10:00+05:30",
        "timeOut": None,
        "gateInId": "G-MAIN",
        "gateOutId": None,
        "checkoutType": None,
        "forceCheckoutReason": None,
        "forceCheckoutByUserId": None,
        "blacklistHit": False,
        "blacklistId": None,
        "blacklistOverrideByUserId": None,
        "meetingDoneAt": None,
        "createdAt": "2026-09-16T14:05:00+05:30",
        "updatedAt": "2026-09-16T14:10:00+05:30",
    }
    store.put_visit(priya)
    store.put_pass(
        {
            "passId": "P-4F21",
            "token": priya_token,
            "visitId": "V-20260916-014",
            "schoolId": SCHOOL_ID,
            "issuedAt": "2026-09-16T14:08:00+05:30",
            "expiresAt": None,
            "revoked": False,
        }
    )

    # Arjun Kale — overdue inside (Staff Gate)
    arjun_token = gen_qr_token()
    arjun = {
        "id": "V-20260916-021",
        "schoolId": SCHOOL_ID,
        "visitorName": "Arjun Kale",
        "mobile": "9881100456",
        "visitorType": "Vendor",
        "purpose": "Water-cooler AMC",
        "hostId": "H04",
        "livePhotoKey": "media/live_photo/arjun",
        "idType": "DL",
        "idNumber": "MH14XXXX",
        "idImageKey": None,
        "vehicleNumber": "MH12AB1234",
        "accompanyingCount": 1,
        "notes": "Overdue vs 4h default",
        "signatureKey": None,
        "gateId": "G-STAFF",
        "registeredByUserId": "U-GATE",
        "status": "inside",
        "rejectReason": None,
        "decidedAt": "2026-09-16T10:00:00+05:30",
        "decidedByUserId": "U-ADMIN",
        "passId": "P-9C08",
        "qrToken": arjun_token,
        "timeIn": "2026-09-16T10:02:00+05:30",
        "timeOut": None,
        "gateInId": "G-STAFF",
        "gateOutId": None,
        "checkoutType": None,
        "forceCheckoutReason": None,
        "forceCheckoutByUserId": None,
        "blacklistHit": False,
        "blacklistId": None,
        "blacklistOverrideByUserId": None,
        "meetingDoneAt": None,
        "createdAt": "2026-09-16T09:55:00+05:30",
        "updatedAt": "2026-09-16T10:02:00+05:30",
    }
    store.put_visit(arjun)
    store.put_pass(
        {
            "passId": "P-9C08",
            "token": arjun_token,
            "visitId": "V-20260916-021",
            "schoolId": SCHOOL_ID,
            "issuedAt": "2026-09-16T10:00:00+05:30",
            "expiresAt": None,
            "revoked": False,
        }
    )

    # Neha — blacklist alert inside
    neha_token = gen_qr_token()
    neha = {
        "id": "V-20260916-033",
        "schoolId": SCHOOL_ID,
        "visitorName": "Neha Salunkhe",
        "mobile": "9876500002",
        "visitorType": "Vendor",
        "purpose": "Stationery delivery",
        "hostId": "H02",
        "livePhotoKey": "media/live_photo/neha",
        "idType": "DL",
        "idNumber": "MH12-XXXX-8890",
        "idImageKey": None,
        "vehicleNumber": None,
        "accompanyingCount": 0,
        "notes": "",
        "signatureKey": None,
        "gateId": "G-PED",
        "registeredByUserId": "U-GATE",
        "status": "inside",
        "rejectReason": None,
        "decidedAt": "2026-09-16T16:18:00+05:30",
        "decidedByUserId": "U-HOST-H02",
        "passId": "P-2A77",
        "qrToken": neha_token,
        "timeIn": "2026-09-16T16:20:00+05:30",
        "timeOut": None,
        "gateInId": "G-PED",
        "gateOutId": None,
        "checkoutType": None,
        "forceCheckoutReason": None,
        "forceCheckoutByUserId": None,
        "blacklistHit": True,
        "blacklistId": "BL-02",
        "blacklistOverrideByUserId": None,
        "meetingDoneAt": None,
        "createdAt": "2026-09-16T16:15:00+05:30",
        "updatedAt": "2026-09-16T16:20:00+05:30",
    }
    store.put_visit(neha)
    store.put_pass(
        {
            "passId": "P-2A77",
            "token": neha_token,
            "visitId": "V-20260916-033",
            "schoolId": SCHOOL_ID,
            "issuedAt": "2026-09-16T16:18:00+05:30",
            "expiresAt": None,
            "revoked": False,
        }
    )

    # Completed: Capt. L. Rao
    store.put_visit(
        {
            "id": "V-20260916-008",
            "schoolId": SCHOOL_ID,
            "visitorName": "Capt. L. Rao",
            "mobile": "9000012345",
            "visitorType": "Official",
            "purpose": "Fire-safety inspection",
            "hostId": "H01",
            "livePhotoKey": "media/live_photo/rao",
            "idType": "Other",
            "idNumber": "OFF-001",
            "idImageKey": None,
            "vehicleNumber": None,
            "accompanyingCount": 0,
            "notes": "",
            "signatureKey": None,
            "gateId": "G-MAIN",
            "registeredByUserId": "U-GATE",
            "status": "completed",
            "rejectReason": None,
            "decidedAt": "2026-09-16T09:12:00+05:30",
            "decidedByUserId": "U-ADMIN",
            "passId": "P-1A01",
            "qrToken": gen_qr_token(),
            "timeIn": "2026-09-16T09:15:00+05:30",
            "timeOut": "2026-09-16T11:40:00+05:30",
            "gateInId": "G-MAIN",
            "gateOutId": "G-MAIN",
            "checkoutType": "normal",
            "forceCheckoutReason": None,
            "forceCheckoutByUserId": None,
            "blacklistHit": False,
            "blacklistId": None,
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": "2026-09-16T11:30:00+05:30",
            "createdAt": "2026-09-16T09:10:00+05:30",
            "updatedAt": "2026-09-16T11:40:00+05:30",
        }
    )

    # Rejected: Rohit Jain
    store.put_visit(
        {
            "id": "V-20260915-104",
            "schoolId": SCHOOL_ID,
            "visitorName": "Rohit Jain",
            "mobile": "9765432100",
            "visitorType": "Guest",
            "purpose": "Alumni talk scout",
            "hostId": "H01",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "XXXX9999",
            "idImageKey": None,
            "vehicleNumber": None,
            "accompanyingCount": 0,
            "notes": "Asked to reschedule",
            "signatureKey": None,
            "gateId": "G-MAIN",
            "registeredByUserId": "U-GATE",
            "status": "rejected",
            "rejectReason": "Host out of station",
            "decidedAt": "2026-09-15T13:22:00+05:30",
            "decidedByUserId": "U-ADMIN",
            "passId": None,
            "qrToken": None,
            "timeIn": None,
            "timeOut": None,
            "gateInId": None,
            "gateOutId": None,
            "checkoutType": "never",
            "forceCheckoutReason": None,
            "forceCheckoutByUserId": None,
            "blacklistHit": False,
            "blacklistId": None,
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "createdAt": "2026-09-15T13:15:00+05:30",
            "updatedAt": "2026-09-15T13:22:00+05:30",
        }
    )

    # Rejected blacklist block: Vikram More
    store.put_visit(
        {
            "id": "V-20260915-088",
            "schoolId": SCHOOL_ID,
            "visitorName": "Vikram More",
            "mobile": "9876500001",
            "visitorType": "Guest",
            "purpose": "Meet accounts",
            "hostId": "H04",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "XXXX-XXXX-3321",
            "idImageKey": None,
            "vehicleNumber": None,
            "accompanyingCount": 0,
            "notes": "Block hit on mobile",
            "signatureKey": None,
            "gateId": "G-MAIN",
            "registeredByUserId": "U-GATE",
            "status": "rejected",
            "rejectReason": "Blacklist Block — SH declined override",
            "decidedAt": "2026-09-15T10:05:00+05:30",
            "decidedByUserId": "U-SH",
            "passId": None,
            "qrToken": None,
            "timeIn": None,
            "timeOut": None,
            "gateInId": None,
            "gateOutId": None,
            "checkoutType": "never",
            "forceCheckoutReason": None,
            "forceCheckoutByUserId": None,
            "blacklistHit": True,
            "blacklistId": "BL-01",
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "createdAt": "2026-09-15T10:00:00+05:30",
            "updatedAt": "2026-09-15T10:05:00+05:30",
        }
    )

    # Force completed: Sneha Iyer
    store.put_visit(
        {
            "id": "V-20260914-052",
            "schoolId": SCHOOL_ID,
            "visitorName": "Sneha Iyer",
            "mobile": "9812345678",
            "visitorType": "Alumni",
            "purpose": "Transcript pickup",
            "hostId": "H02",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Passport",
            "idNumber": "Z1234567",
            "idImageKey": None,
            "vehicleNumber": None,
            "accompanyingCount": 0,
            "notes": "Force checkout: school closing bell",
            "signatureKey": None,
            "gateId": "G-PED",
            "registeredByUserId": "U-GATE",
            "status": "force_completed",
            "rejectReason": None,
            "decidedAt": "2026-09-14T16:01:00+05:30",
            "decidedByUserId": "U-HOST-H02",
            "passId": "P-3B11",
            "qrToken": gen_qr_token(),
            "timeIn": "2026-09-14T16:04:00+05:30",
            "timeOut": "2026-09-14T16:55:00+05:30",
            "gateInId": "G-PED",
            "gateOutId": "G-PED",
            "checkoutType": "force",
            "forceCheckoutReason": "school closing bell",
            "forceCheckoutByUserId": "U-ADMIN",
            "blacklistHit": False,
            "blacklistId": None,
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "createdAt": "2026-09-14T15:58:00+05:30",
            "updatedAt": "2026-09-14T16:55:00+05:30",
        }
    )

    # Approved, not yet inside — pass scan check-in walkthrough
    ready_token = gen_qr_token()
    store.put_visit(
        {
            "id": "V-20260916-041",
            "schoolId": SCHOOL_ID,
            "visitorName": "Kiran Desai",
            "mobile": "9811223344",
            "visitorType": "Guest",
            "purpose": "Approved — ready for Main Gate scan",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Voter",
            "idNumber": "MH/99/0000001",
            "idImageKey": None,
            "vehicleNumber": None,
            "accompanyingCount": 0,
            "notes": "",
            "signatureKey": None,
            "gateId": "G-MAIN",
            "registeredByUserId": "U-GATE",
            "status": "approved",
            "rejectReason": None,
            "decidedAt": "2026-09-16T14:40:00+05:30",
            "decidedByUserId": "U-HOST",
            "passId": "P-C101",
            "qrToken": ready_token,
            "timeIn": None,
            "timeOut": None,
            "gateInId": None,
            "gateOutId": None,
            "checkoutType": None,
            "forceCheckoutReason": None,
            "forceCheckoutByUserId": None,
            "blacklistHit": False,
            "blacklistId": None,
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "createdAt": "2026-09-16T14:35:00+05:30",
            "updatedAt": "2026-09-16T14:40:00+05:30",
        }
    )
    store.put_pass(
        {
            "passId": "P-C101",
            "token": ready_token,
            "visitId": "V-20260916-041",
            "schoolId": SCHOOL_ID,
            "issuedAt": "2026-09-16T14:40:00+05:30",
            "expiresAt": None,
            "revoked": False,
        }
    )

    # Pending visit for host approve walkthrough (extra)
    store.put_visit(
        {
            "id": "V-20260916-040",
            "schoolId": SCHOOL_ID,
            "visitorName": "Demo Pending Parent",
            "mobile": "9811111111",
            "visitorType": "Parent",
            "purpose": "Meet class teacher",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "111122223333",
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
            "blacklistHit": False,
            "blacklistId": None,
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "createdAt": ts,
            "updatedAt": ts,
        }
    )

    store.add_outbox(
        {
            "schoolId": SCHOOL_ID,
            "event": "visit.pending",
            "visitId": "V-20260916-040",
            "payload": {
                "hostPhone": "9000000003",
                "visitorName": "Demo Pending Parent",
                "purpose": "Meet class teacher",
            },
            "channelHints": ["in_app"],
            "status": "pending",
            "createdAt": ts,
        }
    )

    _seed_after_hours(ts)
    _seed_pickup(ts)
    _seed_emergency_blast(ts)


def _seed_after_hours(ts: str) -> None:
    """A1–A2 + P6-style after-hours demos — separate from Priya MVP walkthrough."""
    from app.config import CAMPUS_TZ

    week = [
        ("mon", "08:00", "18:00", False, False),
        ("tue", "08:00", "18:00", False, False),
        ("wed", "08:00", "18:00", False, False),
        ("thu", "08:00", "18:00", False, False),
        ("fri", "08:00", "18:00", False, False),
        ("sat", "08:00", "13:00", False, False),
        ("sun", None, None, True, False),
    ]
    for weekday, open_t, close_t, closed, overnight in week:
        store.put_hours_row(
            {
                "schoolId": SCHOOL_ID,
                "timezone": CAMPUS_TZ,
                "weekday": weekday,
                "openTime": open_t,
                "closeTime": close_t,
                "closed": closed,
                "overnight": overnight,
                "updatedByUserId": "U-ADMIN",
                "updatedAt": "2026-09-01T09:00:00+05:30",
            }
        )

    store.put_holiday(
        {
            "id": "HOL-DIWALI",
            "schoolId": SCHOOL_ID,
            "date": "2026-10-20",
            "label": "Diwali",
            "createdByUserId": "U-ADMIN",
            "updatedByUserId": "U-ADMIN",
            "createdAt": "2026-09-01T09:00:00+05:30",
            "updatedAt": "2026-09-01T09:00:00+05:30",
        }
    )

    # Evening Vendor — pending SH (outside hours). Not Priya.
    store.put_visit(
        {
            "id": "V-AH-VENDOR",
            "schoolId": SCHOOL_ID,
            "visitorName": "Ravi Deshmukh",
            "mobile": "9822098801",
            "visitorType": "Vendor",
            "purpose": "After-hours AC repair — Main Gate",
            "hostId": "H03",
            "livePhotoKey": "media/live_photo/arjun",
            "idType": "DL",
            "idNumber": "MH12AH8801",
            "idImageKey": None,
            "vehicleNumber": "MH12AH8801",
            "accompanyingCount": 0,
            "notes": "After-hours Vendor demo — SH approve required",
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
            "blacklistHit": False,
            "blacklistId": None,
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "afterHours": True,
            "policyTrigger": "outside_hours",
            "afterHoursEvaluatedAt": "2026-09-16T19:30:00+05:30",
            "afterHoursApproveReason": None,
            "escortRequired": True,
            "allowedZones": ["reception", "admin"],
            "escortStaffId": None,
            "escortSuggestedByHost": None,
            "escortWaived": False,
            "escortWaiveReason": None,
            "escortClearedAt": None,
            "createdAt": "2026-09-16T19:30:00+05:30",
            "updatedAt": "2026-09-16T19:30:00+05:30",
        }
    )
    store.add_outbox(
        {
            "schoolId": SCHOOL_ID,
            "event": "visit.pending",
            "visitId": "V-AH-VENDOR",
            "payload": {
                "hostPhone": "9000000003",
                "visitorName": "Ravi Deshmukh",
                "purpose": "After-hours AC repair — Main Gate",
                "afterHours": True,
                "policyTrigger": "outside_hours",
                "hostFyi": True,
            },
            "channelHints": ["in_app", "security_head"],
            "status": "pending",
            "createdAt": "2026-09-16T19:30:00+05:30",
        }
    )

    # Holiday Parent Deepak Nair → Meera Kulkarni (H01) → P-7K88. Not Priya.
    deepak_token = gen_qr_token()
    store.put_visit(
        {
            "id": "V-AH-HOLIDAY",
            "schoolId": SCHOOL_ID,
            "visitorName": "Deepak Nair",
            "mobile": "9822098802",
            "visitorType": "Parent",
            "purpose": "Holiday walk-in — collect notebooks",
            "hostId": "H01",
            "livePhotoKey": "media/live_photo/priya",
            "idType": "Aadhaar",
            "idNumber": "888877776666",
            "idImageKey": None,
            "vehicleNumber": None,
            "accompanyingCount": 1,
            "notes": "Holiday Parent demo — SH approved; pass P-7K88",
            "signatureKey": None,
            "gateId": "G-MAIN",
            "registeredByUserId": "U-GATE",
            "status": "approved",
            "rejectReason": None,
            "decidedAt": "2026-10-20T10:40:00+05:30",
            "decidedByUserId": "U-SH",
            "passId": "P-7K88",
            "qrToken": deepak_token,
            "timeIn": None,
            "timeOut": None,
            "gateInId": None,
            "gateOutId": None,
            "checkoutType": None,
            "forceCheckoutReason": None,
            "forceCheckoutByUserId": None,
            "blacklistHit": False,
            "blacklistId": None,
            "blacklistOverrideByUserId": None,
            "meetingDoneAt": None,
            "afterHours": True,
            "policyTrigger": "holiday",
            "afterHoursEvaluatedAt": "2026-10-20T10:30:00+05:30",
            "afterHoursApproveReason": "Holiday walk-in verified by Security Head",
            "escortRequired": False,
            "allowedZones": ["reception"],
            "escortStaffId": None,
            "escortSuggestedByHost": None,
            "escortWaived": False,
            "escortWaiveReason": None,
            "escortClearedAt": None,
            "createdAt": "2026-10-20T10:30:00+05:30",
            "updatedAt": "2026-10-20T10:40:00+05:30",
        }
    )
    store.put_pass(
        {
            "passId": "P-7K88",
            "token": deepak_token,
            "visitId": "V-AH-HOLIDAY",
            "schoolId": SCHOOL_ID,
            "issuedAt": "2026-10-20T10:40:00+05:30",
            "expiresAt": None,
            "revoked": False,
        }
    )
    store.add_outbox(
        {
            "schoolId": SCHOOL_ID,
            "event": "visit.approved",
            "visitId": "V-AH-HOLIDAY",
            "payload": {
                "hostPhone": "9000000001",
                "visitorName": "Deepak Nair",
                "purpose": "Holiday walk-in — collect notebooks",
                "afterHours": True,
                "policyTrigger": "holiday",
                "hostFyi": True,
                "passId": "P-7K88",
            },
            "channelHints": ["in_app", "security_head"],
            "status": "pending",
            "createdAt": "2026-10-20T10:40:00+05:30",
        }
    )


def _seed_pickup(ts: str) -> None:
    """P6 demo fixtures — separate from Priya visitor walkthrough. No real Aadhaar."""
    from app.config import PICKUP_CONSENT_VERSION
    from app.pickup_match import denormalize_blocked_by_custody

    for key, kind in (
        ("media/collector_live_photo/neha-mehta", "collector_live_photo"),
        ("media/collector_live_photo/rohan-mehta", "collector_live_photo"),
        ("media/pickup_list_photo/neha-mehta", "pickup_list_photo"),
    ):
        store.put_media(
            {
                "key": key,
                "schoolId": SCHOOL_ID,
                "kind": kind,
                "visitId": None,
                "pickupId": None,
                "contentType": "image/jpeg",
                "createdAt": ts,
                "createdByUserId": "U-GATE",
                "bytes": b"",
                "retainUntilDays": 90 if kind == "collector_live_photo" else None,
            }
        )

    store.put_student(
        {
            "id": "STU-AARAV",
            "schoolId": SCHOOL_ID,
            "studentId": "5B-17",
            "name": "Aarav Mehta",
            "class": "5",
            "section": "B",
            "active": True,
            "enrollmentEndedAt": None,
            "legalHold": False,
            "createdAt": "2026-06-01T09:00:00+05:30",
            "updatedAt": "2026-06-01T09:00:00+05:30",
        }
    )
    store.put_student(
        {
            "id": "STU-KABIR",
            "schoolId": SCHOOL_ID,
            "studentId": "3A-09",
            "name": "Kabir Singh",
            "class": "3",
            "section": "A",
            "active": True,
            "enrollmentEndedAt": None,
            "legalHold": True,
            "createdAt": "2026-06-01T09:00:00+05:30",
            "updatedAt": "2026-09-10T11:00:00+05:30",
        }
    )

    consent_at = "2026-06-15T10:00:00+05:30"
    people = [
        {
            "id": "APP-NEHA",
            "schoolId": SCHOOL_ID,
            "studentId": "STU-AARAV",
            "name": "Neha Mehta",
            "relation": "parent",
            "mobile": "9822011001",
            "idType": "DL",
            "idNumber": None,
            "idLast4": "1001",
            "photoRef": "media/pickup_list_photo/neha-mehta",
            "active": True,
            "effectiveFrom": None,
            "effectiveTo": None,
            "blockedByCustody": False,
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": consent_at,
            "createdByUserId": "U-ADMIN",
            "updatedByUserId": "U-ADMIN",
            "createdAt": consent_at,
            "updatedAt": consent_at,
        },
        {
            "id": "APP-ROHAN",
            "schoolId": SCHOOL_ID,
            "studentId": "STU-AARAV",
            "name": "Rohan Mehta",
            "relation": "relative",
            "mobile": "9822011002",
            "idType": "Other",
            "idNumber": None,
            "idLast4": "2002",
            "photoRef": None,
            "active": True,
            "effectiveFrom": "2026-09-01",
            "effectiveTo": "2026-12-31",
            "blockedByCustody": False,
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": consent_at,
            "createdByUserId": "U-ADMIN",
            "updatedByUserId": "U-ADMIN",
            "createdAt": consent_at,
            "updatedAt": consent_at,
        },
        {
            "id": "APP-SUNITA",
            "schoolId": SCHOOL_ID,
            "studentId": "STU-KABIR",
            "name": "Sunita Singh",
            "relation": "parent",
            "mobile": "9822012001",
            "idType": "Voter",
            "idNumber": None,
            "idLast4": "3003",
            "photoRef": None,
            "active": True,
            "effectiveFrom": None,
            "effectiveTo": None,
            "blockedByCustody": False,
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": consent_at,
            "createdByUserId": "U-SH",
            "updatedByUserId": "U-SH",
            "createdAt": consent_at,
            "updatedAt": consent_at,
        },
        {
            "id": "APP-RAJESH",
            "schoolId": SCHOOL_ID,
            "studentId": "STU-KABIR",
            "name": "Rajesh Singh",
            "relation": "parent",
            "mobile": "9822012002",
            "idType": "DL",
            "idNumber": None,
            "idLast4": "4004",
            "photoRef": None,
            "active": True,
            "effectiveFrom": None,
            "effectiveTo": None,
            "blockedByCustody": True,
            "pickupConsentVersion": PICKUP_CONSENT_VERSION,
            "pickupConsentAt": consent_at,
            "createdByUserId": "U-SH",
            "updatedByUserId": "U-SH",
            "createdAt": consent_at,
            "updatedAt": "2026-09-10T11:00:00+05:30",
        },
    ]
    for person in people:
        store.put_authorized_person(person)

    store.put_custody_flag(
        {
            "studentId": "STU-AARAV",
            "schoolId": SCHOOL_ID,
            "flag": "none",
            "gateInstruction": "",
            "blockedPersonIds": [],
            "allowedPersonIds": None,
            "updatedByUserId": "U-ADMIN",
            "updatedAt": "2026-06-15T10:05:00+05:30",
        }
    )
    store.put_custody_flag(
        {
            "studentId": "STU-KABIR",
            "schoolId": SCHOOL_ID,
            "flag": "court_order",
            "gateInstruction": (
                "Release only to Sunita Singh (Mother). Block Rajesh Singh. "
                "Do not discuss case details at gate."
            ),
            "blockedPersonIds": ["APP-RAJESH"],
            "allowedPersonIds": ["APP-SUNITA"],
            "updatedByUserId": "U-SH",
            "updatedAt": "2026-09-10T11:00:00+05:30",
        }
    )
    denormalize_blocked_by_custody("STU-AARAV", SCHOOL_ID)
    denormalize_blocked_by_custody("STU-KABIR", SCHOOL_ID)


def _seed_blast_inside(visit: dict) -> None:
    """Blast-demo inside visitor — not part of the Priya gate walkthrough."""
    from app.util import gen_qr_token

    token = visit.pop("qrToken", None) or gen_qr_token()
    visit.setdefault("schoolId", SCHOOL_ID)
    visit.setdefault("livePhotoKey", "media/live_photo/priya")
    visit.setdefault("idImageKey", None)
    visit.setdefault("vehicleNumber", None)
    visit.setdefault("accompanyingCount", 0)
    visit.setdefault("notes", "Emergency blast demo — currently inside")
    visit.setdefault("signatureKey", None)
    visit.setdefault("registeredByUserId", "U-GATE")
    visit.setdefault("status", "inside")
    visit.setdefault("rejectReason", None)
    visit.setdefault("gateOutId", None)
    visit.setdefault("checkoutType", None)
    visit.setdefault("forceCheckoutReason", None)
    visit.setdefault("forceCheckoutByUserId", None)
    visit.setdefault("blacklistHit", False)
    visit.setdefault("blacklistId", None)
    visit.setdefault("blacklistOverrideByUserId", None)
    visit.setdefault("meetingDoneAt", None)
    visit["qrToken"] = token
    store.put_visit(visit)
    if visit.get("passId"):
        store.put_pass(
            {
                "passId": visit["passId"],
                "token": token,
                "visitId": visit["id"],
                "schoolId": SCHOOL_ID,
                "issuedAt": visit.get("decidedAt") or visit.get("timeIn"),
                "expiresAt": None,
                "revoked": False,
            }
        )


def _seed_emergency_blast(ts: str) -> None:
    """E3 demo — Meera SH on the live board, separate from Priya walkthrough.

    Existing inside (Priya / Arjun / Neha) plus three blast-only visitors so
    preview count is 6 and matches GET /v1/visits/inside. Seed blast B-20260916-03.
    """
    from app.inside import list_inside_visits
    from app.util import mask_mobile

    _seed_blast_inside(
        {
            "id": "V-BL-LEELA",
            "visitorName": "Leela Iyer",
            "mobile": "9822098809",
            "visitorType": "Parent",
            "purpose": "Evening notebook pickup — blast demo",
            "hostId": "H03",
            "idType": "Aadhaar",
            "idNumber": "880088008800",
            "gateId": "G-MAIN",
            "decidedAt": "2026-09-16T19:05:00+05:30",
            "decidedByUserId": "U-SH",
            "passId": "P-BL01",
            "timeIn": "2026-09-16T19:08:00+05:30",
            "timeOut": None,
            "gateInId": "G-MAIN",
            "afterHours": True,
            "policyTrigger": "outside_hours",
            "afterHoursEvaluatedAt": "2026-09-16T19:00:00+05:30",
            "createdAt": "2026-09-16T19:00:00+05:30",
            "updatedAt": "2026-09-16T19:08:00+05:30",
        }
    )
    _seed_blast_inside(
        {
            "id": "V-BL-FARHAN",
            "visitorName": "Farhan Qureshi",
            "mobile": "9822098810",
            "visitorType": "Guest",
            "purpose": "PTA volunteer — blast demo",
            "hostId": "H02",
            "idType": "Voter",
            "idNumber": "MH/88/0000888",
            "gateId": "G-PED",
            "decidedAt": "2026-09-16T11:20:00+05:30",
            "decidedByUserId": "U-HOST-H02",
            "passId": "P-BL02",
            "timeIn": "2026-09-16T11:25:00+05:30",
            "timeOut": None,
            "gateInId": "G-PED",
            "createdAt": "2026-09-16T11:15:00+05:30",
            "updatedAt": "2026-09-16T11:25:00+05:30",
        }
    )
    _seed_blast_inside(
        {
            "id": "V-BL-SONAL",
            "visitorName": "Sonal Banerjee",
            "mobile": "9822098811",
            "visitorType": "Official",
            "purpose": "Block education office — blast demo",
            "hostId": "H01",
            "idType": "Other",
            "idNumber": "EDU-8811",
            "gateId": "G-MAIN",
            "decidedAt": "2026-09-16T12:10:00+05:30",
            "decidedByUserId": "U-ADMIN",
            "passId": "P-BL03",
            "timeIn": "2026-09-16T12:15:00+05:30",
            "timeOut": None,
            "gateInId": "G-MAIN",
            "createdAt": "2026-09-16T12:05:00+05:30",
            "updatedAt": "2026-09-16T12:15:00+05:30",
        }
    )

    store.put_blast_template(
        {
            "id": "T-EVAC-01",
            "schoolId": SCHOOL_ID,
            "name": "Evacuation — assembly ground",
            "instruction": "Evacuate to the assembly ground. Follow staff. Do not use lifts.",
            "channel": "sms",
            "active": True,
            "updatedByUserId": "U-SH",
            "updatedAt": "2026-09-16T09:10:00+05:30",
            "createdAt": "2026-09-16T09:10:00+05:30",
        }
    )

    inside = list_inside_visits(SCHOOL_ID)
    blast_id = "B-20260916-03"
    triggered_at = "2026-09-16T16:45:00+05:30"
    instruction = "Evacuate to the assembly ground. Follow staff. Do not use lifts."
    store.put_blast(
        {
            "blastId": blast_id,
            "schoolId": SCHOOL_ID,
            "triggeredByUserId": "U-SH",
            "triggeredAt": triggered_at,
            "templateId": "T-EVAC-01",
            "instruction": instruction,
            "insideCount": len(inside),
            "recipientCount": len(inside),
            "status": "completed",
            "confirmAt": triggered_at,
            "createdAt": triggered_at,
            "updatedAt": triggered_at,
        }
    )
    refs = []
    for visit in inside:
        rid = f"BR-SEED-{visit['id']}"
        masked = mask_mobile(visit.get("mobile"))
        store.put_blast_recipient(
            {
                "id": rid,
                "blastId": blast_id,
                "schoolId": SCHOOL_ID,
                "visitId": visit["id"],
                "mobileMasked": masked,
                "channel": "sms",
                "status": "sent",
                "providerMessageId": f"mock-sms-seed-{visit['id'][-3:]}",
                "attemptedAt": triggered_at,
                "errorCode": None,
            }
        )
        refs.append(
            {
                "visitId": visit["id"],
                "mobileMasked": masked,
                "channel": "sms",
            }
        )
    store.add_outbox(
        {
            "schoolId": SCHOOL_ID,
            "event": "emergency.blast",
            "visitId": None,
            "blastId": blast_id,
            "payload": {
                "blastId": blast_id,
                "insideCount": len(inside),
                "instruction": instruction,
                "templateId": "T-EVAC-01",
                "schoolId": SCHOOL_ID,
                "triggeredByUserId": "U-SH",
                "channels": ["sms"],
                "recipientRefs": refs,
                "waHold": True,
            },
            "channelHints": ["sms"],
            "status": "pending",
            "createdAt": triggered_at,
        }
    )
