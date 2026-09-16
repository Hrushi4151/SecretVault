# SecretVault — Premium Visual Design System for Stitch

## 1. DESIGN DIRECTION

Design SecretVault as a premium, futuristic DevSecOps SaaS control plane.

The visual language should combine:

- Apple-style refinement
- iOS/macOS glassmorphism
- Linear's precision
- Vercel's minimalism
- Cloudflare's infrastructure feel
- Datadog's operational density
- Modern enterprise security software

The product must feel:
- Premium
- Calm
- Secure
- Technical
- Minimal
- High-end
- Trustworthy
- Extremely polished

Avoid making it look like a generic admin dashboard.

The UI should feel like a premium operating system for application secrets and infrastructure.

---

# 2. CORE VISUAL PRINCIPLE

Use a layered glass architecture.

Background
→ ambient light
→ glass surfaces
→ borders
→ content
→ floating controls

Do NOT make every component look like a completely transparent glass card.

Use different levels of glass:

### Level 1 — Primary Glass

Used for:
- Sidebar
- Top navigation
- Main cards
- Floating panels
- Command palette

Properties:
- Semi-transparent background
- Strong backdrop blur
- Thin translucent border
- Soft shadow
- Slightly elevated from background

### Level 2 — Secondary Glass

Used for:
- Table containers
- Filter bars
- Secondary panels
- Activity panels
- Integration cards

Properties:
- Less transparency
- Less blur
- More solid surface
- Subtle border

### Level 3 — Solid Surface

Used for:
- Inputs
- Selects
- Dropdowns
- Code blocks
- Secret value fields
- Dense table rows
- Important controls

These should remain highly readable and should NOT be excessively transparent.

---

# 3. COLOR SYSTEM

## DARK MODE — PRIMARY EXPERIENCE

Dark mode should be the hero experience.

### Base Background

Main:
`#05070A`

Secondary:
`#080B10`

Elevated:
`#0D1117`

Surface:
`rgba(255,255,255,0.055)`

Surface Strong:
`rgba(255,255,255,0.085)`

Surface Hover:
`rgba(255,255,255,0.11)`

### Glass

Glass background:
`rgba(255,255,255,0.06)`

Strong glass:
`rgba(255,255,255,0.09)`

Glass border:
`rgba(255,255,255,0.10)`

Strong border:
`rgba(255,255,255,0.16)`

### Text

Primary:
`#F5F7FA`

Secondary:
`#A7AFBC`

Muted:
`#707986`

Disabled:
`#4B5563`

### Brand Accent

Primary accent:
`#7C5CFF`

Secondary accent:
`#9B82FF`

Subtle accent background:
`rgba(124,92,255,0.12)`

Accent glow:
`rgba(124,92,255,0.20)`

Use purple very selectively.

The interface must remain mostly neutral.

---

# 4. LIGHT MODE

Background:
`#F5F7FA`

Secondary background:
`#EEF1F5`

Card:
`rgba(255,255,255,0.72)`

Strong card:
`rgba(255,255,255,0.88)`

Border:
`rgba(15,23,42,0.08)`

Strong border:
`rgba(15,23,42,0.13)`

Primary text:
`#111827`

Secondary text:
`#5B6472`

Muted:
`#8A93A1`

Brand:
`#6D4AFF`

---

# 5. SEMANTIC COLORS

These colors communicate system state.

## Success

Primary:
`#34D399`

Background:
`rgba(52,211,153,0.10)`

Border:
`rgba(52,211,153,0.20)`

Use for:
- Synced
- Healthy
- Successful
- Resolved

## Warning

Primary:
`#FBBF24`

Background:
`rgba(251,191,36,0.10)`

Border:
`rgba(251,191,36,0.20)`

Use for:
- Warning
- Drift
- Rotation due
- Retry

## Critical

Primary:
`#F87171`

Background:
`rgba(248,113,113,0.10)`

Border:
`rgba(248,113,113,0.20)`

