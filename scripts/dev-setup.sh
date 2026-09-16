#!/usr/bin/env bash
set -e

echo "=== SecretVault Developer Environment Setup ==="

if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
fi

echo "Starting local PostgreSQL and Redis containers..."
docker compose up -d postgres redis

echo "Building backend..."
cd backend
mvn clean compile

echo "=== Setup complete! ==="
echo "Run backend: cd backend && mvn spring-boot:run"
