# Satcop Smart Visitor — Admin Dashboard MVP PRD
**Date:** 16 Sep 2026 · **Owner:** [VMS] Product & Spec · **Consumer:** [VMS] Admin Dashboard  
**Parent:** `school-visitor-management-brief-2026-09-16.md` + `prd-mvp-personas-fields-acceptance.md`  
**Constraint:** Demo-first. No live school gates until Viren says go.

---

## 1. Admin persona & MVP “who’s inside” board

### Who
**Admin** (Principal / office admin) and **Security Head** (same board; Security Head also edits blacklist + force-checkout).

### Job on this surface
Answer in seconds: *Who is on campus right now, which gate, who approved them, how long have they been inside?*

### Live board — row fields (MVP)
| Column | Source | Notes |
|--------|--------|--------|
| Visitor name | Registration | |
| Photo thumb | Live photo | Tap → full |
| Visitor type | Enum | Parent / Vendor / Guest / Official / Alumni |
| Mobile | Registration | Masked for Host role if ever shown; Admin full |
| Host | Staff picker | Name + role |
| Purpose | Registration | Truncate + expand |
| Gate / entry point | Check-in | |
| Time-in | Check-in stamp | Local school TZ |
| Duration | Computed | Highlight > school-configured max (default 4h) |
| Pass / QR id | System | Short code |
| Status | Always “Inside” on this board | |
| Flags | System | Blacklist near-miss, overdue, force-checkout pending |

### Live board — filters / controls
- Gate (multi-select), visitor type, host, duration overdue only
- Search: name, mobile last 4, pass id
- Actions (Security Head / Admin): **Force checkout** (reason required), **View visit detail**
- Auto-refresh ≤ 30s (demo can poll); empty state: “No visitors inside”

### Non-goals on live board
History, reports charts, blacklist CRUD (separate tabs), pre-reg queue.

---

## 2. Visit history — columns + filters

**Retention (Hub-locked):** Metadata rows stay searchable under long-retention. Photo thumbs may placeholder after **90 days** post checkout; ID images gone after **30 days**. Never imply media is kept forever (`compliance-data-pack-2026-09-16.md` §3).

### Columns (table + detail drawer)
| Column | Notes |
|--------|--------|
| Visit id | Stable id |
| Visitor name | |
| Photo | Thumb |
| Mobile | |
| Visitor type | |
| Purpose | |
| Host | |
| Host decision | Approved / Rejected + reason if rejected |
| Decision at | Timestamp |
| Gate in | |
| Time-in | |
| Gate out | May differ if multi-gate exit supported later; MVP same campus |
| Time-out | Blank if open / force-closed |
| Checkout type | Normal / Force / Never (open) |
| Duration | |
| Blacklist hit | Yes/No |
| Registered by | Gate user |
| Notes | Gate remarks |

### Filters
- Date range (required default: today; max range MVP: 90 days)
- Gate, visitor type, host, decision status (Pending / Approved / Rejected)
- Checkout status: Inside / Completed / Force / Open overnight
- Blacklist hit only
- Search: name, mobile, pass id, visit id

### Sort default
Time-in descending.

---

## 3. Blacklist — fields + match rules

### Record fields
| Field | Required | Notes |
|-------|----------|--------|
| Full name | Yes | Display + fuzzy assist only |
| Mobile | One of mobile **or** ID number required | Normalized E.164 / IN 10-digit |
| Govt ID type | If ID used | Same enum as registration |
| Govt ID number | If ID used | Normalized (strip spaces) |
| Reason | Yes | Why banned |
| Severity | Yes | Block (hard) / Alert (soft warn — still needs Security Head override to issue pass) |
| Active | Yes | Soft delete via inactive |
| Added by / at | System | |
| Expires on | Optional | Null = indefinite |
| Notes | Optional | |
| Photo (optional) | No | If available from prior visit |

### Match rules (at registration / check-in)
1. **Hard match mobile** (normalized) → Block or Alert per severity.
2. **Hard match govt ID number** (same type + normalized number) → Block or Alert.
3. **Name-only never auto-blocks** (too many false positives). Optional “similar name” hint is Phase 2.
4. Inactive or expired entries do not match.
5. On hit: Gate cannot issue pass for **Block** without Security Head override (logged). **Alert** shows banner; Gate may proceed only after explicit acknowledge (logged).
6. Match event stored on visit: `blacklist_hit`, `blacklist_id`, `override_by`.

### Who can CRUD
Security Head: full CRUD. Admin: view + suggest (or full CRUD if school config says Admin=Security Head — default: Security Head only for write).

---

## 4. Multi-gate report dimensions (MVP)

MVP reports are **simple aggregates**, not a BI tool.

### Dimensions
- Gate / entry point
- Date (day) and optional week
- Visitor type
- Host (top hosts by visit count)
- Decision: approved vs rejected rate
- Checkout completion rate (same-day checkout / total approved check-ins)

### Metrics
- Visit count
- Unique visitors (by mobile)
- Median host approval time
- Currently inside (point-in-time, not historical series in MVP)
- Blacklist hits count
- Force-checkouts count

### Views
1. **Today by gate** — visits in, still inside, checkouts  
2. **Date range by gate** — counts + approval rate  
3. **Visitor type mix** — pie/table  

Phase 2: campus rollup for chains, hour-of-day heat, guard patrol.

---

## 5. CSV export scopes

### Scopes (pick one per export)
| Scope | Rows included | Default columns |
|-------|----------------|-----------------|
| **History (filtered)** | Current history filter result | All history columns above; photo as URL/reference not binary |
| **Currently inside** | Live board snapshot | Live board columns + visit id |
| **Blacklist** | Active (+ optional inactive) | All blacklist fields |
| **Daily gate summary** | One row per gate per day in range | Gate, date, check-ins, check-outs, peak inside, rejects, blacklist hits |

### Rules
- Max rows MVP: 10k; if over, require narrower date filter.
- UTF-8 CSV; timestamps ISO-8601 with offset or school-local clearly labeled.
- Role: Admin + Security Head only.
- Export action audited (who, when, scope, filter summary).
- No bulk photo zip in MVP.

---

## 6. Acceptance criteria (Admin surfaces)

- **AC-L1** Live board shows only visits with time-in and no time-out; updates within 30s of check-in/out.
- **AC-L2** Force checkout requires reason; visit leaves board; history shows checkout type Force.
- **AC-H2** History respects filters; default Today; CSV matches on-screen filter set.
- **AC-BL1** Blacklist mobile/ID hard match blocks pass per severity rules; override logged.
- **AC-R2** Multi-gate today view totals equal sum of per-gate check-ins for that day.
- **AC-X1** CSV scopes above only; export audit row written.

---

## 7. Handoff
UX/Screens: dark demo layouts for Live / History / Blacklist / Reports.  
Admin Dashboard bot: implement demo data shape matching these columns.  
Product & Spec: revise only on Hub/Viren lock changes.

*End admin MVP PRD slice.*
