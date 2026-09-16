> **Supersede (~22:52 IST):** Hub packages LOCKED. **P2-A5…A11 / G1–G8 demo Exists** via specialist-owned Access Rules and Emergency Blast HTML + named shots; those demos are not inside canonical Admin Live. Pickup Admin has **P2-A1…A3 / G9–G11 demo Exists**. **P2-A4 / G12** override decision UI still MISSING. READY FOR LIVE DEV still **NO**. See `admin-dashboard-predev-delta-stamp-2026-09-16.md`.

# ADMIN DASHBOARD — PRE-DEV COVERAGE (Hub lock MVP + Priority-P2)
Desk: [VMS] Admin Dashboard · 16 Sep 2026 ~22:52 IST
Sources: prd-admin-dashboard-mvp.md, product-coverage-checklist-hub-lock, demos/UX-HUB-LOCK-COVERAGE, demos/admin-whos-inside.html, prd-pickup-custody-phase2.md
No coding. Structured for Word doc v1.1.

## 1) MVP admin features × pains — done / gap

| Pain | Admin surface (PRD) | Spec | Demo HTML | Eng-ready? |
|------|---------------------|------|-----------|-----------|
| **A4** Multiple gates | Live board gate multi-select + Gates tab + Reports today/range-by-gate | DONE in admin PRD §1/§4 | DONE (Live filters, Gates, Reports) | YES for demo/stub |
| **B3** Banned visitors | Blacklist tab + match rules (mobile/ID hard; Block/Alert) | DONE §3 | PARTIAL — table + severity view; **no add/edit CRUD form** | Spec YES; UI gap on CRUD form |
| **C3** Meeting overruns | Live duration highlight + overdue filter + force checkout | DONE §1 + AC-L2 | DONE (Force modal + reason) | YES |
| **E1** Searchable history | History columns/filters + CSV scopes | DONE §2/§5 | DONE (History + CSV modal 4 scopes) | YES |
| **E2** Checkout (admin side) | Force checkout + history checkout type Normal/Force/Open | DONE | DONE | YES |
| **E3-list** Emergency roll | Live who's-inside = roll list | DONE §1 + AC-L1 | DONE | YES (list only) |
| **E4** Data sensitivity | Retention notes on history; CSV no bulk photo; export audit | DONE §2/§5 (policy pointer) | PARTIAL — audit empty-state copy; no consent/unmask UI on Admin | Spec YES; Compliance fields owned w/ Backend |
| **F1** Security overload | Digital live board + filters + CSV reduce call-arounds | DONE (job of surface) | DONE | YES |

**MVP admin demo gaps (not P2 — close before live build):** host filter on Live; photo thumb → full; visit detail drawer; staff directory / roles management UI not in demo pack (PRD mentions roles Gate/Host/Admin/Security Head — directory is Backend staff APIs). Blacklist CRUD + force Admin/SH copy: **EXISTS** in demo.

## 2) Priority-P2 admin UI gaps (specialist-owned G1–G8 demos now exist; canonical Admin Live remains separate)

| # | Admin screen / gap | Pain | PRD status | Demo |
|---|--------------------|------|------------|------|
| # | Admin screen / gap | Pain | PRD status | Demo |
|---|--------------------|------|------------|------|
| P2-A1 / G9 | **Authorized pickup list CRUD** (student + collectors + effective dates) | D1–D3 | Drafted; Hub P1–P6+H1 **LOCKED**; API Gap | **DEMO EXISTS** — Pickup Admin HTML + shots |
| P2-A2 / G10 | **Custody flags** editor (`none`/`restricted`/`court_order` + `gate_instruction`; no court PDF) | D2 | Same PRD; SH preferred for court_order; API Gap | **DEMO EXISTS** — Pickup Admin |
| P2-A3 / G11 | Pickup history / proof trail filters + export purpose | D1–D3 | Pickup PRD AC-D3/D9; API Gap | **DEMO EXISTS** — Pickup Admin history + purpose stub |
| P2-A4 / G12 | SH **custody override** decision UI (reason required) | D1–D3 | Pickup PRD §6.3 | **STILL MISSING** (history column/chip only) |
| P2-A5 / G5 | **Campus hours + holiday calendar** editor | C4 | Access Rules PRD §6.1 / AC-C4c; API Gap | **DEMO EXISTS** — `demos/access-rules/afterhours.html` + `afterhours-hours-calendar.png`; canonical Admin Live still lacks it |
| P2-A6 / G1–G2 | After-hours **flag + filter** on Live/History | C4 | Access Rules AC-C4b / §15; API Gap | **DEMO EXISTS** — `demos/access-rules/afterhours.html` + `afterhours-admin-flag.png`; canonical Admin Live still lacks it |
| P2-A7 / G4 | **Zone / escort rules** config | B4 | Access Rules §6.2; API Gap | **DEMO EXISTS** — `demos/access-rules/escort-zones.html` + `escort-rules-editor.png`; canonical Admin Live still lacks it |
| P2-A8 / G3 | Escort name + zones columns on Live/History | B4 | Access Rules AC-B4c; API Gap | **DEMO EXISTS** — `demos/access-rules/escort-zones.html` + `escort-admin-audit.png`; canonical Admin Live still lacks it |
| P2-A9 / G6 | **Emergency blast** button on Live | E3-blast | Notifications blast PRD; API trigger Gap; Hub B1–B6 **LOCKED** | **DEMO EXISTS** — `demos/emergency-blast/blast-admin.html` + `blast-admin-cta.png`; canonical Admin Live still lacks it |
| P2-A10 / G7 | Blast **confirm** + template preview | E3-blast | Draft AC-E3a/b; API trigger Gap | **DEMO EXISTS** — `demos/emergency-blast/blast-admin.html` + `blast-confirm.png`; canonical Admin Live still lacks it |
| P2-A11 / G8 | **Blast send result + per-recipient audit** | E3-blast | Draft AC-E3c; API trigger Gap | **DEMO EXISTS** — `demos/emergency-blast/blast-admin.html` + `blast-results.png`; canonical Admin Live still lacks it |

