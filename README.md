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
| 4 | **Gate pickup** (Priority P2 · PickupEvent) | `cd pickup-gate && python3 -m http.server 8769` → http://127.0.0.1:8769/ · defaults to **Pranay** · kiosk header **Pickup** is an entry only |

**Theme (host-web + visitor-qr):** header Light / Dark toggle. Persists `localStorage` `satcop-theme` = `light` | `dark`. Unset → `prefers-color-scheme`. Same teal token pack as Admin. Product flows unchanged.

Pills: cyan **LIVE mock** when the tunnel answers; amber **FIXTURES** when it does not. Demo still works offline.

P2 pickup GATE default is **Pranay School Pune** (`SCH-PRANAY-01` / `PRANAY`): **Asha Patil 5-B** → **Ramesh Patil (parent)** / **Smita Patil (guardian)**; **Rohan Shah** on the same tenant. Login `pranay.gate` / `PranayGate@2026`. Isolated demo P6 (Aarav / Kabir) remains `SCH-DEMO-01` via `gate` / `gate123`. See `pickup-gate/README.md`.

## Mock URL + seed logins

Living API (Cloudflare; **pensions-usb is dead — do not use it**):

`https://replacing-spyware-yes-due.trycloudflare.com/v1`

Admin pickup preview: `https://saver-recognised-daughter-revolutionary.trycloudflare.com`

Visitor MVP school: **Demo International School** · `SCH-DEMO-01` · TZ `Asia/Calcutta`  
Pickup GATE default: **Pranay School Pune** · `SCH-PRANAY-01` · `PRANAY`

| Username | Password | Role | Who |
|----------|----------|------|-----|
| `pranay.gate` | `PranayGate@2026` | gate | Pranay pickup (default on `pickup-gate/`) |
| `pranay.sh` | `PranaySH@2026` | security_head | Pranay SH override (preferred) |
| `gate` | `gate123` | gate | Demo visitor kiosk + isolated Aarav/Kabir pickup |
| `host` | `host123` | host | Anita Joshi (`H03`) — visitor host-web |
| `admin` | `admin123` | admin | Office Admin (visitor + Admin desk; not needed for Priya walk) |
| `security` | `sh123` | security_head | Demo pickup override; Pranay fallback if `pranay.sh` seed is missing |

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
