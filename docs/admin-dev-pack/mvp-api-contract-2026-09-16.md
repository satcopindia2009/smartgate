# Satcop Smart Visitor — MVP Backend & API Contract
**Date:** 16 Sep 2026 · **Owner:** [VMS] Backend & APIs  
**Consumers:** Mobile Apps, Admin Dashboard, Notifications, UX (field names)  
**Sources:** `prd-mvp-personas-fields-acceptance.md`, `prd-admin-dashboard-mvp.md`, `admin-mvp-fixtures.json`  
**Constraint:** Spec + demo mock only. **No live school deploy until Viren says go.**

---

## 0. Principles
- Multi-tenant by `schoolId`. All queries scoped to school.
- School local TZ for display; store timestamps as ISO-8601 with offset (fixtures use `Asia/Calcutta`).
- Roles: `gate` | `host` | `admin` | `security_head` (map to PRD Gate / Host / Admin / Security Head).
- Demo watermark: responses may include `"meta.watermark": "DEMO"` until production go.
- Photo / ID media: store object keys + signed URLs; never embed binary in list/CSV.

---

## 1. Core entities

### School
| Field | Type | Notes |
|-------|------|--------|
| id | string | |
| name | string | |
| timezone | string | e.g. Asia/Calcutta |
| overdueHoursDefault | number | default 4 |
| config.hostNotifyChannels | string[] | mvp: `["in_app"]`; WhatsApp/SMS when Notifications ready |

### Gate
| Field | Type |
|-------|------|
| id | string |
| schoolId | string |
| name | string | Main Gate, Pedestrian Gate, … |
| active | bool |

### Staff (host directory)
| Field | Type | Notes |
|-------|------|--------|
| id | string | e.g. H03 |
| schoolId | string |
| name | string |
| roleTitle | string | Principal, Teacher, … |
| mobile | string? | for notify |
| userId | string? | linked login if host uses app |
| active | bool |

### User (auth)
| Field | Type | Notes |
|-------|------|--------|
| id | string | |
| schoolId | string | |
| role | enum | gate / host / admin / security_head |
| staffId | string? | required for host |
| gateIds | string[]? | gate users limited to these gates |
| displayName | string | |
| phone / email | string | login identifiers TBD with stack |

### Visit
| Field | Type | Notes |
|-------|------|--------|
| id | string | visitId e.g. V-20260916-014 |
| schoolId | string | |
| visitorName | string | |
| mobile | string | normalized IN 10-digit / E.164 |
| visitorType | enum | Parent / Vendor / Guest / Official / Alumni |
| purpose | string | |
| hostId | string | staff id |
| livePhotoKey | string | media object key |
| idType | enum | Aadhaar / DL / Voter / Passport / Other |
| idNumber | string? | normalized; one of number **or** idImageKey required |
| idImageKey | string? | |
| vehicleNumber | string? | |
| accompanyingCount | number? | |
| notes | string? | gate remarks |
| signatureKey | string? | |
| gateId | string | registration / intended entry |
| registeredByUserId | string | |
| status | enum | see lifecycle |
| rejectReason | string? | |
| decidedAt | datetime? | |
| decidedByUserId | string? | |
| passId | string? | short code e.g. P-4F21 |
| qrToken | string? | opaque; not the passId |
| timeIn | datetime? | check-in |
| timeOut | datetime? | |
| gateInId / gateOutId | string? | MVP same campus |
| checkoutType | enum? | normal / force / never |
| forceCheckoutReason | string? | |
| forceCheckoutByUserId | string? | |
| blacklistHit | bool | |
| blacklistId | string? | |
| blacklistOverrideByUserId | string? | |
| meetingDoneAt | datetime? | host mark |
| createdAt / updatedAt | datetime | |

### Pass / QR token
| Field | Type | Notes |
|-------|------|--------|
| passId | string | human short code on badge |
| token | string | signed/opaque for scan |
| visitId | string | |
| schoolId | string | |
| issuedAt | datetime | |
| expiresAt | datetime? | school policy; optional MVP |
| revoked | bool | on reject after issue (edge) |

### Blacklist entry
Matches admin PRD + fixtures (`BL-01` shape).

### Media object
| Field | Type |
|-------|------|
| key | string |
| schoolId | string |
| kind | live_photo / id_image / signature / other |
| visitId | string? |
| contentType | string |
| createdAt | datetime |
| createdByUserId | string |

