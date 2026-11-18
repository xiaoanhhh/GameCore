#!/usr/bin/env bash
# 仅供已授权测试服首次安装：拒绝覆盖已有用户、配置、服务或current链接，不修改SSH/云安全组。
set -euo pipefail
if [[ $# -ne 3 || $EUID -ne 0 ]]; then
    echo "Usage (root): bash install-first.sh UPLOAD_DIRECTORY RELEASE_ID 127.0.0.1|0.0.0.0" >&2
    exit 2
fi
upload=$(realpath -e -- "$1")
release_id=$2
bind_host=$3
[[ $release_id =~ ^[a-zA-Z0-9_-]+$ ]] || exit 2
[[ $bind_host == 127.0.0.1 || $bind_host == 0.0.0.0 ]] || exit 2
[[ $(java --version | awk 'NR == 1 {print $2}') == 21.* ]] || { echo "Java 21 required" >&2; exit 2; }
for target in /etc/systemd/system/gamecore-tank.service /etc/gamecore /var/lib/gamecore /opt/gamecore/current; do
    [[ ! -e $target && ! -L $target ]] || { echo "Refusing existing target: $target" >&2; exit 1; }
done
if getent passwd gamecore >/dev/null || getent group gamecore >/dev/null; then
    echo "Refusing existing gamecore account/group" >&2
    exit 1
fi
cd -- "$upload"
sha256sum --check server.sha256
release=/opt/gamecore/releases/$release_id
install -d -m 0755 /opt/gamecore/releases
mkdir -m 0755 -- "$release"
tar -xf GameCore-TankServer-0.1.0-SNAPSHOT.tar -C "$release" --strip-components=1
test -x "$release/bin/GameCore-TankServer"
chown -R root:root -- "$release"
chmod -R go-w -- "$release"
useradd --system --user-group --home-dir /var/lib/gamecore --shell /usr/sbin/nologin gamecore
install -d -m 0750 -o root -g gamecore /etc/gamecore
install -m 0640 -o root -g gamecore tank.env.example /etc/gamecore/tank.env
sed -i "s/^GAMECORE_HOST=.*/GAMECORE_HOST=$bind_host/" /etc/gamecore/tank.env
ln -s -- "$release" /opt/gamecore/current
systemd-analyze verify "$upload/gamecore-tank.service"
install -m 0644 gamecore-tank.service /etc/systemd/system/gamecore-tank.service
systemctl daemon-reload
systemctl enable --now gamecore-tank.service
systemctl show gamecore-tank.service -p ActiveState -p SubState -p MainPID -p User
echo "Installed release: $release; verify application READY and real protocol before declaring success."
