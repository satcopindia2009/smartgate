# Satcop Smart Visitor — Gate kiosk (Wave 4)

Android gate / reception kiosk. Phone-friendly `fullUser` orientation (portrait on phones, landscape still works on tablets). **Login first** (typed username + password). JWT `user.role` picks the home: **gate** keeps registration steps 1–4; **host** opens host-web only; other roles get a stay-put message + Log out.

On compact width (`< 600.dp`, phone portrait) the shell uses 16.dp padding, a wrap-content card under the header (no empty 40% band), stacked title + full-width Prefill, 2-up type cards, and the school/queue/recent strip below the cards. Tablet/landscape keeps the two-column 280.dp context strip. Header **Demo** overflow opens after-hours / host / visitor QR / escort seed URLs in the external browser.

**Not for live school deploy.** DEMO watermark stays on until Viren says go.

## Waves

| Step | What |
|------|------|
| 1 | Visitor type cards (Parent / Vendor / Guest / Official / Alumni) + gate pill + peak strip |
| 2 | Name, +91 mobile, purpose, staff picker, optional vehicle / accompanying / notes |
| 3 | Live photo (camera, placeholder if no camera), ID type + number **or** ID image (V1), optional signature. `POST /v1/media/upload`, `POST /v1/blacklist/match`, `POST /v1/visits` |
| 4 | Outcome: pending → wait / Refresh; fixtures **Demo host approve** issues `passId` + `qrToken`; approved → SATCOP PASS box + **Check in**; inside → **Check out**. `POST /v1/passes/scan` `{passId\|token, action: check_in\|check_out, gateId?}`. **Load P-4F21 story**. |

Blacklist **Block** stops the pass; **Alert** shows an escalate banner. Header: signed-in JWT `displayName` + `schoolId`, DEMO watermark, **LIVE** (cyan) / **FIXTURES** (amber) pill, **Log out**.

Demo story: **Priya Sharma** → **Anita Joshi** (`H03`) → **Main Gate** (`G-MAIN`) / **P-4F21**. Prefill sample on step 1. Step 3 has **Block sample** (Vikram More `9876500001`) and **Alert sample** (Neha Salunkhe `9876500002`).

## Live mock + fallback

Default base: `https://replacing-spyware-yes-due.trycloudflare.com/v1`  
Typed login → `POST /v1/auth/login` → Bearer JWT on later calls. **No** hardcoded `gate`/`gate123` auto-login.

Gate seed: `pranay.gate` / `PranayGate@2026` → `SCH-PRANAY-01` / role `gate` / `PS-G01` / "Pranay Gate" → visitor types + demo hub.  
Host seed: `pranay.host` / `PranayHost@2026` → `SCH-PRANAY-01` / role `host` → **Host approve** (no visitor-type stepper / Prefill). Primary button opens `https://england-content-resulting-heavily.trycloudflare.com` in the external browser. Optional Pending / After-hours use `#pending` and `#afterhours`.  
Admin / SH / escort: "This APK is for Gate or Host" + Log out.

Never use the retired `weed-pumps-laura-upc` host.

Amber **FIXTURES** pill = later 5xx/timeout after a successful login. Login itself is required first. Gate never auto-approves a live visit.

## Open in Android Studio

1. Install Android Studio + Android SDK 35.
2. **File → Open** this `kiosk/` folder (not the repo root).
3. Phone AVD in portrait, or tablet AVD landscape ~10" (1280×800).
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

## Out of scope

Host-approve web (`../host-web/`), visitor QR (`../visitor-qr/`), guard patrol, production/school deploy, real PII/Aadhaar, P2 pickup.
