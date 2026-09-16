#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:8080}"
echo "== health =="
curl -sf "$BASE/health" | python3 -m json.tool

echo "== login gate =="
LOGIN=$(curl -sf -X POST "$BASE/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"gate","password":"gate123"}')
TOKEN=$(echo "$LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
echo "token ok"

echo "== gates =="
curl -sf "$BASE/v1/gates" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d["data"]), "gates")'

echo "== pass P-4F21 =="
curl -sf "$BASE/v1/passes/P-4F21" -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["visitorName"], d["passId"], d["status"])'

echo "== blacklist match =="
curl -sf -X POST "$BASE/v1/blacklist/match" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"mobile":"9876500001"}' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["hit"]["id"] if d["hit"] else None)'

echo "== blast preview (SH) =="
SHLOGIN=$(curl -sf -X POST "$BASE/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"security","password":"sh123"}')
SH=$(echo "$SHLOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
curl -sf "$BASE/v1/emergency/blasts/preview?templateId=T-EVAC-01" \
  -H "Authorization: Bearer $SH" | python3 -c 'import sys,json; d=json.load(sys.stdin); print("inside", d["insideCount"])'

echo "SMOKE OK"
