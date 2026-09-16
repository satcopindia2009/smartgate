# Satcop Smart Visitor — MVP API Stub + P2 Pickup + After-hours + Escort / Zones

In-memory FastAPI mock implementing `mvp-api-contract-2026-09-16.md` §§0–5 under `/v1`, plus **Priority P2 Pickup & Custody** (Hub **P1–P6+H1**), **after-hours / holiday Access Rules** (Hub **A1–A6 / C4**), and **escort / zones** (Hub **B4**).  
For Mobile / Admin **showable demos**. **Not for live school deploy** (V4 HOLD).

Day-1 routes are hardened for visit state machine, host-scoped approve, pass scan errors, blacklist §5 match, media keys, and the contract error envelope. Pickup is a **separate `PickupEvent`** — not a Visit subtype. After-hours Approve (SH-only) is unchanged. Blast / geo-fence are **not** in this slice.

## Stack

- Python 3.12+
- FastAPI + Pydantic v2 + PyJWT + uvicorn
- In-memory store (re-seeded on process start)
- CORS `*`
- JWT Bearer auth (`userId`, `schoolId`, `role`, `staffId?`, `gateIds?`)
- Error envelope: `{ "error": { "code", "message", "details?" } }` (including request validation)
- Responses include `"meta": { "watermark": "DEMO" }`

## Run

```bash
cd /workspace/satcop-smart-visitor-api
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

- API base: `http://127.0.0.1:8080/v1`
- OpenAPI: `http://127.0.0.1:8080/docs` · committed spec `openapi.json`
- Health: `http://127.0.0.1:8080/health`

Regenerate the committed spec after route/model changes:

```bash
python3 scripts/gen_openapi.py
```

## Demo seed logins

School: **Demo International School** · TZ `Asia/Calcutta` · `schoolId=SCH-DEMO-01`

| Username   | Password  | Role           | Notes                          |
|------------|-----------|----------------|--------------------------------|
| `gate`     | `gate123` | gate           | Gate — Ramesh; all 4 gates     |
| `host`     | `host123` | host           | Anita Joshi (H03)              |
| `rahul`    | `host123` | host           | Rahul Deshpande (H02)          |
| `admin`    | `admin123`| admin          | Office Admin                   |
| `security` | `sh123`   | security_head  | Blacklist write + force + Block override create |

### Seed highlights

- Gates: Main Gate (`G-MAIN`), Pedestrian Gate, Staff Gate, Bus Bay
- Staff includes **Anita Joshi** (`H03`) and assignable escort **Vikram More** (`E01`)
- Walkthrough pass: **Priya Sharma** → passId **`P-4F21`** → status `inside` (Main Gate)
- Approved ready for scan: **Kiran Desai** → **`P-C101`** → status `approved` (Main Gate)
- Blacklist: `BL-01` Vikram More (Block), `BL-02` Neha Salunkhe (Alert)
- Pending visit for host demo: `V-20260916-040` (host H03)
- **Campus hours (A1):** Mon–Fri `08:00–18:00`, Sat `08:00–13:00`, Sun closed; timezone **Asia/Kolkata**
- **Holidays (A2):** one fixture — `2026-10-20` Diwali (`HOL-DIWALI`)
- **After-hours demos (separate from Priya):** evening Vendor **Ravi Deshmukh** `V-AH-VENDOR` pending SH (host Anita / H03); holiday Parent **Deepak Nair** → **Meera Kulkarni** (H01) → pass **`P-7K88`** (`V-AH-HOLIDAY`)
- **Escort / zones (B4):** Vendor default `escortRequired=true` zones `reception`+`admin`; Parent/Guest/Alumni reception only (no escort); Official reception+admin (no escort). **Ravi** is assignable to **Vikram More** (`E01`). P-7K88 / Meera and Priya MVP unchanged.
- **Pickup (P6, separate from Priya):** student **Aarav Mehta · 5-B** (`STU-AARAV`) with **Neha Mehta (Mother)** + **Rohan Mehta (Uncle/Relative)**; **Kabir Singh** (`STU-KABIR`) `court_order` blocking **Rajesh Singh**, allow-list **Sunita Singh**

## Visit lifecycle (contract §2)

```
pending → approved → inside → completed
       ↘ rejected
inside → force_completed   (Admin/SH; body.reason required)
```

