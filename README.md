# Satcop Smart Visitor — MVP API Stub

In-memory FastAPI mock implementing `mvp-api-contract-2026-09-16.md` §§0–5 under `/v1`.  
For Mobile / Admin **showable demos**. **Not for live school deploy** (V4 HOLD).

Day-1 routes are hardened for visit state machine, host-scoped approve, pass scan errors, blacklist §5 match, media keys, and the contract error envelope.

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
- Staff includes **Anita Joshi** (`H03`)
- Walkthrough pass: **Priya Sharma** → passId **`P-4F21`** → status `inside` (Main Gate)
- Approved ready for scan: **Kiran Desai** → **`P-C101`** → status `approved` (Main Gate)
- Blacklist: `BL-01` Vikram More (Block), `BL-02` Neha Salunkhe (Alert)
- Pending visit for host demo: `V-20260916-040` (host H03)

## Visit lifecycle (contract §2)

```
pending → approved → inside → completed
       ↘ rejected
inside → force_completed   (Admin/SH; body.reason required)
```

| Action | From | Notes |
|--------|------|--------|
| `POST /visits` | → `pending` | Gate (or SH override create). Blacklist Block without override → `BLACKLIST_BLOCK` |
| `POST /visits/{id}/approve` | `pending` → `approved` | Host **own visits only** (`hostId == staffId`). Issues `passId` + `qrToken` |
| `POST /visits/{id}/reject` | `pending` → `rejected` | `{ "reason": "..." }` required (non-blank) |
| `POST /visits/{id}/check-in` or `POST /passes/scan` `check_in` | `approved` → `inside` | Sets `timeIn`, `gateInId` |
| `POST /visits/{id}/check-out` or scan `check_out` | `inside` → `completed` | `checkoutType=normal` |
| `POST /visits/{id}/force-checkout` | `inside` → `force_completed` | `{ "reason": "..." }` required |
| `POST /visits/{id}/meeting-done` | `approved` \| `inside` | Sets `meetingDoneAt`; **status unchanged** |

Illegal transitions return `409` `{ "error": { "code": "INVALID_STATE" } }`.

## Auth matrix (MVP)

| Action                         | gate | host | admin | security_head |
|--------------------------------|:----:|:----:|:-----:|:-------------:|
| Create / check-in / out / scan | ✓    |      |       | ✓ create w/ Block override |
| Approve / reject own + meeting-done | | ✓* | ✓ | ✓ |
| Force checkout                 |      |      | ✓     | ✓             |
| Staff POST/PATCH               |      |      | ✓     | ✓             |
| Blacklist write                |      |      | view  | ✓             |
| Live board / history           | ✓    | own* | ✓     | ✓             |

\* Host scoped to `hostId == self.staffId`.  
Gate users are limited to `user.gateIds` (login + JWT). `GET /gates` is filtered to assigned gates.

## Pass / media / blacklist

- `POST /v1/passes/scan` — `{ "token" | "passId", "action": "check_in" | "check_out", "gateId"? }`
  - unknown → `PASS_NOT_FOUND` (404)
  - revoked → `PASS_REVOKED` (410)
  - expired → `PASS_EXPIRED` (410)
  - wrong status → `INVALID_STATE` (409)
- `GET /v1/passes/{passId}` — badge: name, photo URL, host, gate, passId
- `POST /v1/media/upload` — multipart `file` + `kind` = `live_photo` \| `id_image` \| `signature` \| `other` → `{ key, url }`
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

## Remaining thin stubs / out of scope

OK to stay thin (not blocking Mobile/Admin demos):

- Reports (`/v1/reports/*`) — simple in-memory aggregates; `medianApprovalSec` is null
- Exports (`POST /v1/exports`) — sync CSV ≤ current store; audit row kept in memory
- Notify outbox (`GET /v1/internal/notify-outbox`) — events emitted on transitions; no real send
- Media GET returns bytes (or `DEMO_MEDIA_STUB`); not S3 signed URLs
- No Postgres / real object storage
- Optional `cancelled` before check-in is in the §2 diagram but **no REST cancel** in §4 (not implemented)
- Priority P2 (pickup, after-hours, escort/zones, blast, face match, MSR) — **not implemented**
- Production / live-school deploy — **HOLD**
