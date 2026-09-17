# Satcop Smart Visitor — Host approve (mobile web)

Phone-first (~390px) host surface matching `demos/host-approve.html`. No Play Store install.

**Not for live school deploy.** DEMO watermark stays on.

## What it does

- **Host sign-in** — Viren types a host username + password. There is **no** auto-login as demo `host` / `host123` (Anita Joshi).
- Session lives in `sessionStorage` (`satcop-host-token`, `satcop-host-user`). Reload restores that typed session only.
- Host label comes from the JWT `displayName` + `schoolId` (e.g. **Pranay Host · SCH-PRANAY-01**).
- **Sign out** clears the session and returns to the login screen.
- Shows a pending visitor request: name, type, purpose, gate, time IST, mobile
- **Approve** → `POST /v1/visits/{id}/approve` → “visitor on the way”
- **Reject** → reason required → `POST /v1/visits/{id}/reject`
- **Meeting done** when the visit is `inside` → `POST /v1/visits/{id}/meeting-done`
- Tabs: **Pending**, **After-hours** (`#afterhours` / Ravi `V-AH-VENDOR` FYI — host cannot approve), **Priya (inside)** (`V-20260916-014` / `P-4F21`)
- Amber **FIXTURES** toast when the tunnel has no matching visit or times out (~6s AbortController)

## Open locally

```bash
cd host-web
python3 -m http.server 8767
# http://127.0.0.1:8767/
# http://127.0.0.1:8767/#afterhours
# http://127.0.0.1:8767/#pending
# http://127.0.0.1:8767/#priya
```

On a phone viewport the chrome goes full-bleed. On desktop you get the dark phone shell.

## API

Living mock (typed login only):

`https://pensions-usb-loops-direction.trycloudflare.com/v1`

School: **SCH-PRANAY-01**. Viren signs in as `pranay.host` / `PranayHost@2026`. `config.js` has **no** `hostUser` / `hostPass`.

If the tunnel 502s or CORS/network fails after a typed session, tabs fall back to local fixtures so approve/reject/meeting-done still click.

Edit `config.js` if the tunnel URL changes.

## After-hours FYI (AC-C4d)

Open **After-hours**. Ravi Deshmukh `V-AH-VENDOR` stays pending Security Head. Sticky rows: `policyTrigger`, `afterHoursEvaluatedAt`, Decision = Security Head only. Tap **Approve** — toast/hint `AFTER_HOURS_SH_REQUIRED` · still pending · never Approved. Pending / Priya approve flow is unchanged.

## Out of scope

Guard patrol, production SSO, real PII. Gate camera / scan lives in `kiosk/`. Visitor badge is `visitor-qr/`. Backend `app/` is not changed here.
