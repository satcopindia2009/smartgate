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

- History (default today, filters, checkout type Force after force-checkout)
- Blacklist (view for Admin; write for Security Head)

### Out of this slice

- Emergency blast, pickup/custody, access-rules (after-hours / escort zones)
- CSV export audit UI, production deploy, live school

## Run

```bash
# optional: API stub from repo root
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8080

# admin UI
cd admin
cp .env.example .env   # VITE_API_BASE_URL=https://weed-pumps-laura-upc.trycloudflare.com/v1
npm install
npm run dev            # http://127.0.0.1:5173
npm run build && npm run preview   # http://127.0.0.1:4173          # tsc --noEmit && vite build
```

If the API tunnel is unreachable, demo logins still work against bundled `public/data/admin-mvp-fixtures.json`.

Default `VITE_API_BASE_URL` is `https://weed-pumps-laura-upc.trycloudflare.com/v1`. `vite preview` allows tunnel hosts (`allowedHosts: true`) so a temporary `*.trycloudflare.com` can be shown to Hub/Viren.

## Roles

| Action            | Admin | Security Head |
|-------------------|:-----:|:-------------:|
| Live / History    | yes   | yes           |
| Force checkout    | yes   | yes           |
| Blacklist write   | view  | yes           |

Gate / Host accounts are rejected at login on this surface.

## Acceptance (Day-1)

- **AC-L1** Board shows only inside visits; poll ≤ 30s
- **AC-L2** Force checkout requires reason; row leaves Live; History checkout type **Force**
