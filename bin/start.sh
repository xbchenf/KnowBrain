#!/usr/bin/env bash
# KnowBrain 一键启动脚本（Linux / macOS）
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  KnowBrain（知脑）— 企业私有知识大脑${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# ---- 检查依赖 ----
check_cmd() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${RED}[错误] 未找到 $1，请先安装。${NC}"
        exit 1
    fi
}

check_cmd docker

# 检查 Docker Compose（兼容新旧命令）
if docker compose version &> /dev/null; then
    COMPOSE="docker compose"
elif docker-compose --version &> /dev/null; then
    COMPOSE="docker-compose"
else
    echo -e "${RED}[错误] 未找到 docker compose，请先安装 Docker Desktop 或 docker-compose。${NC}"
    exit 1
fi

echo -e "[检查] Docker 环境就绪 ✓"

# ---- 环境变量 ----
if [ ! -f .env ]; then
    if [ -f .env.example ]; then
        cp .env.example .env
        echo -e "${YELLOW}[提示] 已从 .env.example 创建 .env，请编辑 LLM API Key 后重新运行。${NC}"
        echo -e "${YELLOW}        vim .env  # 修改 SPRING_AI_OPENAI_API_KEY${NC}"
        exit 0
    else
        echo -e "${RED}[错误] 未找到 .env.example，请确认在项目根目录运行本脚本。${NC}"
        exit 1
    fi
fi

# ---- 构建并启动 ----
echo ""
echo -e "[1/2] 构建镜像（首次运行较慢，后续使用缓存）..."
$COMPOSE build --parallel

echo ""
echo -e "[2/2] 启动服务..."
$COMPOSE up -d

# ---- 等待就绪 ----
echo ""
echo -n "等待服务就绪"
for i in $(seq 1 60); do
    if curl -s http://localhost:8080/api/v1/health > /dev/null 2>&1; then
        echo ""
        echo ""
        echo -e "${GREEN}============================================${NC}"
        echo -e "${GREEN}  KnowBrain 启动成功！${NC}"
        echo -e "${GREEN}============================================${NC}"
        echo ""
        echo -e "  问答界面:  ${GREEN}http://localhost${NC}"
        echo -e "  管理后台:  ${GREEN}http://localhost/admin${NC}"
        echo -e "  MinIO 控制台: ${GREEN}http://localhost:9001${NC}"
        echo ""
        echo -e "  默认管理员: admin / admin123"
        echo ""
        echo -e "  查看日志: $COMPOSE logs -f"
        echo -e "  停止服务: $COMPOSE down"
        exit 0
    fi
    echo -n "."
    sleep 3
done

echo ""
echo -e "${YELLOW}[提示] 服务启动超时，请运行 '$COMPOSE logs' 查看日志排查。${NC}"
echo -e "  问答界面: http://localhost"
echo -e "  管理后台: http://localhost/admin"