Use for:
- Critical risks
- Leaks
- Failed operations
- Destructive actions

## Information

Primary:
`#60A5FA`

Background:
`rgba(96,165,250,0.10)`

Use for:
- Informational events
- AI insights
- Provider information

Do not overuse semantic colors.

---

# 6. BACKGROUND EFFECTS

Use extremely subtle ambient gradients behind the interface.

Dark mode:

- Purple glow near top-left
- Blue/purple glow near top-right
- Very subtle radial gradients
- Large blurred light sources

The background should never become colorful.

Example visual concept:

black/charcoal background
+
very faint purple light
+
very faint blue light
+
glass panels

Avoid strong neon effects.

---

# 7. GLASSMORPHISM

Use Apple-inspired glass surfaces.

Recommended CSS characteristics:

```text
background: rgba(255,255,255,0.055)
backdrop-filter: blur(24px) saturate(140%)
border: 1px solid rgba(255,255,255,0.10)
box-shadow:
  0 10px 40px rgba(0,0,0,0.18)
```

For floating elements:

```text
backdrop-filter: blur(32px) saturate(150%)
```

Use stronger blur for:
- Command palette
- Dropdowns
- Modals
- Floating notifications
- Sidebar
- Topbar

Do not use excessive blur on large data tables.

---

# 8. BORDER STYLE

Borders should be extremely subtle.

Dark mode:
`rgba(255,255,255,0.08)`

Hover:
`rgba(255,255,255,0.14)`

Active:
`rgba(124,92,255,0.35)`

Critical:
`rgba(248,113,113,0.30)`

Avoid thick borders.

The design should rely on:
- Contrast
- Blur
- Shadows
- Spacing

rather than heavy outlines.

---

# 9. CORNER RADIUS

Use generous Apple-style rounded corners.

## Small

Inputs:
`10px`

Small badges:
`8px`

## Medium

Buttons:
`10–12px`

Dropdowns:
`12px`

Table containers:
`14px`

## Large

Cards:
`16px`

Panels:
`18px`

Modals:
`20px`

Large dashboard containers:
`20–24px`

## Very Large

Hero sections:
`24–28px`

Avoid making everything extremely rounded.

---

# 10. SHADOW SYSTEM

Dark mode shadows should be soft.

Small:

```text
0 4px 16px rgba(0,0,0,0.15)
```

Medium:

```text
0 10px 30px rgba(0,0,0,0.20)
```

Large:

```text
0 20px 60px rgba(0,0,0,0.30)
```

Floating:

```text
0 24px 80px rgba(0,0,0,0.35)
```

Light mode should use lighter shadows.

Never use harsh black shadows.

---

# 11. TYPOGRAPHY

Primary font:
Inter / Geist-style sans-serif.

Technical font:
JetBrains Mono / SF Mono-style monospace.

### Page title

32px
Weight 600
Letter spacing -0.03em

### Section title

20–24px
Weight 600

### Card title

14–16px
Weight 600

### Body

14px
Line height 1.5

### Small text

12px

### Secret names

13–14px
Monospace
Medium weight

### IDs / hashes

12px
Monospace

Typography should feel compact and premium.

---

# 12. SPACING SYSTEM

Use an 8px base system.

4px
8px
12px
16px
20px
24px
32px
40px
48px
64px

Dashboard sections:
24–32px gap.

Card internal padding:
20–24px.

Dense table:
12–16px vertical padding.

Do not create huge empty spaces.

---

# 13. BUTTON DESIGN

Buttons should feel like native premium software controls.

## Primary

Dark:
- Purple/white accent
- High contrast
- Subtle glow on hover
- 10–12px radius

Example:
`+ Add Secret`

## Secondary

Glass:
- Transparent
- Thin border
- Backdrop blur
- White/neutral text

## Ghost

No visible background until hover.

## Danger

Use restrained red.

Never make every destructive action bright red.

### Button dimensions

