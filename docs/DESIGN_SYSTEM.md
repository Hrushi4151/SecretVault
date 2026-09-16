# SecretVault — Design System & Stitch Source of Truth

## 1. Visual Source of Truth

The `stitch_secretvault_devsecops_platfor/` directory is the **primary visual reference and definitive source of truth** for the SecretVault user interface.

> **Mandatory Rule:** Under no circumstances should any developer or AI coding agent redesign the SecretVault UI from scratch or introduce arbitrary templates.

---

## 2. Source-of-Truth Priority

When implementing or evolving frontend interfaces, decisions must adhere to this hierarchy:
1. **Security and correctness** (No secret exposure, strict authorization)
2. **Existing SecretVault architecture** (Modular monolith, REST contracts)
3. **Stitch designs in `stitch_secretvault_devsecops_platfor/`**
4. **Established SecretVault design system tokens**
5. **Reusable component patterns**
6. **Individual developer preference**

---

## 3. Core Visual Language & Design Tokens

- **Aesthetic:** Dark-first DevSecOps control plane with Apple/iOS-inspired glassmorphism and Linear-level micro-polish.
- **Surfaces:** Translucent panels, subtle borders (`border-white/10` or `border-zinc-800`), backdrop blur (`backdrop-blur-md`), and soft ambient drop shadows.
- **Corner Radius:** 10px to 24px controlled curves for cards, dialogs, and interactive controls.
- **Palette:**
  - **Base Background:** Neutral charcoal/zinc (`bg-zinc-950` / `bg-zinc-900`).
  - **Primary Brand Accent:** Purple / Indigo (`#7C3AED` / `#8B5CF6`).
  - **Semantic States:** Red (Alert / Critical / Expired), Amber (Warning / Drifted / Pending), Green (Synced / Healthy / Active) — strictly semantic, never decorative.
- **Typography:**
  - **UI / Headings / Labels:** Inter / Geist Sans.
  - **Technical Values (Keys, Hashes, Tokens, Commands):** JetBrains Mono / Geist Mono.
- **Icons:** Lucide Icons.
- **Information Density:** High density, data-rich tables, clean metadata strips, and minimal visual noise.

---

## 4. Reusable Component Taxonomy

Do NOT implement each Stitch screen as an isolated monolith. Extract and compose using shared design system components:

```text
frontend/src/components/
├── layout/
│   ├── AppShell.tsx
│   ├── Sidebar.tsx
│   ├── TopBar.tsx
│   ├── WorkspaceSwitcher.tsx
│   ├── ProjectSwitcher.tsx
│   └── Breadcrumbs.tsx
├── common/
│   ├── PageHeader.tsx
│   ├── StatusBadge.tsx
│   ├── SeverityBadge.tsx
│   ├── EmptyState.tsx
│   ├── LoadingState.tsx
│   ├── ErrorState.tsx
│   ├── Modal.tsx
│   └── ConfirmationDialog.tsx
├── secret/
│   ├── SecretTable.tsx
│   ├── SecretRow.tsx
│   ├── SecretValueMasked.tsx
│   ├── SecretRevealModal.tsx
│   ├── SecretVersionTimeline.tsx
│   └── SecretDiffViewer.tsx
├── security/
│   ├── SecurityFindingCard.tsx
│   ├── RiskScoreCard.tsx
│   ├── BlastRadiusGraph.tsx
│   └── ThreatTimeline.tsx
├── integration/
│   ├── ProviderCard.tsx
│   ├── PlatformMappingRow.tsx
│   ├── SyncStatusBadge.tsx
│   └── SyncJobTimeline.tsx
└── ui/ (shadcn/ui primitives: button, input, dialog, dropdown, tabs, tooltip)
```

---

## 5. Screen Implementation Workflow

For each screen implemented according to the project roadmap:
```text
Inspect Stitch Folder
  └── Understand Visual Hierarchy & Interactions
        └── Check / Build Reusable Components
              └── Connect Real Backend API
                    └── Add Loading, Error, Empty & Auth States
                          └── Verify Responsiveness (Desktop/Tablet/Mobile)
                                └── Security & Masking Audit
```

---

## 6. Prohibited Anti-Patterns

- ❌ Generic admin dashboard templates (AdminLTE, Bootstrap styles).
- ❌ Neon cyberpunk or overly saturated decorative themes.
- ❌ Plaintext secret rendering by default.
- ❌ Replacing designed Stitch layouts with basic HTML tables.
- ❌ Hardcoded mock data when real backend APIs exist.
