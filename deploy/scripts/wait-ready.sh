#!/usr/bin/env bash
# 仅systemd的启动检查，不在网络/逻辑线程等待。
set -euo pipefail
for ((attempt=0;attempt<30;attempt++)); do
  if "$(dirname -- "$0")/healthcheck.sh" >/dev/null 2>&1; then exit 0; fi
  sleep 1
done
echo 'GameCore readiness timed out' >&2
exit 1