Small:
32px height

Default:
36–40px

Large:
44–48px

Padding:
12–16px

---

# 14. INPUT DESIGN

Inputs should be solid-glass hybrid components.

Dark:

```text
background: rgba(255,255,255,0.045)
border: 1px solid rgba(255,255,255,0.09)
```

Focus:
- Purple border
- Very subtle purple glow

Example:

```text
┌─────────────────────────────────┐
│ Search secrets...            ⌕ │
└─────────────────────────────────┘
```

Radius:
10–12px.

Height:
40–44px.

---

# 15. SECRET INPUT

Secret input should look especially secure.

Example:

```text
┌──────────────────────────────────────────┐
│ ••••••••••••••••••••••••••••••      👁 │
└──────────────────────────────────────────┘
```

Use:
- Monospace
- Masked dots
- Reveal icon
- Copy icon only when appropriate
- Security tooltip

Never expose secret values automatically.

---

# 16. CARDS

Cards should feel like translucent physical surfaces floating over the background.

Dark example:

```text
┌────────────────────────────────────────┐
│ Security Score                         │
│                                        │
│ 87 / 100                     ↗ +5      │
│                                        │
│ Excellent security posture             │
└────────────────────────────────────────┘
```

Properties:
- 16–20px radius
- 20–24px padding
- Thin glass border
- Soft shadow
- Subtle hover elevation

Hover:
- Slightly brighter border
- 1–2px visual elevation
- Very subtle accent glow where appropriate

---

# 17. KPI CARDS

Dashboard KPI cards should be compact.

Include:
- Small icon
- Label
- Large number
- Trend
- Supporting text

Example:

Security Score
87/100
+5.2%
Excellent

Avoid giant dashboard numbers.

---

# 18. SIDEBAR

The sidebar should be one of the strongest glass surfaces.

Dark mode:
- Slightly lighter than background
- Backdrop blur
- Right-side border
- Rounded outer edge if floating

Structure:

```text
┌──────────────────────┐
│ ◈ SecretVault        │
│                      │
│ WORKSPACE            │
│  Dashboard           │
│  Projects            │
│  Secrets             │
│  Integrations        │
│  Sync Center         │
│                      │
│ SECURITY             │
│  Security Overview   │
│  Risk Center         │
│  Secret Leaks        │
│  Drift Detection     │
│  Audit Logs          │
│                      │
│ AI                   │
│  AI Assistant        │
│  AI Analysis         │
│                      │
│ ORGANIZATION         │
│  Team                │
│  Settings            │
│                      │
│ ──────────────────── │
│ User                 │
└──────────────────────┘
```

Active item:
- Soft purple translucent background
- Purple/white icon
- Slight left accent or glow
- Rounded 9–10px

Inactive:
- Muted text

---

# 19. TOPBAR

Topbar:
- Transparent/glass
- Sticky
- Backdrop blur
- Very subtle bottom border

Contains:
Organization switcher
Project switcher
Search
Notifications
Profile

Height:
56–64px.

---

# 20. ORGANIZATION SWITCHER

Use a glass dropdown.

Show:
- Organization icon
- Organization name
- Current workspace
- Chevron

Dropdown:
- Search
- Organization list
- Create organization

---

# 21. ENVIRONMENT SWITCHER

Use compact segmented control.

Example:

`Development | Staging | Production`

Production should have subtle visual importance but not aggressive red styling.

Selected:
- Glass/purple background
- High contrast

---

# 22. STATUS BADGES

Use compact pill badges.

Example:

Synced

Green icon + text
Very subtle green translucent background.

Warning:
Amber

Failed:
Red

Disconnected:
Muted gray

Do not use huge pills.

---

# 23. RISK BADGES

Critical:
Red translucent background

High:
Orange/red

Medium:
Amber

Low:
Blue/gray

Risk badges should communicate severity instantly.

---

# 24. TABLE DESIGN