| Action | From | Notes |
|--------|------|--------|
| `POST /visits` | → `pending` | Gate (or SH override create). Blacklist Block without override → `BLACKLIST_BLOCK`. Stamps sticky `afterHours` / `policyTrigger` / `afterHoursEvaluatedAt` (Asia/Kolkata; **not** recomputed later) **and** `escortRequired` / `allowedZones` from EscortZoneRule (Contractor → Vendor) |
| `POST /visits/{id}/approve` | `pending` → `approved` | In-hours: Host **own visits only**. After-hours: **Security Head only** with `{ "reason" }`; Host/Admin → `AFTER_HOURS_SH_REQUIRED` (no-op). Issues `passId` + `qrToken` |
| `POST /visits/{id}/reject` | `pending` → `rejected` | `{ "reason": "..." }` required. After-hours: SH only (A6) |
| `POST /visits/{id}/check-in` or `POST /passes/scan` `check_in` | `approved` → `inside` | Sets `timeIn`, `gateInId`. If `escortRequired && !escortStaffId && !escortWaived` → `ESCORT_REQUIRED` |
| `POST /visits/{id}/check-out` or scan `check_out` | `inside` → `completed` | `checkoutType=normal`; sets `escortClearedAt` (last escort retained) |
| `POST /visits/{id}/force-checkout` | `inside` → `force_completed` | `{ "reason": "..." }` required; sets `escortClearedAt` |
| `POST /visits/{id}/meeting-done` | `approved` \| `inside` | Sets `meetingDoneAt`; **status unchanged** |

Illegal transitions return `409` `{ "error": { "code": "INVALID_STATE" } }`.

## Auth matrix (MVP)

| Action                         | gate | host | admin | security_head |
|--------------------------------|:----:|:----:|:-----:|:-------------:|
| Create / check-in / out / scan | ✓    |      |       | ✓ create w/ Block override |
| Approve / reject own + meeting-done | | ✓* | ✓ | ✓ |
| Approve / reject when `afterHours=true` | | FYI only | | ✓ reason |
| Assign / confirm escort        | ✓    | suggest only | | ✓ |
| Waive escort                   |      |      |       | ✓ reason      |
| Hours / holidays / zones / escort rules | | | ✓ | ✓ |
| Force checkout                 |      |      | ✓     | ✓             |
| Staff POST/PATCH               |      |      | ✓     | ✓             |
| Blacklist write                |      |      | view  | ✓             |
| Live board / history           | ✓    | own* | ✓     | ✓             |

\* Host scoped to `hostId == self.staffId`.  
Gate users are limited to `user.gateIds` (login + JWT). `GET /gates` is filtered to assigned gates.

## Pickup & Custody (Priority P2 · Hub P1–P6+H1)

Separate `PickupEvent` entity. Default is **lobby release** (no Visit/QR). Optional `linkVisit` creates a linked Parent Visit only if the collector also enters campus.

```
Matching → Released
         → BlockedNotAuthorized
         → BlockedCustody
         → ReleasedWithOverride
```

| Action | Role | Notes |
|--------|------|-------|
| `GET/POST /students` · `PATCH /students/{id}` | Gate read; Admin/SH write | Manual CRUD (no MSR) |
| `GET/POST/PATCH …/authorized-pickup` | Gate read; Admin/SH write | Consent version required on create; soft-deactivate only |
| `GET/PUT …/custody-flag` | Gate sees **flag + `gateInstruction` ≤280 only**; Admin `none`/`restricted`; SH writes `court_order` | No court PDF / narrative |
| `POST /pickups` | Gate | Starts `Matching`; claimed collector = person id **or** mobile/id |
| `POST /pickups/{id}/consent` | Gate | **Must succeed before** photo accept / release |
| `POST /pickups/{id}/release` | Gate | Consent + `collectorLivePhotoRef`; server applies §1.3 rules |
| `POST /pickups/{id}/request-override` | Gate | Status stays blocked; SH alert outbox |
| `POST /pickups/{id}/override` | SH | `{ "reason" }` required → `ReleasedWithOverride` |
| `GET /pickups` | Admin/SH; Gate = own attempts | Filters: dateFrom/dateTo, studentId, gateId, status, override, q |

**Decision rules (server):** active + in-date list; match **mobile OR** (`idType` + idNumber/last4); **name-only never auto-matches**. `none` → allow matched person. `restricted`/`court_order` + `blockedPersonIds` → `BlockedCustody`. Optional `allowedPersonIds` (**H1**) is a machine allow-list when present. `court_order` with empty `gateInstruction` **fail-closed** (write rejected + all releases blocked). Gate cannot self-override. Expired `effectiveTo` = not on list (F3).

