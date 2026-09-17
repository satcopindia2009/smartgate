# Satcop Smart Visitor — Gate pickup (Priority P2)

Tablet / desktop-web gate surface. Wires OpenAPI pickup paths. Falls back to local fixtures if the living tunnel is down.

**Default GATE tenant is Pranay School Pune.** Demo Aarav/Kabir stays a separate `SCH-DEMO-01` fixture path.

**Not for live school deploy.** DEMO watermark stays on. Live / pilot HOLD.

Hub **P1–P6+H1 LOCKED**. Separate `PickupEvent`. Gate shows custody **flag + `gateInstruction` ≤280 only** (P4/H1). No court PDFs.

## How to show

```bash
cd pickup-gate
python3 -m http.server 8769
# http://127.0.0.1:8769/                  → Pranay (pranay.gate / PranayGate@2026)
# http://127.0.0.1:8769/?tenant=demo      → Demo International (gate / gate123)
```

**Confirmed living seed** (`https://replacing-spyware-yes-due.trycloudflare.com/v1`):

| | |
|---|---|
| Username / password | `pranay.gate` / `PranayGate@2026` (**NOT** `pranay123`) |
| schoolId | `SCH-PRANAY-01` |
| staffId | `PS-G01` |
| Students | **Asha Patil** `PS-S-001` · **Rohan Shah** `PS-S-002` |

Lookup **Asha Patil** (5-B / `PS-S-001`) → Ramesh Patil (parent) + Smita Patil (guardian). **Rohan Shah** (`PS-S-002`) is the second Pranay student. Amber **FIXTURES** if the tunnel flaps.

Kiosk (Android) has a **Pickup** mode entry that points here — visitor register is unchanged. See `../kiosk/README.md`.

Admin CRUD / history is a separate desk. Link only (not this UI): https://saver-recognised-daughter-revolutionary.trycloudflare.com

## Locked tenant

| Field | Value |
|-------|--------|
| schoolId | `SCH-PRANAY-01` |
| school_code | `PRANAY` |
| name | Pranay School Pune |
| Gate user | `pranay.gate` / `PranayGate@2026` · staffId `PS-G01` |
| Gate | `PS-G-MAIN` (Main Gate) |
| Students | Asha Patil `PS-S-001` · Rohan Shah `PS-S-002` |
| Demo (isolated) | `SCH-DEMO-01` — Aarav / Kabir — never overwrite |

## Mock URL + seed logins

Living API (Cloudflare; pensions-usb is **DEAD** — do not use it):

`https://replacing-spyware-yes-due.trycloudflare.com/v1`

Admin preview: `https://saver-recognised-daughter-revolutionary.trycloudflare.com`

| Username | Password | Role | Tenant |
|----------|----------|------|--------|
| `pranay.gate` | `PranayGate@2026` | gate | **Shipped living seed.** Pranay JWT · Asha + Rohan Shah |
| `pranay.sh` | `PranaySH@2026` | security_head | Pranay SH override |
| `pranay.admin` | `PranayAdmin@2026` | admin | Admin desk only — not this UI |
| `gate` | `gate123` | gate | **SCH-DEMO-01 only** (not a Pranay JWT) |
| `security` | `sh123` | security_head | Demo SH fallback |

`pranay.gate` / `pranay123` **401s** on this API. Do not use it.

**How to show:** `cd pickup-gate && python3 -m http.server 8769` → http://127.0.0.1:8769/ · auto-login `pranay.gate` / `PranayGate@2026`. Fixtures still run Asha / Rohan Shah when the tunnel flaps. Edit `config.js` or pass `?api=` if the URL changes.

Cyan **LIVE mock** when the tunnel answers as that login’s school; amber **FIXTURES** otherwise.

## Pranay click-path (must work on fixtures)

1. Open `8769` — chrome is **Pranay School Pune · SCH-PRANAY-01 · PRANAY**. Signed in as `pranay.gate`.
2. Purpose → **Student pickup** → Continue.
3. Lookup **Asha Patil** (5-B). Authorized: **Ramesh Patil (parent)** + **Smita Patil (guardian)**.
4. Select Ramesh **or** match mobile `9876500101` (last-4 ok) → live photo stub → consent → **Log release** → `Released` (**AC-D3** proof fields on the PickupEvent).
5. New pickup → Asha → claimed name **not** on list → `BlockedNotAuthorized` (**AC-D1**). Request override → SH reason required → `ReleasedWithOverride`.
6. **Rohan Shah** (3-A) is the second Pranay student (Kavita Shah, parent).
7. Custody (**AC-D2**): live Pranay seed has no `court_order` student. Fixtures include **Dev Joshi** (`restricted` + `gate_instruction` only — no PDF). Demo **Kabir Singh** `court_order` is **SCH-DEMO-01 only** (Switch → Demo gate, or `?tenant=demo`).

## AC coverage

| ID | Gate behaviour |
|----|----------------|
| **AC-D1** | Person not on Asha’s active in-date list cannot release. SH override + non-empty reason only (`pranay.sh` preferred). |
| **AC-D2** | Restricted / court_order + allow-list / blocked row → `BlockedCustody`. Banner = flag + `gate_instruction` ≤280. No court PDF. |
| **AC-D3** | Released event stores student, collector name/relation/mobile, match method, gate, timestamps, live photo ref, status. |

## Demo story (P6 — isolated)

- **Aarav Mehta · Class 5-B** — Neha Mehta (Mother), Rohan Mehta (Uncle). Custody `none`.
- **Kabir Singh** — `court_order`. Mother is **Neha Mehta**, not Priya Singh / not visitor Priya.
- Fixtures also include **Aarav Patel · 3-A** for F2 name collision.

Login `gate` / `gate123` (or `?tenant=demo`). Do not mix into Pranay.

## Screen map (UX 1–7 · gate)

| # | Screen | What |
|---|--------|------|
| 1 | Purpose | Student pickup card (visitor types disabled) |
| 2 | Lookup | Name/class typeahead · F2 class/section if demo “Aarav” collides |
| 3 | List + banner | Authorized people + P5 mobile/ID last-4 · quiet vs RESTRICTED / COURT ORDER |
| 4 | Parent / guardian release | Reason + live photo stub + EN consent · optional campus-enter stub |
| 5 | BlockedNotAuthorized | AC-D1 · not on list · SH override + reason only |
| 6 | BlockedCustody | AC-D2 · flag / allow-list / blocked row |
| 7 | Override pending | “Waiting for Security Head…” · SH approve requires reason |

## Out of scope

Visitor MVP (`../kiosk/` register, `../host-web/`, `../visitor-qr/`). Backend `app/` (PR #4 / #19). Admin list CRUD / CSV (PR #6 / #20). Court files. Face match. MSR sync. After-hours / escort / blast. Production SSO.
