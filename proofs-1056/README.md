# 1056 AC-APP1 — Material3 NavigationBar (device)

**versionCode:** 1056 · `1.0.8-1role-m3-nav`  
**SoT IA:** `/workspace/ux-mocks/phone-apple-bottomnav-2026-09-20/IMPLEMENT-SOT-FOR-MOBILE-1055.md`  
**Product drive:** `/workspace/product-mobile-1056-AC-APP1-device-NavBar-DRIVE-2026-09-20.md`

## Wiring fixes (root cause of 1055 device FAIL)
1. **Gate:** `GateTodayScreen` now mounts whenever `screen == HOME` (removed `step == 1` gate). Registration step>1 stays **under** pinned NavigationBar.
2. **Guard:** `GuardTodayShell` no longer nested under `Phase2Banner`+`fillMaxSize` Column (that **clipped** the bottom bar off-screen). ACTIVE/RESULT stay inside shell Patrol tab.
3. **Tab bar:** custom emoji `AppleTabBar` → real **Material3 `NavigationBar` + `NavigationBarItem`** (height 60dp, 20dp icons, 10sp labels).
4. **closeGuardTool** returns `HOME` (Gate no longer lands on old webpage via `GUARD_PATROL`).

## AC-PAR2 / device PNGs — BLOCKER
- **No AVD / emulator / physical device** on this box (`avdmanager list avd` empty).
- HTML twin proofs **banned** (1055 rejected).
- Compose Preview → PNG not available headless here (no Paparazzi / screenshot test harness).
- **DEX proof:** release APK embeds `NavigationBar` / `NavigationBarItem` + versionName `1.0.8-1role-m3-nav`.
- Device PNG vs SoT boards = **pending Viren/QA on-device install** of this APK.

## Tabs (unchanged IA)
| Role | Tabs |
|------|------|
| Gate | Home · Inside · Log · More |
| Host | Inbox · Done · Inside · More |
| Guard | Today · Patrol · Desk · More |
