# Day-1 — Admin Web Dashboard

**Date:** 16 Sep 2026  
**Surface:** Satcop Smart Visitor admin web (MVP)  
**Hard lock:** no production, no live school (V4 HOLD)

## Shipped

1. Vite + React 18 + TypeScript + React Router app in `admin/`
2. Dark theme copied from `demos/shared.css`
3. JWT auth to Backend `/v1` (`POST /auth/login`, `GET /auth/me`)
4. Live who’s-inside matching `admin-whos-inside.html`
5. Gate multi-select + type / host / flag / search filters
6. Auto-refresh every 30s
7. Force checkout with required reason → `POST /visits/{id}/force-checkout`
8. Directory: `GET /gates`, `GET /staff?active=true`
9. Default API tunnel: `https://weed-pumps-laura-upc.trycloudflare.com/v1` (fixtures fallback if it drops)
10. History + Blacklist (SH write) + Gates/Reports + CSV export (4 scopes)
11. Multi-gate enums: Main Gate, Pedestrian Gate, Staff Gate, Bus Bay

## Walkthrough

Priya Sharma is inside at Main Gate, pass `P-4F21`, host Anita Joshi (Primary Coordinator). Force-checkout requires a reason and removes her from Live.

## Out

Blast, access-rules. Pickup Admin CRUD/history is a later P2 wire (`/pickup`, `/pickup-history`) and does not change visit Live/History. `kiosk/` and Mobile paths untouched.
