#!/bin/bash
# KnowBrain E2E — Regression Only
# 用法: ./e2e/scripts/run-regression.sh

set -e
cd "$(dirname "$0")/.."

echo "=== KnowBrain E2E Regression ==="

if [ ! -d "node_modules/@playwright/test" ]; then
    echo "[1/3] Installing dependencies..."
    npm install
fi

echo "[2/3] Running regression tests..."
BASE_URL="${BASE_URL:-http://localhost}" npx playwright test --project=chromium --grep "@regression"

echo "[3/3] Done."