Tables should feel premium and extremely readable.

Container:
- Glass surface
- 14–18px radius
- Thin border
- No heavy grid lines

Header:
- Small uppercase or compact text
- Muted color

Rows:
- 52–60px height
- Very subtle separators
- Hover background

Example:

```text
┌─────────────────────────────────────────────────────┐
│ SECRET NAME      TYPE     VERSION   RISK    STATUS │
├─────────────────────────────────────────────────────┤
│ DATABASE_URL     DB       v5        Low     Synced │
│ JWT_SECRET       Token    v3        Med     Synced │
│ STRIPE_KEY       API      v7        High    Drift  │
└─────────────────────────────────────────────────────┘
```

Use monospace for secret names.

---

# 25. MODALS

Modals should look like floating Apple-style glass panels.

Properties:
- 20px radius
- Strong backdrop blur
- Dark translucent surface
- Thin border
- Large soft shadow
- Background dimming

Backdrop:
black with approximately 45–60% opacity.

Modal width:
400–600px depending on content.

Destructive confirmation:
Clearly communicate consequences.

---

# 26. DRAWERS

Use right-side glass drawers for:
- Audit details
- Secret activity
- Sync details
- Notifications

Width:
360–480px.

Use smooth slide-in animation.

---

# 27. DROPDOWNS

Dropdown menus:
- Glass
- Blur
- 12px radius
- Soft shadow
- Clear hover states

Menu item height:
36–40px.

Icons aligned consistently.

---

# 28. TOASTS

Toasts should appear as small floating glass cards.

Examples:

Success:
"Secret created successfully"

Sync:
"Production sync completed"

Warning:
"3 secrets require rotation"

Error:
"Vercel sync failed"

Use semantic icon + short message + optional action.

---

# 29. NOTIFICATION CENTER

Use a glass floating panel.

Sections:
- Security
- Sync
- Team
- System

Unread notifications:
- Small purple/blue indicator
- Slightly brighter background

---

# 30. COMMAND PALETTE

This should feel extremely premium.

Open with:
Ctrl/Cmd + K

Full floating glass panel centered near top.

Example:

```text
┌─────────────────────────────────────────┐
│ ⌕ Search SecretVault...                 │
├─────────────────────────────────────────┤
│ QUICK ACTIONS                           │
│                                         │
│ + Create secret                         │
│ + Create project                        │
│ ↻ Sync production                       │
│ ⚠ View security risks                   │
│ ✦ Ask AI                                │
└─────────────────────────────────────────┘
```

Use strong blur and subtle glow.

---

# 31. DASHBOARD VISUAL HIERARCHY

Dashboard should have:

Top:
Page title + quick actions

Row 1:
Security Score
Projects
Secrets
Connected Platforms

Row 2:
Large Sync Health panel
Security Issues panel

Row 3:
Recent Activity
Security Trends / AI Insights

Do not make every section the same size.

Create visual hierarchy.

---

# 32. SECURITY SCORE

Use a sophisticated circular/ring indicator.

Example:

87

/100

Excellent

Ring should be mostly neutral with a subtle accent/semantic segment.

Avoid rainbow gradients.

---

# 33. INTEGRATION CARDS

Each provider card:

```text
┌────────────────────────────────────┐
│ [Provider Logo]          Connected │
│                                    │
│ Vercel                             │
│ Deployment platform                │
│                                    │
│ 4 projects     Last sync 2m ago   │
│                                    │
│ [ Manage ]              [ Sync ]  │
└────────────────────────────────────┘
```

Use provider brand logos but keep surrounding UI neutral.

---

# 34. SYNC PROGRESS

Use thin elegant progress bars.

Example:

Production Sync

██████████████████░░ 87%

42 of 48 secrets synchronized

Avoid oversized progress graphics.

---

# 35. ACTIVITY TIMELINE

Timeline should be compact.

Each event:
- Small icon
- Event title
- Actor
- Timestamp
- Result