Do **not** pull guard patrol / F2 / F4 / face / PTM into Admin front.

## 3) Force-checkout reason UX (MVP — locked)
**Who:** Admin + Security Head (demo copy says Security Head; Admin PRD §1 allows both).
**Where:** Live board row → **Force** → modal.
**Required:** Non-empty reason textarea; Confirm disabled/blocked until trim(reason); cancel clears.
**Demo behavior (`admin-whos-inside.html`):** modal title "Force checkout"; subline names visitor + pass; placeholder e.g. school closing / left without scan; empty → toast "Enter a force-checkout reason"; success → leave Live board, history checkout type **Force**, reason retained in session.
**AC-L2:** reason required; visit leaves board; history shows Force.
**Build note:** Persist `forceReason` + actor + timestamp; Backend already sketches `POST /visits/{id}/force-checkout { reason }`.

## 4) Role gates (Admin surfaces)

| Action | Gate | Host | Admin | Security Head |
|--------|------|------|-------|---------------|
| Live board / History / Reports view | — | — | YES | YES |
| CSV export (audited) | — | — | YES | YES |
| Force checkout + reason | — | — | YES | YES |
| Blacklist write CRUD | — | — | view/suggest (default) | **YES full** |
| Blacklist override (Block) | no | — | no | **YES** + reason |
| Emergency blast trigger + confirm | — | — | **YES** | **YES** |
| Hours / holiday edit | — | — | **YES** | **YES** |
| Escort/zone rules edit | — | — | **YES** | **YES** |
| Authorized pickup list CRUD | read+execute only | **NO** | **YES** | **YES** |
| Custody `court_order` flag | — | — | limited (`none`/`restricted`) | **YES** + overrides |
| Custody / pickup override | — | — | no | **YES** + reason |

After-hours approver: Hub **A4 LOCKED** = Security-Head-only (dual-approve rejected for Priority-P2).

## 5) READY FOR DEV?
**MVP Admin slice alone: CONDITIONAL YES** — PRD + dark demo contract enough to stub Live / History / Blacklist(view) / Gates / Reports / CSV / force-checkout against Backend contract; close demo gaps (BL CRUD form, photo/host filter/drawer) before pilot UI freeze.

**Full Hub lock (MVP + Priority-P2 Admin): NO**

**Blockers (live):**
1. **V4** — no production / live school gates until Viren go (hard rule).
2. Priority-P2: **P2-A1…A3 / G9–G11 demo Exists** (Pickup Admin); **P2-A5…A11 / G1–G8 demo Exists** (specialist-owned demos), while **P2-A4 / G12** decision UI remains MISSING and API remains Gap — score = **Gap(API)+V4** only (Hub P/A/B **LOCKED**).
3. P2 API still Gap vs live contract (draft OK against locks; live hard-wire V4).
4. Viren **V1** ID capture / **V2** notify channel / **V7** / **L9** / **WA HOLD** as listed — not Hub P/A/B.
5. Separate Admin-surface demos are now landed against locks (UNBLOCKED); do not soft-pass residual API/G12/V4 Gap→Ready.

**Recommendation:** Mark Admin MVP surfaces **spec+demo locked**; mark G1–G8 and G9–G11 as **demo Exists** from their separate Admin-surface HTML evidence, retain G12 as **STILL MISSING**, and retain API/V4 blockers. Hub packages already LOCKED — do not re-ask P1–P6 / A1–A6 / B1–B6.
