# KnowBrain E2E — Regression Only
# 用法: .\e2e\scripts\run-regression.ps1

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

Write-Host "=== KnowBrain E2E Regression ===" -ForegroundColor Cyan

if (-not (Test-Path "node_modules\@playwright\test")) {
    Write-Host "[1/3] Installing dependencies..." -ForegroundColor Yellow
    npm install
}

Write-Host "[2/3] Running regression tests..." -ForegroundColor Yellow
$env:BASE_URL = "http://localhost"
npx playwright test --project=chromium --grep "@regression"

Write-Host "[3/3] Done." -ForegroundColor Green
