#!/usr/bin/env bash
# 仅安装不可变版本；不会自动改current或启动服务。预期SHA应来自可信构建渠道。
set -euo pipefail
[[ $EUID == 0 && $# == 2 ]] || { echo 'Usage (root): install-release.sh ARCHIVE EXPECTED_SHA256' >&2; exit 2; }
umask 022
[[ ! -L /opt/gamecore && ! -L /opt/gamecore/releases ]] || { echo 'Release root must not be a symlink' >&2; exit 1; }
install -d -o root -g root -m 0755 /opt/gamecore/releases
exec python3 "$(dirname -- "$0")/verify-release.py" "$1" "$2" /opt/gamecore/releases
