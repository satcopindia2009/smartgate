# Satcop Smart Visitor — Gate kiosk (Wave 4)

Android gate / reception kiosk. Phone-friendly `fullUser` orientation (portrait on phones, landscape still works on tablets) + a **Demo hub** (step 0 / header overflow) that opens public web previews in the external browser, plus registration steps 1–4 against the live mock when reachable, fixtures otherwise.

**Not for live school deploy.** DEMO watermark stays on until Viren says go.

## Waves

| Step | What |
|------|------|
| 0 | **Demo hub** cards + header **Demo hub** overflow. In-app visitor gate, plus `ACTION_VIEW` previews: after-hours gate, host approve (`#afterhours`), visitor QR `P-7K88`, escort/zones. Tunnels ephemeral — 502 → FIXTURES / this app. |
| 1 | Visitor type cards (Parent / Vendor / Guest / Official / Alumni) + gate pill + peak strip |
| 2 | Name, +91 mobile, purpose, staff picker, optional vehicle / accompanying / notes |
| 3 | Live photo (camera, placeholder if no camera), ID type + number **or** ID image (V1), optional signature. `POST /v1/media/upload`, `POST /v1/blacklist/match`, `POST /v1/visits` |
| 4 | Outcome: pending → wait / Refresh; fixtures **Demo host approve** issues `passId` + `qrToken`; approved → SATCOP PASS box + **Check in**; inside → **Check out**. `POST /v1/passes/scan` `{passId\|token, action: check_in\|check_out, gateId?}`. **Load P-4F21 story**. |

Blacklist **Block** stops the pass; **Alert** shows an escalate banner. Header: DEMO watermark, **LIVE mock** (cyan) / **FIXTURES** (amber) pill, gate role.

Demo story: **Priya Sharma** → **Anita Joshi** (`H03`) → **Main Gate** (`G-MAIN`) / **P-4F21**. Prefill sample on step 1. Step 3 has **Block sample** (Vikram More `9876500001`) and **Alert sample** (Neha Salunkhe `9876500002`).

## Live mock + fallback

Default base: `https://pensions-usb-loops-direction.trycloudflare.com/v1`  
Gate login: `gate` / `gate123` (see `ApiConfig.kt`). Never use the retired `weed-pumps-laura-upc` host.

Amber **FIXTURES** pill = tunnel down or later 5xx/timeout. The kiosk keeps working from `DemoFixtures` + `LocalVisitStore`. Do not block a demo on a dead tunnel. Gate never auto-approves a live visit.

Public Demo hub previews (ephemeral Cloudflare tunnels; 502 → FIXTURES / in-app gate):

- After-hours gate: `https://simon-configure-hiking-cms.trycloudflare.com`
- Host approve (After-hours tab): `https://england-content-resulting-heavily.trycloudflare.com/#afterhours`
- Visitor QR P-7K88: `https://ethernet-prairie-carefully-furnishings.trycloudflare.com/?passId=P-7K88`
- Escort/zones: `https://votes-carlos-charter-damaged.trycloudflare.com`

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
