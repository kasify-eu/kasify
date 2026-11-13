# Kasify Dashboard — Design System Spec

A lightweight reference for the visual language of the Kasify dashboard (the authenticated app under `app/routes/dashboard`). Use this as a brief when working with a design assistant.

> Marketing site (`app/routes/site/*`) is a separate, isolated theme (`.kasify-site`) with its own tokens and is **not** covered here.

---

## 1. Foundations

### Stack
- **UI library:** Mantine v8 (`primaryColor: "gray"`, forced light mode)
- **Utility CSS:** Tailwind v4 (slate-based palette)
- **Icons:** `@tabler/icons-react` (stroke 1.5–2)
- **Module CSS:** per component, in `*.module.css`

### Typography
- **Sans:** `Inter`, weights 400 / 500 / 600 / 700 / 800
- **Mono:** `JetBrains Mono`, weights 500 / 600 — used for access codes, timestamps, badges/eyebrows on the marketing site
- **Global letter-spacing:** `0.02em` on `body`
- **Font smoothing:** `-webkit-font-smoothing: antialiased`, `text-rendering: optimizeLegibility`
- **Mobile:** root font-size bumped to `18px` under `48em`

**Type scale (effective):**

| Token | Size | Weight | Use |
|---|---|---|---|
| `xs` | 0.75rem (12px) | 500/600 | Field labels (`#727272`), meta, captions |
| `sm` | 0.875rem (14px) | 500/600/700 | Body, input text, list rows |
| `md` | ~1rem (16px) | 600/700 | Section labels, card titles |
| `lg` | 1.125rem (18px) | 700 | ContentHeader breadcrumb active |
| `xl/Title 3` | 1.25rem+ | 700 | Empty state titles, page headings |

Form labels are explicitly styled: **`0.75rem / 500 / #727272`** with no bottom padding. Form inputs are **`44px` tall, `0.875rem`, padding `10px 12px`**.

---

## 2. Color System

### 2.1 Neutral / Slate palette (canvas, ink, borders)

This is the dashboard's spine. All values are slate-based and used heavily across layouts, menus, cards.

| Token | Hex | Where it's used |
|---|---|---|
| Canvas / App background | `#F8FAFC` | `AppShell.Main` background, drawer header bg |
| Surface (white) | `#FFFFFF` | Sidebar, cards, modals, code cards |
| Hover / subtle row | `#F1F5F9` | Nav hover & active, user-profile hover, mobile drawer hover |
| Hover (warmer) | `#F7F7F7` | Activity item hover background |
| Border / divider | `#E2E8F0` | Sidebar right border, mobile nav top border |
| Border (softer) | `#E5E7EB` | Timeline group separator line |
| Border (slate-100) | `#F1F5F9` | Property card border |
| Muted text / icon | `#64748B` | Nav idle, secondary text, icons |
| Sub-muted | `#94A3B8` | Empty-state hints, breadcrumb separator |
| Body 2 | `#374151` | Time stamps, secondary headers |
| Body 1 | `#334155` | Body text |
| Ink (heading) | `#0F172A` | Nav active text, breadcrumb active, property name |
| Ink 2 (near-black) | `#323232` | Activity item primary text, secondary button hover bg |
| **Action / Primary ink** | **`#1A1A1A` → `#000000` on hover** | Primary buttons, secondary button border |

### 2.2 Primary action color (MAIN — most important)

The dashboard does **not** use a blue/branded primary. The primary affordance is **near-black on white**.

```
--primary:        #1A1A1A   /* MainButton default + SecondaryButton border */
--primary-hover:  #000000   /* MainButton primary hover */
--primary-text:   #FFFFFF
```

`SecondaryButton` is an outline variant with `#1A1A1A` border that inverts on hover (bg `#323232`, text `#FFFFFF`).

`MainButton` variants:

| Variant | bg | text | hover bg |
|---|---|---|---|
| `primary` | `#1A1A1A` | `#FFFFFF` | `#000000` |
| `default` | `oklch(0.975 0.004 270)` | `oklch(0.38 0.01 270)` | `oklch(0.955 0.004 270)` |
| `ghost` | `transparent` | `oklch(0.55 0.008 270)` | `oklch(0.975 0.004 270)` |
| `danger` | `oklch(0.58 0.18 25)` (≈ red) | `#FFFFFF` | mix(red, black 15%) |

### 2.3 Brand accent (mobile only)

A warm orange shows up only on the **mobile bottom-nav active item**:

```
--accent-orange: #EC5B13
```

(There is no top-level use of this color in the desktop dashboard chrome.)

### 2.4 Semantic / status palette (activity timeline + status states)

Each activity type has a matched icon-foreground + soft background:

| Type | Foreground | Background |
|---|---|---|
| `check_in` (success) | `#10B981` (emerald-500) | `#ECFDF5` |
| `check_out` (warn/exit) | `#F43F5E` (rose-500) | `#FFF1F2` |
| `current_guest` | `#6366F1` (indigo-500) | `#EEF2FF` |
| `cleaning` | `#F59E0B` (amber-500) | `#FFFBEB` |
| `maintenance` | `#3B82F6` (blue-500) | `#EFF6FF` |
| `default` / unknown | `#6B7280` (gray-500) | `#F3F4F6` |

