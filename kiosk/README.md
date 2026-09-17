# Satcop Smart Visitor — Gate / Host kiosk

Android gate / reception kiosk. Phone-friendly `fullUser` orientation (portrait on phones, landscape still works on tablets). **Login first** (typed username + password). JWT `user.role` picks the home: **gate** keeps registration steps 1–4 plus native student pickup; **host** stays on native Host Home (pending list, approve / reject, after-hours copy). Other roles get a stay-put message + Log out.

On compact width (`< 600.dp`, phone portrait) the shell uses 16.dp padding, a wrap-content card under the header (no empty 40% band), stacked title + full-width actions, 2-up type cards, and the school/queue/recent strip below the cards. Tablet/landscape keeps the two-column 280.dp context strip.

**No web opens.** The APK does not launch host-web, pickup web, Custom Tabs, or any https URL. Host JWT stays in the app.

**Not for live school deploy.** DEMO watermark stays on until Viren says go.

## Waves

| Step | What |
|------|------|
| 1 | Visitor type cards (Parent / Vendor / Guest / Official / Alumni) + gate pill + peak strip + **Student pickup** |
| 2 | Name, +91 mobile, purpose, staff picker, optional vehicle / accompanying / notes |
| 3 | Live photo (camera, placeholder if no camera), ID type + number **or** ID image (V1), optional signature. `POST /v1/media/upload`, `POST /v1/blacklist/match`, `POST /v1/visits` |
| 4 | Outcome: pending → wait / Refresh; fixtures **Demo host approve** issues `passId` + `qrToken`; approved → SATCOP PASS box + **Check in**; inside → **Check out**. `POST /v1/passes/scan` `{passId\|token, action: check_in\|check_out, gateId?}`. Story chip is **off** for `SCH-PRANAY-01`. |

Blacklist **Block** stops the pass; **Alert** shows an escalate banner. Header: signed-in JWT `displayName` + `schoolId`, DEMO watermark, **LIVE** (cyan) / **FIXTURES** (amber) pill, **Log out**. Gate header has **Pickup**.

## Native host home

`GET /v1/visits?status=pending` (JWT school + `hostId` from staffId). Photos: `GET {apiBase}/media/{livePhotoKey}` with Bearer. Approve `POST /v1/visits/{id}/approve`. Reject with reason. After approve the card leaves the list (refresh). Empty list stays empty — **no Priya fixtures** when `schoolId=SCH-PRANAY-01`. Optional `GET /v1/notifications` toasts `visit.pending` (WA/SMS hold). After-hours: Host Approve is a no-op / `AFTER_HOURS_SH_REQUIRED`. Copy: Admin or Security Head.

## Native student pickup

Gate can open pickup from the header or step 1. Live APIs: `/v1/students`, `/v1/students/{id}/authorized-pickup`, `/v1/pickups`. Pranay live students: Asha Patil 5-B (`STU-PS-S-001`, authorized Ramesh Patil), Rohan Shah 3-A. Fixtures only if not live / not Pranay.

## Live mock + fallback

Default base: `https://replacing-spyware-yes-due.trycloudflare.com/v1`  
Typed login → `POST /v1/auth/login` → Bearer JWT on later calls. **No** hardcoded `gate`/`gate123` auto-login.

Gate seed: `pranay.gate` / `PranayGate@2026` → `SCH-PRANAY-01` / role `gate` / `PS-G01` / "Pranay Gate" → visitor register + pickup.  
Host seed: `pranay.host` / `PranayHost@2026` → `SCH-PRANAY-01` / role `host` / `PS-H03` → native Host Home.  
Admin / SH / escort: "This APK is for Gate or Host" + Log out.

Priya Sharma / `P-4F21` demo chips stay off for `SCH-PRANAY-01`.

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

Standalone host-web / visitor-qr browsers, guard patrol, production/school deploy, real PII/Aadhaar, WA/SMS send.
