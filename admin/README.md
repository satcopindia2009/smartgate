# Satcop Smart Visitor — Admin Web Dashboard (Day-1)

Vite + React 18 + TypeScript + React Router. Dark theme from `demos/shared.css`.  
JWT to Backend `/v1`. **Fixtures fallback** if the API is down.  
**Not for production. No live school gates (V4 HOLD).**

Walkthrough story: **Priya Sharma** → pass **`P-4F21`** → **Main Gate** → host **Anita Joshi**.

## Day-1 notes

### Must (this slice)

- Login: `admin` / `admin123` (Office Admin), `security` / `sh123` (Security Head)
- Live who’s-inside board matching `admin-whos-inside.html`
- Gate **multi-select** (Main / Pedestrian / Staff / Bus Bay)
- Filters: search (name, mobile last 4, pass), type, host, flags (overdue / blacklist)
- Auto-refresh **≤ 30s**
- **Force checkout** — reason required; Confirm disabled until `trim(reason)`; visit leaves the board
- Multi-gate enums locked: Main Gate, Pedestrian Gate, Staff Gate, Bus Bay
- Wire: `POST /auth/login`, `GET /auth/me`, `GET /gates`, `GET /staff?active=true`, `GET /visits/inside`, `POST /visits/{id}/force-checkout` `{ reason }`

### Stretch (included)

- History (default today, host/gate/type/decision/checkout filters, 90-day max, checkout type Force after force-checkout)
- Blacklist (view for Admin; write for Security Head)
- Gates + Reports (today-by-gate, range-by-gate, type mix)
- CSV export (history / inside / blacklist / daily gate summary) + demo audit row

### Out of this slice

- Emergency blast
- Production deploy, live school, kiosk / gate tablet pickup

## Pickup & custody (Priority P2)

Admin/Security Head surfaces only — no visit-flow or kiosk changes.

- **Students & lists** (`/pickup`): authorized pickup CRUD + custody flag editor (`none` / `restricted` / `court_order` + `gate_instruction` ≤280). No court PDF upload. Office Admin cannot set `court_order`.
- **Pickup history** (`/pickup-history`): searchable proof trail (student, collector, relation, gate, status, override) + purpose-required CSV.
- Wire: `GET /students`, `GET|POST /students/{id}/authorized-pickup`, `PATCH .../authorized-pickup/{personId}`, `GET|PUT /students/{id}/custody-flag`, `GET /pickups`.
- Seed: **Aarav Mehta · Class 5-B** with **Neha Mehta (Mother)** + **Rohan Mehta (Uncle/Relative)**; **Kabir Singh** `court_order` demo. Fixtures fallback if the `/v1` tunnel is down.

## After-hours / holidays (Priority P2 · Hub A1–A6 / AC-C4)

Same Admin light/dark tokens — no alternate layout pack. Escort/zones and blast stay out.

- **Hours + holidays** (`/access-rules`): 7-day campus hours + holiday calendar CRUD. Admin / Security Head write; Gate cannot. Policy copy locked **Security Head only** (not dual). Wire: `GET|PUT /access-rules/hours`, `GET|POST /access-rules/holidays`, `DELETE /access-rules/holidays/{id}`.
- **Live / History**: `afterHours` + `policyTrigger` flag and filters (After-hours / Holiday / Pending SH). SH Approve/Reject with reason on pending after-hours; Host Approve is a no-op on the API. Sticky eval at registration.
- Seed: weekday close **18:00 Asia/Kolkata**; holiday **Diwali 2026-10-20 `HOL-DIWALI`**; Evening Vendor **Ravi Deshmukh `V-AH-VENDOR`**; Holiday Parent **Deepak Nair / pass `P-7K88`**. **Priya Sharma `P-4F21` unchanged.**

## Escort / zones (Priority P2 · B4)

Same Access Rules page. Keys fixed; labels school-renamable. Admin / Security Head write.

- `GET /zones` · `PATCH /zones/{key}` `{ label }`
- `GET|PUT /access-rules/escort` (Vendor default escort ON · reception + admin)
- Live / History escort name + allowed-zones columns (Priya: escort no · reception)

## Run

```bash
# optional: API stub from repo root
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8080

# admin UI
cd admin
cp .env.example .env   # VITE_API_BASE_URL=https://pensions-usb-loops-direction.trycloudflare.com/v1
npm install
npm run dev            # http://127.0.0.1:5173
npm run build          # tsc --noEmit && vite build
npm run preview        # http://127.0.0.1:4173
```

Override API with `VITE_API_BASE_URL` in `.env` if needed. Default (and `.env.example`) is the showable Cloudflare stub.

If the API tunnel is unreachable, demo logins still work against bundled `public/data/admin-mvp-fixtures.json`.

Default `VITE_API_BASE_URL` is `https://pensions-usb-loops-direction.trycloudflare.com/v1`. `vite preview` allows tunnel hosts (`allowedHosts: true`) so a temporary `*.trycloudflare.com` can be shown to Hub/Viren.

## Roles

| Action            | Admin | Security Head |
|-------------------|:-----:|:-------------:|
| Live / History    | yes   | yes           |
| Force checkout    | yes   | yes           |
| Blacklist write   | view  | yes           |
| Pickup list CRUD  | yes   | yes           |
| Custody `court_order` | no | yes           |
| Hours / holiday write | yes | yes           |
| After-hours Approve | no | yes           |

Gate / Host accounts are rejected at login on this surface.

## Acceptance (Day-1)

- **AC-L1** Board shows only inside visits; poll ≤ 30s
- **AC-L2** Force checkout requires reason; row leaves Live; History checkout type **Force**
