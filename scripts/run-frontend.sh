#!/usr/bin/env bash
# Starts the React dev server on :5173 (proxies /api to the backend on :8080).
set -euo pipefail
cd "$(dirname "$0")/../frontend"
[ -d node_modules ] || npm ci
npm run dev