**Media kinds (additive):** `collector_live_photo` (90d retention stamp), `pickup_list_photo`.

**Outbox:** `pickup.blocked_custody`, `pickup.override_requested`, `pickup.override_completed` (SH channelHints). `pickup.released` class-teacher notify stays **OFF**.

**Exports:** scopes `pickup_events`, `pickup_lists` (lists require logged `purpose` — AC-D9).

Out of this slice: blast, geo-fence, face match, MSR, live school (V4), Patrol.

## After-hours / holiday (Priority P2 · Hub A1–A6 / C4)

Per-school weekday hours + holiday date calendar. Evaluation is **sticky on `POST /v1/visits` only** (A3 / AC-C4e). Dual-approve is **rejected**. After-hours Approve is unchanged in this slice; Vendor after-hours still gets B4 escort stamps.

```
afterHours=false → normal MVP Host Approve
afterHours=true  → Pending (SH gate)
  ├─ Host notified (FYI outbox) — Host Approve is NO-OP (does not → Approved)
  ├─ Security Head Approve ({ "reason" } required — A6) → Approved
  └─ Security Head Reject ({ "reason" } required) → Rejected
```

| Action | Role | Notes |
|--------|------|-------|
| `GET /access-rules/hours` | any authed | 7 weekday rows; timezone Asia/Kolkata |
| `PUT /access-rules/hours` | Admin/SH | Full week array; `closeTime < openTime` requires `overnight=true` |
| `GET /access-rules/holidays?from&to` | any authed | Date calendar (no CSV) |
| `POST /access-rules/holidays` | Admin/SH | `{ date, label? }`; duplicate date rejected |
| `DELETE /access-rules/holidays/{id}` | Admin/SH | |
| `GET /visits` · `GET /visits/inside` | (MVP roles) | Extra filters: `afterHours`, `policyTrigger` |

**Evaluation (create time → Asia/Kolkata):** holiday date wins even if the clock is inside the weekday window; close is **exclusive** `[open, close)`; overnight is a single window when `close < open`. `policyTrigger` = `outside_hours` \| `holiday` \| `both`.

### Curl — hours + after-hours SH approve

```bash
BASE=http://127.0.0.1:8080/v1
ADMIN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
SH=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"security","password":"sh123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

curl -s "$BASE/access-rules/hours" -H "Authorization: Bearer $ADMIN" | python3 -m json.tool
curl -s "$BASE/access-rules/holidays" -H "Authorization: Bearer $ADMIN" | python3 -m json.tool
curl -s "$BASE/visits/V-AH-VENDOR" -H "Authorization: Bearer $SH" | python3 -m json.tool
curl -s "$BASE/visits/V-AH-HOLIDAY" -H "Authorization: Bearer $SH" | python3 -m json.tool
curl -s "$BASE/passes/P-7K88" -H "Authorization: Bearer $ADMIN" | python3 -m json.tool

# Host Approve on after-hours is a no-op (403 AFTER_HOURS_SH_REQUIRED)
# SH Approve with reason:
curl -s -X POST "$BASE/visits/V-AH-VENDOR/approve" \
  -H "Authorization: Bearer $SH" -H 'Content-Type: application/json' \
  -d '{"reason":"Verified night vendor call"}' | python3 -m json.tool
```

## Escort / zones (Priority P2 · Hub B4)

Fixed zone keys (school-renamable **labels** only). EscortZoneRule by `visitorType`. **No geo-fence / beacon.** Restricted in `allowedZones` forces `escortRequired=true`. Contractor maps to **Vendor**.

| Type | escortRequired | allowedZones |
|------|----------------|--------------|
| Vendor | **true** | `reception`, `admin` |
| Parent | false | `reception` |
| Guest | false | `reception` |
| Official | false | `reception`, `admin` |
| Alumni | false | `reception` |

| Action | Role | Notes |
|--------|------|-------|
| `GET /zones` | any authed | 7 starter keys + school labels |
| `PATCH /zones/{key}` | Admin/SH | rename `label` only |
| `GET /access-rules/escort` | any authed | rules by visitorType |
| `PUT /access-rules/escort` | Admin/SH | upsert per type; restricted force server-side |
| `POST /visits/{id}/assign-escort` | Gate / SH | `{ "escortStaffId" }`; Host suggest only |
| `POST /visits/{id}/waive-escort` | SH | `{ "reason" }` required |

