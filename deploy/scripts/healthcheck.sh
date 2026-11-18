#!/usr/bin/env bash
# 管理探针仅固定回环，不可通过环境重定向到公网或注入curl选项。
set -euo pipefail
port=${GAMECORE_HEALTH_PORT:-7202}
[[ $port =~ ^[0-9]{1,5}$ ]] && (( port > 0 && port <= 65535 )) || exit 2
exec curl --noproxy '*' --fail --silent --show-error --max-time 2 "http://127.0.0.1:$port/health"
