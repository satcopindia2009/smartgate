# Satcop Smart Visitor — Gate kiosk (Wave 1)

Android tablet kiosk for gate / reception check-in. **Wave 1 only:** locked-landscape shell + registration steps 1–2 against local fixtures. Dark demo visual contract from `demos/gate-tablet.html` + `shared.css`.

**Not for live school deploy.** DEMO watermark stays on until Viren says go.

## What Wave 1 includes

1. Kotlin + Jetpack Compose project, tablet landscape (~1024–1280).
2. Dark tokens (charcoal/navy, purple/cyan accents) and large tap targets. English first.
3. **Step 1** — visitor type cards: Parent / Vendor / Guest / Official / Alumni + gate pill + peak-hour strip.
4. **Step 2** — name, mobile (+91), purpose, staff picker (`GET /v1/staff?active=true` shape), optional vehicle / accompanying / notes. Required fields validate before continue.
5. Fixture layer for `GET /v1/auth/me`, `GET /v1/staff?active=true`, `GET /v1/gates`. Contract names only (`schoolId`, `hostId`, `gateId`, `roleTitle`, …). No live API yet.
6. Demo story: **Priya Sharma** (Parent, +91 98220 11122) → host **Anita Joshi** (Primary Coordinator, `H03`) → **Main Gate** (`G-MAIN`). Use **Prefill sample**.
7. Locked landscape kiosk shell + DEMO watermark.

## Explicit non-goals (later waves)

- No camera, media upload, blacklist match, pass/QR scan.
- No host-approve web, visitor QR web, or guard patrol.
- No production/school deploy, no real PII/Aadhaar.

Step 3 is a hold screen so the 4-step shell is visible; it does **not** capture photo/ID.

## Open in Android Studio

1. Install Android Studio (Koala / Ladybug or newer) + Android SDK 35.
2. **File → Open** and select this `kiosk/` folder (not the repo root).
3. Wait for Gradle sync.
4. Use a **tablet** AVD, landscape, ~10" (e.g. Pixel Tablet or a 1280×800 / 1280×720 profile).
5. Run the `app` configuration.

The app locks to landscape (`sensor` not required — `landscape` in the manifest + activity).

## Gradle assemble

From this `kiosk/` directory:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Debug APK:

`app/build/outputs/apk/debug/app-demo-debug.apk`

(Debug builds use `applicationId` suffix `.demo` and version suffix `-DEMO`.)

If you are not using the wrapper yet:

```bash
# one-time, from kiosk/
gradle wrapper --gradle-version 8.9
```

Point `local.properties` at your SDK (`sdk.dir=...`). That file is gitignored.

## Fixtures

| Asset | Mirrors |
|-------|---------|
| `app/src/main/assets/fixtures/me.json` | `GET /v1/auth/me` |
| `app/src/main/assets/fixtures/staff.json` | `GET /v1/staff?active=true` |
| `app/src/main/assets/fixtures/gates.json` | `GET /v1/gates` |
| `app/src/main/assets/fixtures/inside.json` | recent check-ins / inside board subset |
| `app/src/main/assets/fixtures/demo-story.json` | Priya → Anita → Main Gate |

Kotlin source of truth: `DemoFixtures` + `FixtureDirectoryRepository`. Swap the repository for a live OpenAPI client in Wave 2+ without renaming fields.

Gate login fixture: `U-GATE` / role `gate` / `gateIds` = Main, Pedestrian, Staff, Bus Bay. Tap the cyan **gate pill** to switch among assigned gates.

## Stack

- Kotlin 2.0 · AGP 8.7 · Compose BOM 2024.10 · compileSdk 35 · minSdk 26
- kotlinx.serialization (contract JSON)
- No camera / network permissions in Wave 1

## Repo

This folder is a monorepo sibling of the FastAPI stub at the repository root (`app/`). Do not move or overwrite the backend.