Check-in / scan `check_in` blocked with `ESCORT_REQUIRED` when escort is required and not assigned/waived (AC-B4e). Checkout sets `escortClearedAt`; last escort name/id stays on history (AC-B4f). `GET /passes/{passId}` adds `escortRequired`, `escortName`, `allowedZoneLabels` (+ `afterHours` chrome).

### Curl — zones + Ravi assign Vikram More

```bash
BASE=http://127.0.0.1:8080/v1
GATE=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"gate","password":"gate123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
ADMIN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
SH=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"security","password":"sh123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

curl -s "$BASE/zones" -H "Authorization: Bearer $ADMIN" | python3 -m json.tool
curl -s "$BASE/access-rules/escort" -H "Authorization: Bearer $ADMIN" | python3 -m json.tool
curl -s "$BASE/visits/V-AH-VENDOR" -H "Authorization: Bearer $SH" | python3 -m json.tool

curl -s -X POST "$BASE/visits/V-AH-VENDOR/assign-escort" \
  -H "Authorization: Bearer $GATE" -H 'Content-Type: application/json' \
  -d '{"escortStaffId":"E01"}' | python3 -m json.tool
```

### Curl — Aarav / Neha lobby release

```bash
BASE=http://127.0.0.1:8080/v1
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"gate","password":"gate123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

curl -s "$BASE/students?q=Aarav" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
curl -s "$BASE/students/STU-AARAV/authorized-pickup" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
curl -s "$BASE/students/STU-KABIR/custody-flag" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

PK=$(curl -s -X POST "$BASE/pickups" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"studentId":"STU-AARAV","gateId":"G-MAIN","pickupReason":"early","collectorMobile":"9822011001","collectorName":"Neha Mehta"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')

curl -s -X POST "$BASE/pickups/$PK/consent" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"pickupConsentVersion":"pickup_notice_en_hi_v1"}'

MEDIA=$(curl -s -X POST "$BASE/media/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@README.md;type=image/jpeg' \
  -F 'kind=collector_live_photo' \
  -F "pickupId=$PK")
KEY=$(echo "$MEDIA" | python3 -c 'import sys,json; print(json.load(sys.stdin)["key"])')

curl -s -X POST "$BASE/pickups/$PK/release" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"collectorLivePhotoRef\":\"$KEY\"}" | python3 -m json.tool
```

## Pass / media / blacklist

- `POST /v1/passes/scan` — `{ "token" | "passId", "action": "check_in" | "check_out", "gateId"? }`
  - unknown → `PASS_NOT_FOUND` (404)
  - revoked → `PASS_REVOKED` (410)
  - expired → `PASS_EXPIRED` (410)
  - wrong status → `INVALID_STATE` (409)
- `GET /v1/passes/{passId}` — badge: name, photo URL, host, gate, passId, plus `escortRequired` / `escortName` / `allowedZoneLabels` / `afterHours`
- `POST /v1/media/upload` — multipart `file` + `kind` = `live_photo` \| `id_image` \| `signature` \| `other` \| `collector_live_photo` \| `pickup_list_photo` → `{ key, url }`
- Visit create requires `livePhotoKey` (known key) and **one of** `idNumber` or `idImageKey`
- Blacklist match (§5): normalize mobile (IN 10-digit / E.164) **or** (`idType` + `idNumber`); name-only never matches; skip inactive / expired

## Error codes (Mobile/Admin)

| code | typical HTTP | when |
|------|--------------|------|
| `UNAUTHORIZED` | 401 | missing/invalid Bearer |
| `INVALID_CREDENTIALS` | 401 | bad login |
| `FORBIDDEN` | 403 | role / host-scope / gate assignment / Block override |
| `VALIDATION` | 400 | missing fields, empty reason, bad mobile, unknown media key |
| `NOT_FOUND` | 404 | visit (or hidden from host) |
| `PASS_NOT_FOUND` | 404 | unknown pass/token |
| `PASS_REVOKED` / `PASS_EXPIRED` | 410 | scan |
| `INVALID_STATE` | 409 | illegal lifecycle transition |
| `BLACKLIST_BLOCK` | 403 | Block hit without SH override |
| `AFTER_HOURS_SH_REQUIRED` | 403 | Host/Admin Approve or Reject when `afterHours=true` (no state change) |
| `ESCORT_REQUIRED` | 403 | Check-in / scan check_in when escort required and not assigned/waived |