Other semantic tones used across cards/toasts:

| State | Light bg | Border | Text |
|---|---|---|---|
| Success | `#D1FAE5` / `bg-emerald-100` | `border-green-100` | `#047857` / `text-green-700` |
| Warning | `#FEF3C7` (amber-100) | `border-orange-100` | `#B45309` (amber-700) |
| Danger | `#FEE2E2` / `#FECACA` / `bg-red-50` | `border-red-200` | `#DC2626` / `text-red-700` |
| Info | `#DBEAFE` / `bg-blue-50` | `border-blue-100` | `#1D4ED8` / `text-blue-700` |

### 2.5 Channel brand colors

Used in `ChannelBadge` and the details drawer:

| Channel | Color |
|---|---|
| Airbnb | `#FF5A5F` (icon) / `#FF385C` (CSS var) |
| Booking.com | `#003580` |
| Other / world | `text-blue-600` |

---

## 3. Layout

### Shell
- **Sidebar** (`AppShell.Navbar`): width **`288px`**, white bg, right border `1px solid #E2E8F0`, full-viewport height, hidden on mobile (`breakpoint: "sm"`)
- **Main area** (`AppShell.Main`): bg `#F8FAFC`, top padding `24px`, horizontal padding `xl` on `sm+`, none on base, bottom padding `80px` mobile (clears mobile nav) / `xl` desktop
- **Mobile nav** (`MobileNav`): fixed bottom bar, `rgba(255,255,255,0.95)` with `backdrop-filter: blur(12px)`, top border `#E2E8F0`, safe-area-inset aware

### Content header
- `ContentHeader` row is **`72px` tall**, breadcrumb-based
- Breadcrumb separator: `IconChevronRight 14px #94A3B8`
- Active crumb: `lg / 700 / #0F172A`
- Inactive crumb: `lg / #64748B`, link

### Grid & spacing
- Spacing scale: Mantine defaults (`xs`, `sm`, `md`, `lg`, `xl`) — most padding is in `12 / 16 / 20 / 24 / 28px` increments
- Card content padding: `20px 24px` (property card), `12px 14px` (booking-detail rows), `28px` (modals)

### Radii (consistent system)

| Token | Px | Use |
|---|---|---|
| sm | `8–10px` | Buttons, mobile drawer items, small chips |
| md | `12px` | Nav links, user profile, cards, code cards, modal action buttons |
| lg | `14–20px` | Booking detail modals, content cards |
| xl | `24px` | Property cards |
| pill | `999px` | Badges, status dots, avatars, progress rails |

### Shadows
The dashboard is largely **flat** — relies on 1px slate borders, not shadows. Subtle `shadow-sm` (Tailwind) used on booking list cards. (The marketing site uses heavier shadows — different theme.)

---

## 4. Components (key visual rules)

### Sidebar nav link
- `12px 16px` padding, `12px` radius, `0.95rem / 500`, color `#64748B`
- Icon `22×22`, stroke `1.5`, color `#64748B`
- **Hover & active:** bg `#F1F5F9`, text `#0F172A`, active is also `font-weight: 600`
- Active state set via `data-active` attribute (matches path or path prefix)

### User profile block (sidebar bottom)
- Avatar `md / radius xl / color blue`, with initials
- Name `0.875rem / 600 / #0F172A`, truncated
- Email `0.75rem / #64748B`, truncated
- `padding: 16px`, `border-radius: 12px`, hover bg `#F1F5F9`
- Separated from nav by `1px solid #E2E8F0` top border

### MainButton (primary CTA)
- Radius `10px` (lg size: `12px`), font `13px / 600`, letter-spacing `-0.005em`
- Padding: `8px 14px` (sm) / `11px 16px` (lg), `height: auto`
- Icon gap inside button: `7px`
- Transitions: `background-color 0.2s ease, color 0.2s ease`

### SecondaryButton
- Mantine `variant="outline"`, `border-color: #1A1A1A`, `color: #1A1A1A`, weight `600`, `letter-spacing: 0.04em`
- Hover: bg `#323232`, text `#FFFFFF`

### Property card
- White surface, `border-radius: 24px`, `1px solid #F1F5F9`
- Image area `180px` tall, falls back to gradient placeholder `linear-gradient(135deg, #E2E8F0 0%, #F1F5F9 100%)` with `IconHome` in `#94A3B8`
- Content padding `20px 24px`, title `1rem / 700 / #0F172A`, location `0.75rem / #64748B` with `IconMapPin 14px`

### Activity timeline item
- White card, `12px` radius, padding `sm / md` (8/16px), `margin-bottom: 10px`
- Hover: bg `#F7F7F7`, text `#111827`
- Type **badge** with type color bg and `IconType 12px / white / stroke 2.5`
- Primary text `sm / 700 / #323232`, secondary `xs / dimmed`, time `sm / 600 / #374151`
- Group date label: `md / 700 / #374151 / letter-spacing 0.3px`, then offset label (Today / Tomorrow / etc.) plus an absolute date `xs / #9CA3AF`, then a `1px #E5E7EB` divider line