Example:

● Secret rotated
  DATABASE_URL
  2 minutes ago

● Sync completed
  Vercel / Production
  4 minutes ago

---

# 36. AI UI STYLE

AI should feel integrated into the infrastructure product.

Do NOT make AI look like a generic chatbot.

Use:
- Compact AI icon
- Glass response cards
- Evidence sections
- Related resources
- Recommended actions

Example:

AI Analysis

"Production deployment failure is likely related to
a stale environment variable."

Confidence
92%

Evidence
3 related events

[View Sync] [Inspect Secret]

AI should be calm and analytical.

---

# 37. AI CHAT

Chat interface:

User messages:
- Slightly stronger surface

AI responses:
- Transparent/glass
- Structured sections

Avoid huge chat bubbles.

AI response structure:

Summary
Evidence
Risk
Recommended Action

---

# 38. SECURITY FINDING CARDS

Each finding should show:

Severity
Title
Reason
Affected resource
Detected time
Recommended action

Example:

CRITICAL

Potential secret exposure

STRIPE_SECRET_KEY
Repository: payments-api

Detected 12 minutes ago

[Investigate]

---

# 39. LEAK INVESTIGATION VISUAL STYLE

This page should feel like a security investigation console.

Top:
Critical severity banner

Main:
Finding details

Side:
Risk summary

Bottom:
Timeline + remediation

Use red only where necessary.

Do not make the entire page red.

---

# 40. DRIFT VISUALIZATION

Use a comparison UI.

```text
SecretVault
DATABASE_URL
Version v5

        ≠

Vercel
DATABASE_URL
Version v4
```

Show:
Expected
Observed
Difference
Action

Primary action:
Sync latest

---

# 41. AUDIT LOG DESIGN

Audit logs should feel enterprise-grade.

Use a dense table with:
- Timestamp
- Actor
- Action
- Resource
- Result

Use small icons.

Successful events should remain neutral/green.
Failures use subtle red.

---

# 42. SETTINGS DESIGN

Use a settings sidebar inside the page.

Example:

Settings

General
Security
Authentication
Sessions
API Keys
Notifications
Billing
Danger Zone

Main content appears as grouped glass cards.

---

# 43. DANGER ZONE

Do NOT make the entire settings page red.

Use:
- Neutral page
- Dedicated danger section
- Thin red border
- Soft red background
- Clear consequence text

Example:

Danger Zone

Delete organization

This permanently deletes all projects,
secrets, integrations, and audit history.

[Delete Organization]

---

# 44. EMPTY STATES

Empty states should be elegant and minimal.

Example:

No integrations connected

Connect your first deployment platform
to synchronize application secrets.

[Connect Integration]

Use subtle illustration/iconography.

No cartoonish illustrations.

---

# 45. ERROR STATES

Example:

Something went wrong

We couldn't load your production secrets.

[Try Again]

Technical details
Request ID: req_8f72...

Technical information should be collapsible.

---

# 46. LOADING STATES

Use skeletons instead of generic spinners wherever possible.

Skeleton:
- Slight glass shimmer
- Rounded rectangles
- Correct layout dimensions

Avoid excessive animated loading.

---

# 47. MICRO-INTERACTIONS

Use subtle animations.

Button hover:
100–150ms

Cards:
150–200ms

Dropdown:
150ms

Modal:
180–250ms

Page transitions:
200–300ms

Use:
- opacity
- transform
- blur
- subtle scale

Do not use bouncing animations.

---

# 48. HOVER EFFECTS

Cards:
Very subtle elevation.

Buttons:
Slight brightness.

Rows:
Slight background increase.

Icons:
Subtle accent.

Never make hover effects flashy.

---

# 49. FOCUS STATES

Keyboard focus must be visible.

Use:
- 1px accent border
- subtle accent ring

Example:
`0 0 0 3px rgba(124,92,255,0.15)`

Do not remove browser accessibility focus without replacing it.

---