Pydantic/request validation uses the same `{ "error": { "code": "VALIDATION", ... } }` envelope (not FastAPI `{ detail: [...] }`).

## Curl happy-path (Priya already inside; new visitor lifecycle)

```bash
BASE=http://127.0.0.1:8080/v1

# 1) Login as gate — user.gateIds present
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"gate","password":"gate123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')

# 2) List gates + staff (Anita Joshi H03)
curl -s "$BASE/gates" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | head
curl -s "$BASE/staff?active=true" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | head

# 3) Upload stub photo
MEDIA=$(curl -s -X POST "$BASE/media/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@README.md;type=image/jpeg' \
  -F 'kind=live_photo')
KEY=$(echo "$MEDIA" | python3 -c 'import sys,json; print(json.load(sys.stdin)["key"])')

# 4) Register visit
VISIT=$(curl -s -X POST "$BASE/visits" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"visitorName\":\"Test Parent\",\"mobile\":\"9822099999\",\"visitorType\":\"Parent\",\"purpose\":\"Demo\",\"hostId\":\"H03\",\"livePhotoKey\":\"$KEY\",\"idType\":\"Aadhaar\",\"idNumber\":\"999988887777\",\"gateId\":\"G-MAIN\"}")
VID=$(echo "$VISIT" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')

# 5) Host approve
HTOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"host","password":"host123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
APPROVED=$(curl -s -X POST "$BASE/visits/$VID/approve" -H "Authorization: Bearer $HTOKEN")
PASS=$(echo "$APPROVED" | python3 -c 'import sys,json; print(json.load(sys.stdin)["passId"])')

# 6) Gate scan check-in + check-out
curl -s -X POST "$BASE/passes/scan" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"passId\":\"$PASS\",\"action\":\"check_in\",\"gateId\":\"G-MAIN\"}"
curl -s -X POST "$BASE/passes/scan" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"passId\":\"$PASS\",\"action\":\"check_out\"}"

# Seed passes
curl -s "$BASE/passes/P-4F21" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
curl -s "$BASE/passes/P-C101" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## Tests

```bash
source .venv/bin/activate
pytest -q
# or
bash scripts/smoke.sh
```

- `tests/test_smoke.py` — health, seed pass, full lifecycle, staff write
- `tests/test_contract.py` — host-scope approve, force-checkout without reason, blacklist Block/Alert, invalid pass scan, gateIds, media kinds, state machine negatives
- `tests/test_pickup.py` — Aarav/Neha release, Rohan relative, Kabir BlockedCustody, not-authorized, override, H1 allow-list, no Visit subtype coupling
- `tests/test_pickup_acceptance.py` — AC-D1 / AC-D2 / AC-D3 / F3 / F6 explicit
- `tests/test_after_hours.py` — A1–A6 / C4: hours+holidays CRUD, in-hours host approve, outside/holiday sticky, host no-op, SH reason, sticky not recomputed
- `tests/test_after_hours_acceptance.py` — AC-C4a / AC-C4b / AC-C4c / AC-C4d / AC-C4e + close-exclusive / holiday-wins edges
- `tests/test_escort.py` — B4: zone keys, Vendor default ON, Vikram staff, Ravi assignable, P-7K88/Meera/Priya intact, Contractor→Vendor, assign/waive
- `tests/test_escort_acceptance.py` — AC-B4a / AC-B4b / AC-B4c / AC-B4d / AC-B4e / AC-B4f

## Remaining thin stubs / out of scope

OK to stay thin (not blocking Mobile/Admin demos):

- Reports (`/v1/reports/*`) — simple in-memory aggregates; `medianApprovalSec` is null
- Exports (`POST /v1/exports`) — sync CSV ≤ current store; audit row kept in memory
- Notify outbox (`GET /v1/internal/notify-outbox`) — events emitted on transitions; no real send
- Media GET returns bytes (or `DEMO_MEDIA_STUB`); not S3 signed URLs
- No Postgres / real object storage
- Optional `cancelled` before check-in is in the §2 diagram but **no REST cancel** in §4 (not implemented)
- Priority P2 blast / geo-fence / new zone keys / dual-approve / face match / MSR — **not implemented**
- Production / live-school deploy — **HOLD**
