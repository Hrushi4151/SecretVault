# SecretVault — Local Development Setup & Execution Guide

## 1. Prerequisites

- **Java JDK 21 LTS** (Temurin / Oracle JDK 21)
- **Apache Maven 3.9+**
- **Docker & Docker Compose**
- **Git**

---

## 2. Quickstart Step-by-Step

### 1. Configure Local Environment
```bash
# Copy template to .env (NEVER commit .env)
cp .env.example .env
```

### 2. Start PostgreSQL & Redis Infrastructure
```bash
# Run local supporting containers in background
docker compose up -d postgres redis

# Verify health
docker compose ps
```

### 3. Build & Run the Backend
```bash
# Compile and start Spring Boot app
cd backend
mvn spring-boot:run
```

The server listens on `http://localhost:8080`.

### 4. Verify Local Health & Swagger
- **Public Baseline Health:** `http://localhost:8080/api/v1/health`
- **Actuator Health Probe:** `http://localhost:8080/actuator/health`
- **OpenAPI Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## 3. Useful Developer Commands

```powershell
# Run backend tests
mvn -f backend/pom.xml test

# Package executable JAR
mvn -f backend/pom.xml package -DskipTests

# Connect to local PostgreSQL via psql in Docker
docker exec -it secretvault-postgres psql -U vault_user -d secretvault_dev

# Connect to local Redis CLI in Docker
docker exec -it secretvault-redis redis-cli ping
```
