# Local Development with SecretVault CLI

The primary objective of SecretVault Phase 10 is empowering developers to run local applications without persisting plaintext secrets in unencrypted `.env` files.

---

## The Zero-Plaintext Workflow (Recommended)

Traditionally, developers maintain `.env` files on disk that frequently get committed accidentally to git repositories or inspected by malware.

SecretVault CLI replaces this with **Runtime In-Memory Injection**:

```
Developer Workstation
         │
         ▼
  secretvault run -- npm start
         │
         ├─► Decrypts secrets directly in RAM
         ├─► Passes secrets into child process environment block
         ├─► Child process runs with access to process.env
         └─► Process exits → RAM references zeroed
```

---

## Step-by-Step Local Setup

### 1. Link Your Git Repository
In the root directory of your project, run:

```bash
secretvault dev init --project payment-gateway --environment development
```

This creates a lightweight `.secretvault/project.json` file in your directory containing metadata (workspace, project, and environment slugs).

> [!NOTE]
> `.secretvault/project.json` contains **only non-sensitive metadata**. It is safe to commit to version control.

---

### 2. Verify Available Secrets

Check which secrets are configured for this environment:

```bash
secretvault secret list
```

---

### 3. Launch Your Local Server

Execute your regular build tool or runner prefixed with `secretvault run --`:

#### Node.js / React / Next.js
```bash
secretvault run -- npm run dev
```

#### Spring Boot / Maven
```bash
secretvault run -- mvn spring-boot:run
```

#### Python / Flask / Django / FastAPI
```bash
secretvault run -- python3 main.py
```

#### Golang
```bash
secretvault run -- go run main.go
```

#### Docker Compose
```bash
secretvault run -- docker compose up
```

---

## Signal Handling & Exit Codes

- When you press `Ctrl+C` in your terminal, the SecretVault CLI intercepts the `SIGINT` signal, forwards it cleanly to the child process, and waits for graceful shutdown.
- When the child process exits with an error code (e.g. exit code 1 or 42), `secretvault run` exits with the exact same code, making it fully compatible with developer automation and scripts.
