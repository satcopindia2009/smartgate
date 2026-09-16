# Satcop Smart Visitor (School VMS) — Development Handoff Pack
**Prepared:** 2026-09-16 17:33 IST · **Scope refresh:** ~23:25 IST 16 Sep 2026  
**Prepared by:** [Hub] satcop office for Viren · scope lines refreshed by [VMS] Product & Spec  
**Purpose:** Transfer this entire folder to another Grok account to **develop** the product. This Hub account stops at documentation + static demos unless Viren says otherwise.

---

## 1. What this product is

School **Visitor Management System (VMS)** for Satcop — digital gate entry/exit, host approval, QR pass, live “who’s inside” admin board, blacklist, history/export.

**Overall scope (Hub lock):** **MVP + Priority Phase 2 only** — pickup/custody (D1–D3), after-hours/holiday (C4), escort/zones (B4), emergency blast (E3). See Priority-P2 PRDs + `product-coverage-checklist-hub-lock-2026-09-16.md` + `hub-lock-replies/hub-packages-locked-2026-09-16.md`.  
**Out of this front:** Differentiator Phase 2 (guard patrol, PTM/pre-reg, face match, MSR parent link, lost & found, multi-campus) — after pilot; do **not** pull into current build.

Sits beside Smart Bus / My School Ride (road safety) as **campus gate safety**.

**Working name:** Satcop Smart Visitor / Visitor App  
**Company:** Satcop India · satcop.com · sales@satcop.com

---

## 2. Hard locks (read before coding)

1. **Demo-first** — static HTML demos are the visual + flow contract until Viren says go on live build/deploy.  
2. **No live school gates / no production deploy** until Viren explicitly says go.  
3. **WhatsApp sample-message HOLD** may still be ON on the Hub account — do not blast test WA from this product without Viren OK.  
4. **Walkthrough story (must match across surfaces):** visitor **Priya Sharma** → host **Anita Joshi** → gate **Main Gate** → pass **`P-4F21`**.  
5. **Gate enums:** `Main` | `Pedestrian` | `Staff` | `Bus Bay`.  
6. **Blacklist hard-match:** mobile **or** govt ID only.  
7. **Force checkout** requires a reason.  
8. **Retention (Compliance):** visit **metadata** long/searchable; **photo** ~90 days; **ID image** ~30 days — not “history forever” for media. ID-capture mode (number vs photo) still held for Viren (**V1** OPEN).  
9. **Hub packages LOCKED:** P1–P6+H1 (Pickup), A1–A6 (After-hours + zone defaults), B1–B6 (Blast). Do not reopen.  
10. **Coding defaults (not locks):** Viren lifted demo-only for **CODING**; live school still **HOLD** (**V4**). Until Viren picks: **V1** = number + optional photo; **V2** = in-app/SMS (WhatsApp HOLD); **V7** = Compliance pack windows. See MVP PRD §6.

---

## 3. Read in this order

| Order | File | What it is |
|------:|------|------------|
| 1 | `school-visitor-management-brief-2026-09-16.md` | Pain points, app solutions, bot roster, MVP vs Priority P2 vs Differentiator |
| 2 | `product-coverage-checklist-hub-lock-2026-09-16.md` | Hub lock map; P/A/B LOCKED; open V1/V2/V7/V4 + coding defaults |
| 3 | `prd-mvp-personas-fields-acceptance.md` | Personas, fields, roles, AC; open locks + coding defaults |
| 4 | `prd-admin-dashboard-mvp.md` | Live board / history / blacklist / CSV / multi-gate |
| 5 | `prd-pickup-custody-phase2.md` | Priority P2 — PickupEvent + custody (**P1–P6+H1 LOCKED**) |
| 6 | `prd-access-rules-phase2.md` | Priority P2 — after-hours + escort/zones (**A1–A6 LOCKED**) |
| 7 | `notifications/prd-emergency-blast-priority-p2.md` | Priority P2 — emergency blast (**B1–B6 LOCKED**) |
| 8 | `hub-lock-replies/hub-packages-locked-2026-09-16.md` | Canonical Hub packages stamp |
| 9 | `backend/mvp-api-contract-2026-09-16.md` | API contract (visit lifecycle, QR, media, blacklist) |
| 10 | `demos/README.md` + open `demos/index.html` | Dark Phase 1 UI pack (gate / host / QR / admin) |
| 11 | `demos/SCREEN-GALLERY-INDEX-2026-09-16.md` | Gallery index; MVP gaps + Priority-P2 paint folders |
| 12 | `compliance-data-pack-2026-09-16.md` | Retention, consent EN+HI, DPDP, export |
| 13 | `sales-demo-pack-2026-09-16.md` | Pitch / demo script for schools |
| 14 | `support-playbook/` | Gate SOP, training, escalation, proposed Faveo Issue Types |
| 15 | `prd-guard-patrol-phase2.md` + `desk-ac-guard-patrol-phase2.md` | **Differentiator / Later** — after pilot; out of this front |
| 16 | `demos/guard-patrol/` | Guard UI freeze (reference only — not current front) |
| 17 | `mobile-guard-phone-demo-freeze-2026-09-16.md` | Mobile/guard phone notes (Later) |

