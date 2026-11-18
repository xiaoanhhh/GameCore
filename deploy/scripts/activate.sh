#!/usr/bin/env bash
# 单节点维护窗口升级：预检成功后停服、原子换链接；启动失败恢复旧链接。DB迁移不自动降级。
set -euo pipefail
[[ $EUID == 0 && $# == 1 && $1 =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ && $1 != *..* ]] || { echo 'Usage (root): activate.sh RELEASE_ID' >&2; exit 2; }
base=/opt/gamecore
exec 9>"$base/.activation.lock"
flock -n 9 || { echo 'Another activation is running' >&2; exit 1; }
candidate=$(realpath -e -- "$base/releases/$1")
[[ $candidate == "$base/releases/$1" && ! -e $base/current.next && ! -L $base/current.next ]] || exit 2
python3 "$candidate/deploy/scripts/verify-release.py" --tree "$candidate" >/dev/null
previous=$(readlink -e -- "$base/current" || true)
if [[ -n $previous && $previous != "$base/releases/"* ]]; then echo 'Unexpected current target' >&2; exit 1; fi
systemd-run --quiet --wait --pipe --collect --unit="gamecore-preflight-$$" \
  --property=User=gamecore --property=Group=gamecore --property=WorkingDirectory=/var/lib/gamecore \
  --property=EnvironmentFile=/etc/gamecore/gamecore.env --setenv=GAMECORE_CONFIG=/etc/gamecore/server.properties \
  "$candidate/deploy/scripts/start.sh" --check
stopped=false
rollback() {
  status=$?
  if [[ $status != 0 && $stopped == true ]]; then
    systemctl stop gamecore.service || true
    if [[ -n $previous ]]; then
      if [[ -L $base/current.next ]]; then rm -- "$base/current.next"; fi
      ln -s -- "$previous" "$base/current.next"
      mv -Tf -- "$base/current.next" "$base/current"
      systemctl reset-failed gamecore.service
      systemctl start gamecore.service || echo 'ROLLBACK START FAILED: inspect journal' >&2
      echo "ROLLED_BACK_TO $previous" >&2
    else
      echo 'Initial activation failed; service remains stopped' >&2
    fi
  fi
  exit "$status"
}
trap rollback EXIT
stopped=true
systemctl stop gamecore.service
ln -s -- "$candidate" "$base/current.next"
mv -Tf -- "$base/current.next" "$base/current"
systemctl reset-failed gamecore.service
systemctl start gamecore.service
echo "ACTIVE_RELEASE $1"
