# KnowBrain E2E — Full Suite (Smoke + Regression + API)
# 用法: .\e2e\scripts\run-all.ps1
# 注意：API 和浏览器测试分两个阶段运行，避免 A1 限流测试影响浏览器项目

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

Write-Host "=== KnowBrain E2E Full Suite ===" -ForegroundColor Cyan

if (-not (Test-Path "node_modules\@playwright\test")) {
    Write-Host "[1/4] Installing dependencies..." -ForegroundColor Yellow
    npm install
}

# Phase 1: 浏览器 UI 测试 (smoke + regression)
Write-Host "[2/4] Running browser tests (smoke + regression)..." -ForegroundColor Yellow
$env:BASE_URL = "http://localhost"
npx playwright test --project=chromium
$browserResult = $LASTEXITCODE

# Phase 2: API 纯接口测试（含 A1 限流测试，会影响登录端点）
Write-Host "[3/4] Running API tests..." -ForegroundColor Yellow
npx playwright test --project=api
$apiResult = $LASTEXITCODE

Write-Host "[4/4] Done." -ForegroundColor Green
Write-Host "Browser: $(if ($browserResult -eq 0) {'PASS'} else {'FAIL'})" -ForegroundColor $(if ($browserResult -eq 0) {'Green'} else {'Red'})
Write-Host "API:     $(if ($apiResult -eq 0) {'PASS'} else {'FAIL'})" -ForegroundColor $(if ($apiResult -eq 0) {'Green'} else {'Red'})

# 报告
if (Test-Path "playwright-report") {
    npx playwright show-report --host 0.0.0.0 --port 9323 2>$null &
    Write-Host "Report: http://localhost:9323" -ForegroundColor Cyan
}

exit ($browserResult -bor $apiResult)
