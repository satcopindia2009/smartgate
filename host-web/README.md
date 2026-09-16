# Satcop Smart Visitor — Host approve (mobile web)

Phone-first (~390px) host surface matching `demos/host-approve.html`. No Play Store install.

**Not for live school deploy.** DEMO watermark stays on.

## What it does

- Shows a pending visitor request: name, type, purpose, gate, time IST, mobile
- **Approve** → `POST /v1/visits/{id}/approve` → “visitor on the way”
- **Reject** → reason required → `POST /v1/visits/{id}/reject`
- **Meeting done** when the visit is `inside` → `POST /v1/visits/{id}/meeting-done`
- Demo story tabs: pending (Anita’s queue) and **Priya Sharma** (`V-20260916-014`, already inside on the mock)

## Open locally

```bash
cd host-web
python3 -m http.server 8767
# http://127.0.0.1:8767/
```

On a phone viewport the chrome goes full-bleed. On desktop you get the dark phone shell.

## API

Prefers the ephemeral mock:

`https://weed-pumps-laura-upc.trycloudflare.com/v1`

Logs in as demo host `host` / `host123`. If the tunnel 502s or CORS/network fails, the page falls back to local fixtures (Priya / Demo Pending Parent) and still lets you click approve/reject/meeting-done offline.

Edit `config.js` if the tunnel URL changes.

## Out of scope

Guard patrol, production SSO, real PII. Gate camera lives in `kiosk/`. Visitor badge is `visitor-qr/`.
