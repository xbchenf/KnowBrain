#!/bin/bash
# KnowBrain E2E — Smoke Test
# 用法: bash e2e/scripts/run-smoke.sh
# 前置: docker compose up -d + npm install

set -e
cd "$(dirname "$0")/.."

echo "=== KnowBrain E2E Smoke Tests ==="

# 1. 安装依赖
if [ ! -d "node_modules/@playwright/test" ]; then
    echo "[1/4] Installing dependencies..."
    npm install
fi

# 2. 安装 Playwright 浏览器
echo "[2/4] Checking Playwright browsers..."
npx playwright install chromium --with-deps 2>/dev/null || true

# 3. 确保 auth 目录
mkdir -p .auth

# 4. 运行冒烟测试
echo "[3/4] Running smoke tests..."
BASE_URL="http://localhost" npx playwright test --project=chromium --project=api --grep @smoke

# 5. 显示报告
echo "[4/4] Tests complete. Report: e2e/playwright-report/"
npx playwright show-report --host 0.0.0.0 --port 9323 &
echo "Report available at http://localhost:9323"
