#!/bin/bash
# 配置文件权限检查脚本
# 用法: ./scripts/check-config-perms.sh <env>
# 环境: dev / test / prod
set -euo pipefail

ENV="${1:?请指定环境名: dev/test/prod}"
CONFIG_DIR="app/config/${ENV}"
ERROR_COUNT=0

if [ ! -d "$CONFIG_DIR" ]; then
    echo "[CHECK] 目录 $CONFIG_DIR 不存在，跳过检查"
    exit 0
fi

for f in "$CONFIG_DIR"/*.yaml; do
    [ -f "$f" ] || continue
    PERMS=$(stat -c "%a" "$f")
    if [ "$PERMS" != "400" ]; then
        echo "[FAIL] $f 权限为 $PERMS，期望 400"
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
done

PY_DIR="app/constants"
for f in "$PY_DIR"/*.py; do
    [ -f "$f" ] || continue
    PERMS=$(stat -c "%a" "$f")
    if [ "$PERMS" != "400" ]; then
        echo "[FAIL] $f 权限为 $PERMS，期望 400"
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
done

if [ "$ERROR_COUNT" -gt 0 ]; then
    echo "[BLOCKING] 发现 $ERROR_COUNT 个文件权限异常，阻断发布"
    exit 1
else
    echo "[PASS] 所有配置文件权限为 400"
    exit 0
fi
