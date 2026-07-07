@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================
echo   KnowBrain（知脑）— 企业私有知识大脑
echo ============================================
echo.

REM ---- 检查 Docker ----
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未找到 Docker，请先安装 Docker Desktop。
    pause
    exit /b 1
)

REM 检测 Docker Compose 命令
set COMPOSE=
docker compose version >nul 2>nul
if %errorlevel% equ 0 (
    set COMPOSE=docker compose
) else (
    docker-compose --version >nul 2>nul
    if %errorlevel% equ 0 (
        set COMPOSE=docker-compose
    ) else (
        echo [错误] 未找到 docker compose。
        pause
        exit /b 1
    )
)

echo [检查] Docker 环境就绪

REM ---- 环境变量 ----
if not exist .env (
    if exist .env.example (
        copy .env.example .env >nul
        echo [提示] 已从 .env.example 创建 .env
        echo        请用记事本编辑 .env，填写 SPRING_AI_OPENAI_API_KEY 后重新运行。
        pause
        exit /b 0
    ) else (
        echo [错误] 未找到 .env.example，请确认在项目根目录运行本脚本。
        pause
        exit /b 1
    )
)

REM ---- 构建并启动 ----
echo.
echo [1/2] 构建镜像（首次运行较慢，后续使用缓存）...
%COMPOSE% build --parallel

echo.
echo [2/2] 启动服务...
%COMPOSE% up -d

echo.
echo ============================================
echo   KnowBrain 启动中...
echo   等待约 30 秒后访问:
echo   问答界面:  http://localhost
echo   管理后台:  http://localhost/admin
echo   MinIO:    http://localhost:9001
echo.
echo   默认管理员: admin / KnowBrain@2026
echo.
echo   查看日志: %COMPOSE% logs -f
echo   停止服务: %COMPOSE% down
echo ============================================

pause