# 50. MOBILE DESIGN

Mobile should preserve the premium aesthetic.

Use:
- Bottom navigation or compact navigation drawer
- Glass header
- Cards
- Bottom sheets
- Horizontal scrolling tabs
- Stacked sections

Important mobile navigation:
Dashboard
Secrets
Sync
Security
AI

Secret values remain masked.

---

# 51. DESKTOP LAYOUT

Recommended max content width:
1400–1600px.

Main content:
24–32px horizontal padding.

Sidebar:
240–260px.

Topbar:
56–64px.

Dashboard grid:
12-column responsive grid.

---

# 52. GLASS EFFECT RULE

Use glass where it adds depth.

Recommended:
- Sidebar = glass
- Topbar = glass
- Cards = glass
- Modals = strong glass
- Dropdowns = strong glass
- Command palette = strong glass
- Tables = lighter glass
- Inputs = semi-solid
- Code blocks = solid
- Secret values = solid
- Dense technical content = solid

Do NOT turn every pixel into glass.

---

# 53. APPLE-STYLE REFINEMENT

The Apple-inspired aspect should come from:

- Excellent spacing
- Soft surfaces
- Subtle translucency
- Smooth animations
- Strong typography
- Minimal controls
- Clear hierarchy
- Quiet visual design
- High-quality icon alignment
- Rounded geometry

Do NOT copy Apple's branding or UI literally.

---

# 54. ICONOGRAPHY

Use simple modern line icons.

Preferred style:
- 1.5–2px stroke
- Rounded line caps
- Consistent 16–20px size

Icons:
- Lucide-style
- Minimal
- Technical

Do not mix many icon styles.

---

# 55. LOGO / BRAND

SecretVault logo should communicate:
- Security
- Infrastructure
- Centralized control

Possible visual direction:
A minimal geometric shield/lock combined with a node/network motif.

Avoid:
- Generic padlock clipart
- Complex gradients
- Overly detailed logos

---

# 56. PAGE BACKGROUND

The page background should remain visible behind glass.

Dark:
Almost-black charcoal.

Add extremely subtle:
- Purple radial glow
- Blue radial glow
- Soft noise/grain if appropriate

Keep the effect at very low opacity.

---

# 57. SCROLLBARS

Use minimal custom scrollbars.

Dark mode:
Subtle gray thumb.

Track:
Transparent.

Do not make scrollbars visually dominant.

---

# 58. DATA DENSITY

This is a developer/security product.

Prioritize information over decoration.

Use:
- Compact rows
- Clear metadata
- Inline status
- Filters
- Search
- Tooltips
- Expandable details

But preserve enough whitespace to avoid visual fatigue.

---

# 59. SECURITY VISUAL LANGUAGE

Security should feel calm and authoritative.

Healthy:
Quiet green.

Warning:
Controlled amber.

Critical:
Focused red.

Do not make every security screen alarming.

A critical issue should stand out because everything else is restrained.

---

# 60. FINAL STITCH GENERATION INSTRUCTION

Generate the complete SecretVault interface using this design system consistently across every page.

All pages must share:
- Same colors
- Same typography
- Same spacing
- Same glass surfaces
- Same border treatment
- Same corner radius
- Same button system
- Same status badges
- Same table system
- Same modal system
- Same sidebar
- Same topbar
- Same animation language

The final visual result should feel like:

"Apple-level polish + Linear-level product design + Vercel-level developer experience + Cloudflare/Datadog-level infrastructure tooling."

The product should look expensive, trustworthy, modern, technically sophisticated, and ready for production customers.

PRIMARY AESTHETIC:
Dark premium glassmorphism.

SECONDARY AESTHETIC:
Clean light-mode glassmorphism.

Use translucency carefully.
Use blur carefully.
Use purple accent carefully.
Use semantic colors only for meaning.

The interface must remain highly readable, highly functional, and security-focused.

Never sacrifice usability for visual effects.
