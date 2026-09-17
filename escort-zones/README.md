# Escort / Zones — Gate glance (Access Rules B4)

Static Hub surface mirroring `demos/access-rules/escort-zones.html`. Live mock when the tunnel answers; Ravi / Vikram / zone fixtures otherwise.

**Not for live school deploy.** DEMO watermark stays on. Soft process only — **no geo-fence**.

## Open

```bash
cd escort-zones
python3 -m http.server 8771
# http://127.0.0.1:8771/
# http://127.0.0.1:8771/#assign
# http://127.0.0.1:8771/#stamps
# http://127.0.0.1:8771/#pass
# http://127.0.0.1:8771/#audit
# http://127.0.0.1:8771/#editor
# http://127.0.0.1:8771/?story=escort   # Load P-7K88 · Vikram + chips
```

From repo root (all static surfaces): `python3 -m http.server 8770` → `/escort-zones/`, `/afterhours-gate/`, `/host-web/`, `/visitor-qr/`.


| Who | Role |
|-----|------|
| **Ravi Deshmukh** `+91 98765 44021` | Vendor · A/C maintenance — Block B |
| **Anita Joshi** `H03` | Host (FYI / optional escort suggest) |
| **Meera Kulkarni** | Security Head (waive with reason) |
| **Vikram More** `E01` | Escort staff (Gate assigns) |
| Pass | Glance paints **P-7K88** escort + zone chips (never demo pass). **Load P-7K88** assigns Vikram More / `E01` and opens pass chrome. Live visit id / assign stays in the footnote. After-hours Deepak `GET /passes/P-7K88` is unchanged — badge uses `?passId=P-7K88&story=escort` to paint chips from fixtures when live lacks escort. |

## Click-path

1. **Gate escort assign** — picker is **E01 Vikram More only** (from `GET /v1/staff?active=true`, fixtures match). `selectedStaffId` defaults to **E01**. Never send `E02` / `E03`. Live: `POST /v1/visits/{id}/assign-escort` `{ escortStaffId: "E01" }` as `pranay.gate` / `PranayGate@2026`.
2. Pick **Vikram More · E01** → Issue pass enabled. Pass chrome (story 3 or **Load P-7K88**) shows **P-7K88** + Escort: Vikram More + Reception / Lobby + Admin block.
3. **Load P-7K88** — one-click demo story: assign live `E01` when the visit is real, else paint chips from fixtures. Or `?story=escort`.
4. **Open visitor badge** → `visitor-qr/?passId=P-7K88&story=escort` (holiday `?passId=P-7K88` without `story` stays Deepak).
5. **SH waive** — Security Head only (`pranay.sh` / `PranaySH@2026`). Reason required. Body is `{ reason }` only — no staff id. Gate → 403.
6. **Admin audit** — escort + zones + waive columns (AC-B4c / AC-B4f).
7. **Rules editor** — fixed zone keys, school-renamable labels, Vendor escort ON. Live save as `pranay.sh` / `PranaySH@2026` (`PUT /v1/access-rules/escort`, `PATCH /v1/zones/{key}`).

Gate cannot waive (403 `FORBIDDEN`). Security Head waive: `POST /v1/visits/{id}/waive-escort` `{ reason }` (AC-B4e / B4-E3). Check-in without escort returns `ESCORT_REQUIRED`.

If SH approve returns `INVALID_STATE`, the after-hours visit is already `inside`. Use a **pending** after-hours seed or register fresh — that is not an escort assign bug.

## API

`https://replacing-spyware-yes-due.trycloudflare.com/v1`

Docs: `https://replacing-spyware-yes-due.trycloudflare.com/docs` · OpenAPI **v0.5.0**

Reads: `GET /v1/access-rules/escort`, `GET /v1/zones`, `GET /v1/staff`, `GET /v1/visits?visitorType=Vendor`, `GET /v1/passes/{passId}`.

## Out of scope

