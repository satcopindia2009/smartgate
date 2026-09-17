# Satcop Smart Visitor — Visitor QR pass (mobile web)

Read-only visitor badge matching `demos/visitor-qr.html`. No app install.

**Not for live school deploy.** DEMO watermark stays on.

## What it shows

- Pass id **P-4F21**, visitor **Priya Sharma**, host **Anita Joshi**, gate **Main Gate**
- Status banner from visit lifecycle (`pending` / `approved` / `inside` / `completed`)
- QR encodes the opaque `qrToken` (not `passId` alone)
- `GET /v1/passes/{passId}` when the mock tunnel is up (~6s timeout → FIXTURES)
- On FIXTURES, tap the status banner to cycle banners for the demo

## Open locally

```bash
cd visitor-qr
python3 -m http.server 8768
# http://127.0.0.1:8768/
# http://127.0.0.1:8768/?passId=P-4F21
```

## Light / dark theme

Same tokens as host-web / Admin (`tokens-admin.css`). Header **Light / Dark** toggle; persists `localStorage` key `satcop-theme` = `light` | `dark`. Default is `prefers-color-scheme` when unset.

The mock currently requires a Bearer token for pass GET. This page logs in with the **demo gate** account (`gate` / `gate123`) only to read the badge — visitors would get a public/signed link later.

If the tunnel is down, fixtures render the Priya / P-4F21 / `V-20260916-014` story (token is a demo placeholder).

QR rendering uses [qrcodejs](https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js); if the CDN is blocked, a CSS fallback shows a token snippet.

## Out of scope

No edit actions, no scan check-in from this page (gate kiosk owns `POST /v1/passes/scan`). No production deploy.