### Webhook / notify outbox (for Notifications desk)
| Field | Type | Notes |
|-------|------|--------|
| id | string | |
| schoolId | string | |
| event | enum | visit.pending / visit.approved / visit.rejected / visit.checked_in / visit.checked_out / visit.force_checkout / blacklist.hit / emergency.blast |
| visitId | string? | |
| payload | object | host phone, photo URL, purpose, … |
| channelHints | string[] | in_app / whatsapp / sms / push |
| status | pending / sent / failed | |
| createdAt | datetime | |

### Export audit
who, when, scope, filter summary (Admin PRD AC-X1).

---

## 2. Visit lifecycle (state machine)

```
draft? → pending → approved → inside → completed
                ↘ rejected
         approved → (optional) cancelled before check-in
         inside → force_completed (checkoutType=force)
```

| Status | Meaning |
|--------|---------|
| `pending` | Registered; waiting host |
| `approved` | Host approved; QR issuable; not yet time-in |
| `rejected` | Host rejected (reason required) |
| `inside` | Checked in; on live board |
| `completed` | Checked out (normal) |
| `force_completed` | Admin/SH force checkout |

Transitions:
1. `POST /visits` → `pending` (+ blacklist check; may block create or flag)
2. Host `POST /visits/{id}/approve` → `approved` + issue pass/QR + notify gate
3. Host `POST /visits/{id}/reject` → `rejected` (body.reason required)
4. Gate `POST /visits/{id}/check-in` or `POST /passes/scan` (check_in) → `inside`, set timeIn
5. Gate `POST /passes/scan` (check_out) → `completed`, set timeOut
6. Admin/SH `POST /visits/{id}/force-checkout` → `force_completed`
7. Host `POST /visits/{id}/meeting-done` → sets meetingDoneAt (status unchanged)

Blacklist **Block** without override: cannot leave `pending` into pass issuance / check-in (PRD). Override logged on visit.

---

## 3. Auth (MVP sketch)

- `POST /auth/login` → access token (JWT or session) with `userId`, `schoolId`, `role`, `staffId?`, `gateIds?`
- `GET /auth/me`
- All other routes require Bearer (or cookie) auth
- Authorization matrix matches PRD §3 Roles

| Action | gate | host | admin | security_head |
|--------|------|------|-------|---------------|
| Create visit / check-in/out | ✓ | | | |
| Approve/reject own visits | | ✓ | | |
| Live board / history / CSV | | own only* | ✓ | ✓ |
| Staff/gates manage | | | ✓ | ✓ |
| Blacklist write | | | view† | ✓ |
| Force checkout | | | ✓ | ✓ |

\* Host: only visits where hostId = self (AC-R1).  
† Default: Admin view; write = Security Head (admin PRD).

---

## 4. REST surface (MVP)

Base: `/v1` · JSON · errors `{ "error": { "code", "message", "details?" } }`

### Auth
- `POST /v1/auth/login`
- `GET /v1/auth/me`

### Directory
- `GET /v1/gates`
- `GET /v1/staff?active=true` — host picker
- `POST /v1/staff` · `PATCH /v1/staff/{id}` — admin

### Media
- `POST /v1/media/upload` — multipart; returns `{ key, url }` (signed GET, short TTL)
- `GET /v1/media/{key}` — authorized redirect or signed URL

### Visits
- `POST /v1/visits` — gate registration (required fields per PRD); runs blacklist match
- `GET /v1/visits/{id}`
- `GET /v1/visits` — filters: dateFrom, dateTo, gateId, visitorType, hostId, status, checkoutStatus, blacklistHit, q (name/mobile/passId/visitId); default date=today; max 90 days
- `POST /v1/visits/{id}/approve`
- `POST /v1/visits/{id}/reject` — `{ "reason": "..." }`
- `POST /v1/visits/{id}/check-in` — `{ "gateId"? }`
- `POST /v1/visits/{id}/check-out`
- `POST /v1/visits/{id}/force-checkout` — `{ "reason": "..." }`
- `POST /v1/visits/{id}/meeting-done`
- `GET /v1/visits/inside` — live board (timeIn set, timeOut null); filters gate, type, host, overdueOnly, q

