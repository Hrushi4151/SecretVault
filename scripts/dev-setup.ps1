Write-Host "=== SecretVault Developer Environment Setup (PowerShell) ===" -ForegroundColor Cyan

if (-not (Test-Path ".env")) {
    Write-Host "Creating .env from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
}

Write-Host "Starting local PostgreSQL and Redis containers..." -ForegroundColor Green
docker compose up -d postgres redis

Write-Host "Building backend..." -ForegroundColor Green
mvn -f backend/pom.xml clean compile

Write-Host "=== Setup complete! ===" -ForegroundColor Cyan
Write-Host "Run backend with: mvn -f backend/pom.xml spring-boot:run" -ForegroundColor Yellow
