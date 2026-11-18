#!/usr/bin/env bash
# 由管理员以postgres身份运行；不把密码放命令行。备份包含身份数据，目录必须限制访问。
set -euo pipefail
[[ $# == 1 && $1 == /* ]] || { echo 'Usage: backup-platform.sh ABSOLUTE_BACKUP_DIR' >&2; exit 2; }
umask 077
install -d -m 0700 -- "$1"
target="$1/gamecore-$(date -u +%Y%m%dT%H%M%SZ).dump"
pg_dump --format=custom --no-owner --dbname=gamecore --file="$target.partial"
mv -- "$target.partial" "$target"
sha256sum -- "$target" > "$target.sha256"
# 不自动删备份；先核实异地副本和恢复测试，再由管理员执行保留策略。
