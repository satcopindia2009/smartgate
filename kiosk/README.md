# Satcop Smart Visitor — Gate kiosk (Wave 1–2)

Android tablet kiosk for gate / reception check-in. Locked-landscape shell + registration steps 1–4 against the live mock when reachable, fixtures otherwise.

**Not for live school deploy.** DEMO watermark stays on until Viren says go.

## Waves

| Step | What |
|------|------|
| 1 | Visitor type cards (Parent / Vendor / Guest / Official / Alumni) + gate pill + peak strip |
| 2 | Name, +91 mobile, purpose, staff picker, optional vehicle / accompanying / notes |
| 3 | **Wave 2:** live photo (camera, placeholder if no camera), ID type + number **or** ID image (V1), optional signature. `POST /v1/media/upload`, `POST /v1/blacklist/match`, `POST /v1/visits` |
| 4 | Light outcome — pending / host notified. Blacklist **Block** stops the pass; **Alert** shows an escalate banner. QR scan is a later wave. |

Demo story: **Priya Sharma** → **Anita Joshi** (`H03`) → **Main Gate** (`G-MAIN`). Prefill sample on step 1. Step 3 has **Block sample** (Vikram More `9876500001`) and **Alert sample** (Neha Salunkhe `9876500002`).

## Live mock + fallback

Default base: `https://weed-pumps-laura-upc.trycloudflare.com/v1`  
Gate login: `gate` / `gate123` (see `ApiConfig.kt`).

The cyan **LIVE mock** / **FIXTURES** pill shows which source is active. The tunnel is ephemeral — on 502/timeout the kiosk keeps working from local fixtures (`DemoFixtures`, `LocalBlacklist`). Do not block a demo on a dead tunnel.

## Open in Android Studio

1. Install Android Studio + Android SDK 35.
2. **File → Open** this `kiosk/` folder (not the repo root).
3. Tablet AVD, landscape, ~10" (1280×800).
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

Unchanged: `schoolId`, `hostId`, `gateId`, `livePhotoKey`, `idImageKey`, `idType`, `idNumber`, `passId`, `qrToken`, visit `status`.

## Out of scope

Host-approve web (`../host-web/`), visitor QR (`../visitor-qr/`), guard patrol, production/school deploy, real PII/Aadhaar. Full QR scan check-in/out is Wave 4.
