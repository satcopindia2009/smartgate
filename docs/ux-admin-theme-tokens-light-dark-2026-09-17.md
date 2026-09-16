# Satcop Smart Visitor — Admin theme tokens (light + dark)

**Stamp:** 2026-09-17 ~01:33 IST · Hub order (Viren visual direction)  
**Product:** Satcop Smart Visitor / Smart Gate — **never** vizmo branding  
**Scope:** IMPLEMENTATION on real Admin web (`admin/` on PR #1). Not a Viren design-review pack.  
**Product locks / flows / fields:** unchanged.

## Vibe (reference only)

Clean school admin: optional dark sidebar, teal accents, card stats, simple table, lots of whitespace. School-friendlier than the ref: **larger taps**, **fewer columns/fields**, **clearer hierarchy**, less chrome.

## Rules

1. Light + Dark themes with a simple toggle; **persist** preference (`localStorage` key `satcop-theme` = `light` | `dark`).
2. Default: follow `prefers-color-scheme` if no stored preference; else stored.
3. Apply Admin first: Live who’s-inside, History, Blacklist, Force-checkout modal, Gates (+ Reports if already in shell).
4. Branding: **Satcop Smart Visitor** / **Smart Gate** only.
5. After Admin preview works both themes → host-web / visitor QR; gate Compose tokens later.

## Token file

Implement as CSS variables on `:root` (light) and `[data-theme="dark"]` / `.theme-dark` (dark). Prefer `data-theme` on `<html>`.

Canonical CSS: `ux/theme/tokens-admin.css` (also attached to cloud agent).

### Accent

| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `--accent` | `#0d9488` | `#2dd4bf` | Primary buttons, active nav, links |
| `--accent-hover` | `#0f766e` | `#5eead4` | Hover |
| `--accent-muted` | `rgba(13,148,136,0.12)` | `rgba(45,212,191,0.15)` | Soft fills, active row tint |
| `--danger` | `#dc2626` | `#f87171` | Overstay, force-checkout, errors |
| `--danger-muted` | `rgba(220,38,38,0.12)` | `rgba(248,113,113,0.15)` | Soft alert fills |
| `--success` | `#16a34a` | `#4ade80` | Checked-in / OK |
| `--warning` | `#d97706` | `#fbbf24` | Caution banners |

### Surfaces

| Token | Light | Dark |
|-------|-------|------|
| `--bg` | `#f3f4f6` | `#0f1115` |
| `--sidebar` | `#0f172a` | `#15171c` |
| `--sidebar-text` | `#e2e8f0` | `#e5e7eb` |
| `--sidebar-muted` | `#94a3b8` | `#9ca3af` |
| `--card` | `#ffffff` | `#1a1d24` |
| `--border` | `#e5e7eb` | `#2a2e38` |
| `--text` | `#0f172a` | `#f3f4f6` |
| `--text-muted` | `#6b7280` | `#9ca3af` |
| `--table-header` | `#f8fafc` | `#15171c` |
| `--row-hover` | `#f0fdfa` | `#1f232c` |

### Density (school-friendly)

- Min tap / button height: **44px**
- Table row min-height: **52px**
- Font base: **15–16px** (Inter / system-ui)
- Radius: `--radius` 10px · cards 14px
- Sidebar width: ~220px; collapse to icons OK on narrow

### Toggle UX

- Place theme toggle in Admin header (sun/moon or “Light / Dark”).
- Switching must restyle Live, History, Blacklist, Gates, Force-checkout modal without reload.
- Persist immediately on toggle.

## Out of scope this PR

- Do not add vizmo name/logo/copy
- Do not change Product screen inventory, fields, or flows
- Do not send Viren a design deck — refresh preview URL only
- Phase 1 HTML demos stay frozen visual contract for MVP stories; this restyle is the **live Admin app**

## Done when

- [ ] Light + dark both correct on Admin preview URL
- [ ] Preference persists across reload
- [ ] Live / History / Blacklist / Force-checkout / Gates look school-simple
- [ ] `npm run build` still passes
- [ ] Hub pinged with refreshed preview URL
