# Satcop Smart Visitor — Gate pickup (Priority P2)

Tablet / desktop-web gate surface matching `demos/pickup/pickup-gate.html`. Wires OpenAPI **v0.3.0** pickup paths. Falls back to local P6 fixtures if the mock tunnel is down.

**Not for live school deploy.** DEMO watermark stays on. Live / pilot HOLD. Designs STOP — this UI follows the demo + PRD, not a Viren redesign.

Hub **P1–P6+H1 LOCKED**. Separate `PickupEvent` (not a Visit subtype). Gate shows custody **flag + `gateInstruction` ≤280 only** (P4/H1). No court PDFs.

## Open

```bash
cd pickup-gate
python3 -m http.server 8769
# http://127.0.0.1:8769/
```

Kiosk (Android) has a **Pickup** mode entry that points here — visitor register is unchanged. See `../kiosk/README.md`.

Admin CRUD / history is the Admin desk. This slice only stubs that link.

## Screen map (UX 1–7 · gate)

| # | Screen | What |
|---|--------|------|
| 1 | Purpose | Student pickup card (visitor types disabled) |
| 2 | Lookup | Name/class typeahead · F2 class/section if “Aarav” collides |
| 3 | List + banner | Authorized people + P5 mobile/ID last-4 · quiet vs COURT ORDER banner |
| 4 | Relative / parent release | Reason + live photo stub + EN consent · optional campus-enter stub |
| 5 | BlockedNotAuthorized | AC-D1 · not on list · SH override + reason only |
| 6 | BlockedCustody | AC-D2 · flag / allow-list / blocked row |
| 7 | Override pending | “Waiting for Security Head…” · SH approve requires reason |

## Demo story (P6)

- **Aarav Mehta · Class 5-B** — Neha Mehta (Mother), Rohan Mehta (Uncle). Custody `none`.
- **Kabir Singh** — `court_order` block path. Mother is **Neha Mehta**, not Priya Singh / not visitor Priya.
- Fixtures also include **Aarav Patel · 3-A** for F2 name collision.

**Live stub seed (PR #4)** may differ on Kabir: class **3-A**, mother **Sunita Singh**, blocked **Rajesh Singh**. Aarav → Neha / Rohan still matches P6. Gate smoke uses whoever the API returns; fixtures keep the locked 4-A / Neha story.

## Mock URL + seed logins

API base (ephemeral Cloudflare tunnel):

`https://weed-pumps-laura-upc.trycloudflare.com/v1`

| Username | Password | Role | Used for |
|----------|----------|------|----------|
| `gate` | `gate123` | gate | Run pickup (default) |
| `security` | `sh123` | security_head | Override approve (reason required) |
| `admin` | `admin123` | admin | Admin desk only — not this UI |
| `host` | `host123` | host | Visitor host-web — no pickup list edit |

Cyan **LIVE mock** when the tunnel answers; amber **FIXTURES** when it 502s / times out (~6s). Edit `config.js` if the tunnel URL changes.

## Live smoke (when tunnel is up)

Manual walk (not only the G1–G7 strip):

1. Purpose → Continue → search **Aarav** → **Aarav Mehta 5-B**.
2. Select **Neha Mehta** → photo stub + consent → **Log release** → `Released`.
3. New pickup → Aarav → claimed name **not** on list → `BlockedNotAuthorized`.
4. Lookup **Kabir** → COURT ORDER banner (`gateInstruction` only) → blocked collector → `BlockedCustody`.
5. Request override → SH reason required → `ReleasedWithOverride`.

Proof fields (student, collector, relation, mobile, match, gate, photo ref, timestamps, status, pickup id) stay on the PickupEvent for later Admin search.

## Out of scope

Visitor MVP (`../kiosk/`, `../host-web/`, `../visitor-qr/`). Backend `app/` (PR #4 owns the stub). Admin list CRUD / history UI. Court files. Face match. MSR sync. Production SSO.