### Passes
- `GET /v1/passes/{passId}` — badge payload (name, photo URL, host, gate, passId)
- `POST /v1/passes/scan` — `{ "token" | "passId", "action": "check_in" | "check_out", "gateId"? }`

### Blacklist
- `GET /v1/blacklist?active=true`
- `POST /v1/blacklist`
- `PATCH /v1/blacklist/{id}` — soft inactive
- `POST /v1/blacklist/match` — `{ mobile?, idType?, idNumber? }` → hit or null (gate pre-check)

### Reports (simple aggregates)
- `GET /v1/reports/today-by-gate`
- `GET /v1/reports/range-by-gate?from&to`
- `GET /v1/reports/visitor-type-mix?from&to`

### Exports
- `POST /v1/exports` — `{ "scope": "history"|"inside"|"blacklist"|"daily_gate_summary", "filters": {} }` → CSV job or direct download ≤10k rows; writes export audit

### Webhooks (internal / Notifications)
- `GET /v1/internal/notify-outbox?status=pending` — service-to-service
- Events emitted on transitions listed in entity section

---

## 5. Blacklist match (server rules)
1. Normalize mobile (IN 10-digit / E.164).  
2. Normalize ID number (strip spaces/dashes).  
3. Hard match mobile **or** (idType + idNumber).  
4. Name-only never auto-blocks.  
5. Skip inactive / expired.  
6. Persist `blacklistHit`, `blacklistId` on visit; Block vs Alert per severity; override requires security_head (+ audit).

---

## 6. Alignment with demo fixtures
Field names intentionally close to `admin-mvp-fixtures.json`:
`visitId`, `passId`, `hostId`, `gate`, `timeIn`, `visitorTypes`, `idTypes`, `severity` Block|Alert, `checkoutType`.

API may use `gateId` + resolve name for display; fixtures can keep denormalized `gate` string for demo UI.

---

## 7. Non-goals (MVP)
- Face match, guard patrol, pre-registration, MSR parent sync, multi-campus rollup APIs
- Bulk photo ZIP export
- Production school cutover

---

## 8. Next engineering steps (this desk)
1. OpenAPI 3 stub from this contract  
2. In-memory / JSON mock server serving fixtures for Admin + Mobile demos  
3. Stack lock with Hub (Node/Nest vs Python/FastAPI, Postgres, object storage) — **after** Viren/Hub say build beyond demo  

---

*Backend & APIs owns this file; Product revises only on Hub/Viren scope locks.*

---

## Appendix — Priority Phase 2 (DRAFT)

> **DRAFT / Gap(demo+API).** This appendix is a pointer only. MVP §§0–8 above remain the Source of Truth for MVP Visit / Pass / Blacklist / Media / Auth and are **unchanged**. Soft-pass forbidden. Live school still **V4**.

**Full P2 draft (entities + REST + auth deltas + outbox):**  
`backend/mvp-api-contract-priority-p2-draft-2026-09-16.md`

**Hub packages LOCKED** (`hub-lock-replies/hub-packages-locked-2026-09-16.md`): **P1–P6+H1** (Pickup), **A1–A6** (Access Rules C4+B4), **B1–B6** (Emergency blast).

| Slice | Entities (draft) | REST groups (draft) |
|-------|------------------|---------------------|
| Pickup | Student, AuthorizedPickupPerson, StudentCustodyFlag (+H1 `allowedPersonIds`), PickupEvent | `/v1/students*`, `/v1/pickups*`, export scopes `pickup_*` |
| After-hours | CampusHours, HolidayCalendar; Visit `afterHours` / `policyTrigger` | `/v1/access-rules/hours`, `/holidays`; SH-only Approve when afterHours |
| Escort/zones | ZoneLabel, EscortZoneRule; Visit escort stamps | `/v1/zones`, `/v1/access-rules/escort`, assign-escort/waive; pass GET chrome |
| Blast | School blast config, BlastTemplate, EmergencyBlast, BlastRecipient | `/v1/emergency/blasts*` per Blast PRD §8 |

**Out of scope here:** Campus API, Visit-subtype pickup, geo-fence, patrol/face/MSR/PTM, live SMS/WA, production deploy.

**One-page note:** `hub-lock-replies/backend-p2-api-draft-note-2026-09-16.md`
