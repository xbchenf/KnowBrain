# KnowBrain E2E — Smoke Test (冒烟测试)
# 用法: .\e2e\scripts\run-smoke.ps1
# 前置: docker compose up -d (服务运行中) + npm install (依赖已安装)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

Write-Host "=== KnowBrain E2E Smoke Tests ===" -ForegroundColor Cyan

# 1. 确保依赖已安装
if (-not (Test-Path "node_modules\@playwright\test")) {
    Write-Host "[1/4] Installing dependencies..." -ForegroundColor Yellow
    npm install
}

# 2. 确保 Playwright 浏览器已安装
Write-Host "[2/4] Checking Playwright browsers..." -ForegroundColor Yellow
npx playwright install chromium --with-deps 2>$null

# 3. 确保 auth 目录存在
if (-not (Test-Path ".auth")) {
    New-Item -ItemType Directory -Path ".auth" | Out-Null
}

# 4. 运行冒烟测试
Write-Host "[3/4] Running smoke tests..." -ForegroundColor Yellow
$env:BASE_URL = "http://localhost"
npx playwright test --project=chromium --project=api --grep @smoke

# 5. 显示报告
Write-Host "[4/4] Tests complete. Report: e2e\playwright-report\" -ForegroundColor Green
npx playwright show-report --host 0.0.0.0 --port 9323 2>$null &
Write-Host "Report available at http://localhost:9323" -ForegroundColor Green
