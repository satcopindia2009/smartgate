# 1055 AC-PAR / AC-APP proofs — bottomnav shell

**SoT:** `/workspace/ux-mocks/phone-apple-bottomnav-2026-09-20/`
**Implement:** GateToday / HostInbox / GuardTodayShell match frozen HTML+PNG 1:1
**versionCode:** 1055 · `1.0.8-1role-bottomnav`

## Side-by-side (SoT LEFT | APK shell RIGHT)
- `PROOF-LIGHT-board-HOMES-sot-vs-apk.png`
- `PROOF-DARK-board-HOMES-sot-vs-apk.png`
- `PROOF-LIGHT-{gate,host,guard}-home-sot-vs-apk.png`
- `PROOF-DARK-{gate,host,guard}-home-sot-vs-apk.png`

APK shell frames = frozen `_html/{LIGHT|DARK}/*-home.html` (same IA Compose paints).
Pinned bottom tabs · hero CTA above fold · no long-scroll home.

## Fixed in 1055
- MainActivity / GuardPatrolActivity: portrait + stop hide(navigationBars)
- Gate tabs Home|Inside|Log|More · Host Inbox|Done|Inside|More · Guard Today|Patrol|Desk|More
- Compact hero CTA homes (not phone-theme-apple long scroll)
