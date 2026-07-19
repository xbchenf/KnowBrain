# Docker 定期清理脚本
# 每月执行一次，防止虚拟磁盘膨胀
# 注意：只清理无用镜像/缓存，不影响运行中的容器和数据卷

Write-Host "=== Docker 定期清理 ===" -ForegroundColor Cyan

# 1. 清理悬空镜像（无标签的中间层）
Write-Host "1/4 清理悬空镜像..." -ForegroundColor Yellow
docker image prune -f

# 2. 清理构建缓存
Write-Host "2/4 清理构建缓存..." -ForegroundColor Yellow
docker builder prune -f

# 3. 清理已停止的容器
Write-Host "3/4 清理已停止容器..." -ForegroundColor Yellow
docker container prune -f

# 4. 清理未使用的网络
Write-Host "4/4 清理未使用网络..." -ForegroundColor Yellow
docker network prune -f

Write-Host ""
Write-Host "=== 清理完成 ===" -ForegroundColor Green
docker system df