HTML twin of the brief: `school-visitor-management-brief-2026-09-16.html`

---

## 4. Surfaces to build (MVP)

| Surface | Owner intent | Demo file |
|---------|----------------|-----------|
| Gate / reception tablet (kiosk) | Security registers visitor | `demos/gate-tablet.html` |
| Host approve (mobile web/app) | Teacher/admin approve/reject | `demos/host-approve.html` |
| Visitor QR pass | Show pass on phone | `demos/visitor-qr.html` |
| Admin web dashboard | Live inside, history, blacklist, reports | `demos/admin-whos-inside.html` (+ related admin pages in demos/) |

**Priority Phase 2 (in current front with MVP):** Pickup & custody, after-hours, escort/zones, emergency blast — Priority-P2 PRDs + demos under `demos/pickup/`, `demos/pickup-custody/`, `demos/access-rules/`, `demos/emergency-blast/` (design-review paint; Priya walkthrough untouched).

**Differentiator / Later (out of this front):** Guard patrol phone app — see `demos/guard-patrol/` and `prd-guard-patrol-phase2.md`. Do not build into current front.

---

## 5. Suggested tech approach (non-binding)

Implementer may choose stack. Constraints that matter:

- Multi-tenant by **school / campus**; multi-**gate** per campus.  
- Roles: Gate, Host, Admin, Security Head (see PRD).  
- Media upload for live photo + ID; store keys as in API contract.  
- Notifications: host approve via app push and/or WhatsApp/SMS (India) — wire later; respect Viren WA hold for samples.  
- Offline-ish gate tablet is nice-to-have, not MVP blocker.  
- Match dark demo UX closely (charcoal/navy, purple/blue accents).

---

## 6. Hub bot map (context only — other account may recreate)

Visitor App Dep room (specialists hidden from sidebar). Room seats max 6; others exist as hidden bots:

**In room:** Product & Spec · UX & Screens · Admin Dashboard · Backend & APIs · Mobile Apps (+ Hub)  
**Also created:** Sales & Demo · Notifications · Guard Patrol · Support Playbook · Compliance & Data  

Do **not** assume those bots exist on the new account — this zip is the source of truth.

---

## 7. How to open demos locally

```bash
cd demos
python3 -m http.server 8766
# open http://127.0.0.1:8766/
```

Screenshots: `demos/shots/`

---

## 8. Open decisions for Viren / Product (before production)

**Still OPEN (do not mark locked):**
- [ ] **V1** ID capture: govt ID **number only** vs **ID photo** (or both)  
- [ ] **V2** Host notify channel (in-app / SMS / WhatsApp) — WhatsApp **HOLD**  
- [ ] **V7** Exact retention windows legal sign-off  
- [ ] **V4** Go / no-go on live school / real backend deploy  
- [ ] Pilot school shortlist (**V3**)  
- [ ] Pricing / packaging with Smart Bus (**V6**)  

**Coding defaults (NOT locks — coding may proceed; live school still HOLD):**
- **V1:** number + optional photo  
- **V2:** in-app / SMS (WhatsApp HOLD)  
- **V7:** Compliance pack windows (metadata long / photo ~90d / ID image ~30d)  

**Already LOCKED (do not reopen):** Hub packages **P1–P6+H1 / A1–A6 / B1–B6**. Scope = MVP + Priority P2 only — no Differentiator expansion.

**READY FOR LIVE DEV: NO.**

---

## 9. Zip contents

This handoff zip = entire `visitor-vms-project/` tree (docs + demos + fixtures + shots).

---

*End of handoff. Build against demos + MVP PRD + Priority-P2 PRDs + API contract. Scope = MVP + Priority P2 only. No live school until Viren says go (V4).*
