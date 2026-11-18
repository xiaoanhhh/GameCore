#!/usr/bin/env bash
# systemd发SIGTERM，等待hook；禁止按端口批量杀进程。
set -euo pipefail
[[ $# == 0 ]] || exit 2
exec systemctl stop gamecore.service
