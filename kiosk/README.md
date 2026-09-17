# Satcop Smart Visitor — Gate kiosk (Wave 4)

Android tablet kiosk for gate / reception check-in. Locked-landscape shell + registration steps 1–4 against the live mock when reachable, fixtures otherwise.

**Not for live school deploy.** DEMO watermark stays on until Viren says go.

## Waves

| Step | What |
|------|------|
| 1 | Visitor type cards (Parent / Vendor / Guest / Official / Alumni) + gate pill + peak strip |
| 2 | Name, +91 mobile, purpose, staff picker, optional vehicle / accompanying / notes |
| 3 | Live photo (camera, placeholder if no camera), ID type + number **or** ID image (V1), optional signature. `POST /v1/media/upload`, `POST /v1/blacklist/match`, `POST /v1/visits` |
| 4 | Outcome: pending → wait / Refresh; fixtures **Demo host approve** issues `passId` + `qrToken`; approved → SATCOP PASS box + **Check in**; inside → **Check out**. `POST /v1/passes/scan` `{passId\|token, action: check_in\|check_out, gateId?}`. **Load P-4F21 story**. |

Blacklist **Block** stops the pass; **Alert** shows an escalate banner. Header: DEMO watermark, **LIVE mock** (cyan) / **FIXTURES** (amber) pill, gate role.

Demo story: **Priya Sharma** → **Anita Joshi** (`H03`) → **Main Gate** (`G-MAIN`) / **P-4F21**. Prefill sample on step 1. Step 3 has **Block sample** (Vikram More `9876500001`) and **Alert sample** (Neha Salunkhe `9876500002`).

## Live mock + fallback

Default base: `https://replacing-spyware-yes-due.trycloudflare.com/v1`  
Visitor gate login stays `gate` / `gate123` (see `ApiConfig.kt`). Pickup-gate web defaults to `pranay.gate` / `PranayGate@2026`.

Amber **FIXTURES** pill = tunnel down or later 5xx/timeout. The kiosk keeps working from `DemoFixtures` + `LocalVisitStore`. Do not block a demo on a dead tunnel. Gate never auto-approves a live visit.

## Open in Android Studio

1. Install Android Studio + Android SDK 35.
2. **File → Open** this `kiosk/` folder (not the repo root).
3. Tablet AVD, landscape, ~10" (1280×800) if available.
4. Run the `app` configuration (CAMERA + INTERNET permissions).

## Gradle

```bash
cd kiosk
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Point `local.properties` at your SDK (`sdk.dir=...`). Gitignored.

## Contract field names

Unchanged: `schoolId`, `hostId`, `gateId`, `livePhotoKey`, `idImageKey`, `idType`, `idNumber`, `passId`, `qrToken`, visit `status`. Scan body: `passId` or `token`, `action`, `gateId`.

## Pickup mode (P2 entry)

Header **Visitor | Pickup**, plus a **Student pickup** card on step 1. This is an entry point only — the wired Day-1 gate flow is `../pickup-gate/` (`python3 -m http.server 8769`). Visitor steps 1–4 are unchanged.

Pickup chrome / copy defaults to **Pranay School Pune** (`SCH-PRANAY-01`): Asha Patil 5-B · Ramesh Patil (parent) + Smita Patil (guardian); Rohan Shah. Login `pranay.gate` / `PranayGate@2026`. Isolated demo P6 (Aarav / Kabir) stays on `SCH-DEMO-01`. Mother / guardian is not Priya. Visitor steps 1–4 and `gate` / `gate123` are unchanged.

## Out of scope

Host-approve web (`../host-web/`), visitor QR (`../visitor-qr/`), guard patrol, production/school deploy, real PII/Aadhaar. Admin pickup CRUD/history is the Admin desk.