### Access code card (booking details)
- Grid `34px 1fr auto`, gap `12px`, white bg, `1px solid var(--mantine-color-gray-3)`, radius `12px`
- Code digits: `JetBrains Mono / 600 / 17px / letter-spacing 0.18em / color var(--mantine-color-dark-9)`
- Meta row uses small dot separators (`3×3px`, `--mantine-color-gray-5`)

### Modal
- Surface white, radius `20px`, padding `28px`
- Close (X): `32×32`, pill, `--mantine-color-gray-6`, hover bg `--mantine-color-gray-1`
- Modal action buttons: `height: 44px`, `flex: 1`, radius `12px`
- Danger modal icon disc: `48×48 / pill / --mantine-color-red-0 bg / --mantine-color-red-6 fg`

### Mobile bottom nav
- Item: column layout, `6px 12px` padding, label `0.625rem / 600 / uppercase / letter-spacing 0.05em`
- Idle color `#94A3B8`, hover `#0F172A`, **active `#EC5B13`** (the only place this orange appears in chrome)

### Drawer (details)
- Header bg `#F8FAFC`, padding `18px 22px 16px`, bottom border `var(--mantine-color-gray-3)`
- Title: `22px / 700 / letter-spacing -0.025em / dark-9`, truncated
- Kicker (eyebrow): `11px / 600 / uppercase / letter-spacing 0.1em / gray-6` with `2×2` dot separators (`gray-5`)
- Channel badge: `40×44 / radius 12px / 1px gray-3 border`, with 22px icon in brand color

---

## 5. Mantine theme overrides (root)

```ts
createTheme({
  primaryColor: "gray",
  fontFamily: '"Inter", system-ui, sans-serif',
  components: {
    TextInput | Select | PasswordInput | NumberInput | Textarea | DateInput: {
      label: { fontSize: "0.75rem", fontWeight: 500, color: "#727272" },
      input: { height: "44px", fontSize: "0.875rem", padding: "10px 12px" },
    },
  },
})
```

Color scheme is hard-locked: `forceColorScheme="light"`, `defaultColorScheme="light"` on both `ColorSchemeScript` and `MantineProvider`. No dark mode.

Tables (Mantine): `.mantine-Table-tr { border: none }`, `.mantine-Table-td { padding: 1rem 0.75rem }`.

---

## 6. Iconography
- Library: `@tabler/icons-react`
- Default stroke: `1.5` for nav/large, `2`–`2.5` for inline/badge icons
- Common sizes: `12 / 14 / 16 / 18 / 20 / 22 / 48px`
- Main nav set: `IconCalendar`, `IconCalendarMonth`, `IconHome`, `IconTicket`, `IconUsers`, `IconChecklist`, `IconApps`, `IconLogout`, `IconSettings`

---

## 7. Quick-reference token cheat sheet

```css
/* Surfaces */
--bg-canvas:    #F8FAFC;
--bg-surface:   #FFFFFF;
--bg-hover:     #F1F5F9;

/* Text */
--text-ink:     #0F172A;
--text-body:    #334155;
--text-muted:   #64748B;
--text-subtle:  #94A3B8;
--text-label:   #727272;

/* Borders */
--border:       #E2E8F0;
--border-soft:  #F1F5F9;

/* Primary action (near-black) */
--action:       #1A1A1A;
--action-hover: #000000;
--action-fg:    #FFFFFF;

/* Mobile accent */
--accent:       #EC5B13;

/* Activity / status */
--success:      #10B981;  --success-bg: #ECFDF5;
--warning:      #F59E0B;  --warning-bg: #FFFBEB;
--danger:       #F43F5E;  --danger-bg:  #FFF1F2;
--info:         #3B82F6;  --info-bg:    #EFF6FF;
--guest:        #6366F1;  --guest-bg:   #EEF2FF;

/* Radii */
--radius-sm: 10px;
--radius-md: 12px;
--radius-lg: 20px;
--radius-xl: 24px;

/* Typography */
--font-sans: "Inter", system-ui, sans-serif;
--font-mono: "JetBrains Mono", monospace;
```

---

## 8. Design principles (observed)

1. **Flat, slate-based, light-only.** No dark mode, very few shadows, 1px slate borders do the structural work.
2. **Near-black is the primary action.** No blue/branded CTAs in the dashboard chrome; the only chrome accent is the mobile-nav orange (`#EC5B13`).
3. **Color is reserved for meaning.** Status and activity types are color-coded; UI structure stays neutral.
4. **Generous radii.** Cards `20–24px`, buttons `10–12px`, badges fully pill — gives the product a soft, modern feel.
5. **Typography does the hierarchy.** Weight (`500 → 600 → 700`) and color (`#94A3B8 → #64748B → #334155 → #0F172A`) carry most of the rank; sizes change less.
6. **Mantine + Tailwind coexist.** Mantine handles structure/forms/modals; Tailwind utility classes used for list rows and one-off layouts (notably in `bookings/` and `tasks/`).
