#!/bin/bash

echo "=========================================="
echo "海南储备集团粮食市场智能分析平台"
echo "快速启动脚本"
echo "=========================================="

# 检查Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker未安装，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose未安装"
    exit 1
fi

echo "✅ Docker已就绪"

# 进入docker目录
cd "$(dirname "$0")/docker" || exit

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p mysql/data redis/data backend/logs backend/data

# 启动服务
echo "🚀 启动服务..."
docker-compose up -d

# 等待MySQL启动
echo "⏳ 等待MySQL启动..."
sleep 10

# 检查服务状态
echo ""
echo "=========================================="
echo "✅ 启动完成！"
echo "=========================================="
echo ""
echo "🌐 访问地址："
echo "   - 前端界面: http://localhost:3000"
echo "   - 后端API:  http://localhost:8080/api"
echo ""
echo "📊 服务状态："
docker-compose ps
echo ""
echo "💡 常用命令："
echo "   - 查看日志: docker-compose logs -f backend"
echo "   - 停止服务: docker-compose down"
echo "   - 重启服务: docker-compose restart"
echo ""
