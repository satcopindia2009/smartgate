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
            "displayName": "Security Head",
            "phone": "9000000098",
            "email": "security@demo.school",
            "active": True,
        },
    ]
    for u in users:
        store.put_user(u)

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

    _seed_pickup(ts)


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
