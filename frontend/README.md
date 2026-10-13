<div align="center">

# 💻 SecretVault Web Dashboard

[![React](https://img.shields.io/badge/React-18.3-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-5.4-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3.4-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)

**The mission-control web interface for the SecretVault enterprise DevSecOps platform.**

</div>

---

## 🌟 Overview

The **SecretVault Frontend** is a high-performance Single Page Application (SPA) providing an intuitive, polished operations dashboard for developers, security engineers, and DevOps administrators.

It offers real-time secret inspection, multi-cloud synchronization management, drift detection, tamper-evident audit ledger analysis, and an embedded **AI Security Copilot Workbench**.

---

## ⚡ Key Capabilities & Views

| View Component | Purpose & Features |
|---|---|
| **`WorkspaceOverview.jsx`** | Multi-tenant overview displaying project cards, environment status, active secret counts, and quick actions. |
| **`SecretListView.jsx`** | Comprehensive secret inventory with version history, rollback controls, masking, and Step-Up MFA reveal dialogs. |
| **`SyncCenterView.jsx`** | Multi-cloud sync operations: manual sync triggers, real-time status indicators, retry queues, and drift detection. |
| **`RiskCenterView.jsx`** | Interactive security health matrix: risk heatmaps, blast radius graphs, and posture score (0–100) analytics. |
| **`SecurityOverviewView.jsx`** | High-level executive dashboard showing active vulnerability findings, aging credentials, and compliance readiness. |
| **`AuditLogsView.jsx`** | Tamper-evident SHA-256 audit explorer with cryptographic chain verification and SOC 2 export capabilities. |
| **`AiCopilotView.jsx`** | Conversational DevSecOps copilot interface with streaming responses (SSE), zero-plaintext guarantees, and suggested prompt chips. |
| **`AiRemediationWorkbenchView.jsx`** | Four-Eyes approval console for high-risk AI remediation plans with diff previews and Step-Up MFA signing. |
| **`GitHubSettingsView.jsx`** | Repository security integration, push webhook configuration, automated branch scans, and leak finding triage. |
| **`AccessControlView.jsx`** | Role-Based Access Control management, member invitations, and Just-In-Time (JIT) elevation requests. |

---

## 🚀 Performance & Architecture

- **Code Splitting & Lazy Loading:** Core views are dynamically imported with `React.lazy()` and wrapped in custom skeleton `Suspense` boundaries for near-instant Initial Page Load (FCP < 800ms).
- **Parallel Data Hydration:** Dashboard modules execute parallel API queries via `Promise.allSettled`, preventing blocking waterfall requests.
- **Fail-Safe HTTP Client:** `apiClient.js` provides automatic token refreshing, exponential backoff retries, and comprehensive error mapping.
- **Zero-Plaintext In-Browser Guarantee:** Decrypted secrets are kept strictly in transient component memory during reveal sessions and cleared on navigation.

---

## 🛠️ Local Development

### 1. Install Dependencies
```bash
npm install
```

### 2. Configure Environment
Create a `.env.local` file:
```env
VITE_API_BASE_URL=http://localhost:3000/api/v1
```

### 3. Start Development Server
```bash
npm run dev
```
The application will launch at `http://localhost:5173`.

### 4. Run Test Suite
```bash
npm test
```

### 5. Production Build
```bash
npm run build
```
Generates an optimized, tree-shaken static production bundle in `dist/`.
