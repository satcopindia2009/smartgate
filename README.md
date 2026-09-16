# Satcop Smart Visitor — Mobile MVP (showable demo)

**Not for live school deploy.** DEMO watermark stays on until Viren says go. Live / pilot remain HOLD.

Designs STOP. Demos + API contract are the source of truth. Backend FastAPI under `app/` is untouched on this branch.

## Viren: click all three surfaces

Shared story: **Priya Sharma** (Parent, `+91 98220 11122`) → **Anita Joshi** (`H03`, Primary Coordinator) → **Main Gate** (`G-MAIN`) → pass **P-4F21** / visit `V-20260916-014`.

| # | Surface | Open |
|---|---------|------|
| 1 | **Gate kiosk** (Android tablet landscape) | Android Studio → **File → Open** `kiosk/` → Run `app` (CAMERA + INTERNET). Tablet AVD ~1280×800 if you have one. |
| 2 | **Host approve** (phone ~390) | `cd host-web && python3 -m http.server 8767` → http://127.0.0.1:8767/ |
| 3 | **Visitor QR** (read-only badge) | `cd visitor-qr && python3 -m http.server 8768` → http://127.0.0.1:8768/?passId=P-4F21 |
| 4 | **Gate pickup** (Priority P2 · PickupEvent) | `cd pickup-gate && python3 -m http.server 8769` → http://127.0.0.1:8769/ · kiosk header **Pickup** is an entry only |

Pills: cyan **LIVE mock** when the tunnel answers; amber **FIXTURES** when it does not. Demo still works offline.

P2 pickup is a separate surface from Priya’s visitor walk. Story: **Aarav Mehta 5-B** → **Neha Mehta (Mother)** / **Rohan Mehta (Uncle)**; **Kabir Singh** `court_order` block. See `pickup-gate/README.md`.

## Mock URL + seed logins

API base (ephemeral Cloudflare tunnel):

`https://weed-pumps-laura-upc.trycloudflare.com/v1`

School: **Demo International School** · `SCH-DEMO-01` · TZ `Asia/Calcutta`

| Username | Password | Role | Who |
|----------|----------|------|-----|
| `gate` | `gate123` | gate | Gate — Ramesh (all 4 gates) |
| `host` | `host123` | host | Anita Joshi (`H03`) |
| `admin` | `admin123` | admin | Office Admin (visitor + Admin desk; not needed for Priya walk) |
| `security` | `sh123` | security_head | Pickup override only (P2 gate) |

Blacklist samples on the kiosk: **Block** Vikram More `9876500001` · **Alert** Neha Salunkhe `9876500002`.

## 90-second walkthrough

1. **Kiosk** — Prefill sample → Continue → capture live photo (placeholder OK) + ID number → Submit. Status is `pending` (no QR until host). On FIXTURES tap **Demo host approve**. On LIVE leave it pending.
2. **Host** (`8767`) — Pending tab: Approve (or Reject with a reason). Priya (inside) tab: **Meeting done** for the seed `inside` visit.
3. **Kiosk step 4** — **Refresh** after live approve → SATCOP PASS box + **Check in** (`POST /v1/passes/scan` `check_in`) → **Check out**. Or **Load P-4F21 story**.
4. **Visitor QR** (`8768`) — Read-only P-4F21 badge; QR encodes opaque `qrToken`. On FIXTURES tap the status banner to cycle pending → approved → inside → completed.

## Build / tests (kiosk)

```bash
cd kiosk
./gradlew :app:assembleDebug :app:testDebugUnitTest
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Out of scope

Access / blast, guard patrol, production or school SSO, inventing Aadhaar / real PII. Admin pickup CRUD/history is the Admin desk (stub link only on the gate). Local FastAPI stub (`uvicorn app.main:app`) is the backend desk — Mobile does not change `app/` (pickup API is Backend PR #4).
