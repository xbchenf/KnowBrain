#!/bin/bash
# KnowBrain E2E — Full Regression
# 用法: bash e2e/scripts/run-all.sh

set -e
cd "$(dirname "$0")/.."

echo "=== KnowBrain E2E Full Suite ==="

[ ! -d "node_modules/@playwright/test" ] && npm install

echo "[1/2] Running all tests..."
BASE_URL="http://localhost" npx playwright test --project=chromium --project=api

echo "[2/2] Done."
npx playwright show-report --host 0.0.0.0 --port 9323 &
