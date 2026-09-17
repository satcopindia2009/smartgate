# After-hours / holiday — Gate glance (Access Rules C4)

Static Hub surface mirroring `demos/access-rules/afterhours.html`. Live mock when the tunnel answers; Ravi / Deepak / hours fixtures otherwise.

**Not for live school deploy.** DEMO watermark stays on. Escort / zones are out of this slice.

## Open

```bash
./scripts/serve-demos.sh
# or:
cd afterhours-gate
python3 -m http.server 8769
# http://127.0.0.1:8769/
# http://127.0.0.1:8769/#gate
# http://127.0.0.1:8769/#host
# http://127.0.0.1:8769/#sh
# http://127.0.0.1:8769/#holiday
# http://127.0.0.1:8769/#hours
```

From repo root (all static surfaces): `python3 -m http.server 8770` → `/afterhours-gate/`, `/host-web/`, `/visitor-qr/`, `/pickup-gate/` (P2 pointer).

## Stories

| Story | Visitor | Host | Flag | Pass |
|-------|---------|------|------|------|
| Evening Vendor | **Ravi Deshmukh** `+91 98765 44021` · `V-AH-VENDOR` | Anita Joshi **H03** (FYI) | `afterHours` · `outside_hours` · Pending SH | — |
| Holiday Parent | **Deepak Nair** `+91 98220 98802` | Meera Kulkarni **H01** | `afterHours` · `holiday` | **P-7K88** |

Weekday close **18:00** Asia/Kolkata (close exclusive). Sat 08:00–13:00. Sun closed. Holiday **Diwali 2026-10-20**.

## Click-path

1. **Gate banner** — high-vis “After hours / holiday — Admin or Security Head approval required”. Self-approve blocked.
2. **Host FYI** — Anita. Tap **Approve** → stays pending (`AFTER_HOURS_SH_REQUIRED` live, or fixture no-op). Ack FYI optional. AC-C4d.
3. **SH decision** — Meera / `security` · `sh123`. **Reason required** for Approve and Reject (A6). Buttons stay disabled until a reason is present. This page is a glance — no Admin hours editor.
4. **Holiday pass** — Deepak → P-7K88 + `holiday` chip (AC-C4b).
5. **Hours + holidays** — read-only calendar from `GET /v1/access-rules/hours` + `/holidays` (or fixtures).

## API

`https://replacing-spyware-yes-due.trycloudflare.com/v1`  
Docs: `https://replacing-spyware-yes-due.trycloudflare.com/docs` · OpenAPI **v0.5.0**

Gate `pranay.gate` / `PranayGate@2026` · Host `pranay.host` / `PranayHost@2026` · SH `pranay.sh` / `PranaySH@2026` (reason required).

## Out of scope

SH Admin hours editor (Admin desk). Escort / zones. Demo seed only — no Priya fixtures.
