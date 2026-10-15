#!/usr/bin/env bash
# Starts Postgres (Docker) and the Spring Boot backend on :8080 with settings from backend/.env.
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose up -d postgres

if [ -f backend/.env ]; then
  set -a; . backend/.env; set +a
else
  echo "backend/.env not found; copy backend/.env.example and fill it in." >&2
  exit 1
fi

cd backend
if [ -x ./mvnw ]; then ./mvnw spring-boot:run; else mvn spring-boot:run; fi
