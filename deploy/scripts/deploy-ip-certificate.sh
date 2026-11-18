#!/usr/bin/env bash
# Certbot续期回调：校验证书/密钥后原子切换游戏TLS目录，仅重载Nginx并优雅重启新节点。
# 与旧7100 Tank无关；私钥只留在云机root/专用服务组可读目录。
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Certificate deployment requires root' >&2; exit 2; }
lineage=${RENEWED_LINEAGE:-/etc/letsencrypt/live/gamecore-ip}
[[ $lineage == /etc/letsencrypt/live/gamecore-ip ]] || { echo 'Unexpected certificate lineage' >&2; exit 2; }
base=/etc/gamecore-online/tls
[[ -d $base && ! -L $base ]] || { echo 'Missing/unsafe online TLS directory' >&2; exit 1; }
exec 9>/run/lock/gamecore-ip-certificate.lock
flock -n 9 || exit 0
openssl x509 -in "$lineage/fullchain.pem" -checkend 86400 -noout
ip=$(awk -F= '$1=="GAME_PUBLIC_HOST" {print $2}' /etc/gamecore-online/platform.env)
[[ $ip =~ ^[0-9.]+$ ]] || { echo 'Missing configured public IP' >&2; exit 1; }
openssl x509 -in "$lineage/fullchain.pem" -checkip "$ip" -noout
certificate_key=$(openssl x509 -in "$lineage/fullchain.pem" -pubkey -noout | openssl pkey -pubin -outform DER | sha256sum | cut -d' ' -f1)
private_key=$(openssl pkey -in "$lineage/privkey.pem" -pubout -outform DER | sha256sum | cut -d' ' -f1)
[[ $certificate_key == "$private_key" ]] || { echo 'Certificate/key mismatch' >&2; exit 1; }
fingerprint=$(openssl x509 -in "$lineage/fullchain.pem" -outform DER | sha256sum | cut -d' ' -f1)
[[ $fingerprint =~ ^[a-f0-9]{64}$ ]] || exit 1
nginx -t
changed=true
if [[ -f $base/current/fullchain.pem ]] && cmp -s "$lineage/fullchain.pem" "$base/current/fullchain.pem"; then changed=false; fi
if [[ $changed == true ]]; then
  install -d -o root -g gamecore-online -m 0750 "$base/releases" "$base/releases/$fingerprint"
  install -o root -g gamecore-online -m 0640 "$lineage/fullchain.pem" "$base/releases/$fingerprint/fullchain.pem"
  install -o root -g gamecore-online -m 0640 "$lineage/privkey.pem" "$base/releases/$fingerprint/privkey.pem"
  ln -s "releases/$fingerprint" "$base/.current.new"
  mv -Tf -- "$base/.current.new" "$base/current"
fi
systemctl reload nginx
if [[ $changed == true ]] && systemctl is-active --quiet gamecore-online.service; then
  systemctl restart gamecore-online.service
fi
# 仅清理由本回调生成的64位指纹目录，保留当前和最近上一份；不删除ACME原始证书。
current=$(readlink "$base/current")
kept=0
while IFS= read -r directory; do
  name=${directory##*/}
  [[ $name =~ ^[a-f0-9]{64}$ && ! -L $directory && $directory == "$base/releases/$name" ]] || continue
  if [[ releases/$name == "$current" ]]; then continue; fi
  kept=$((kept+1))
  if ((kept > 1)); then rm -rf -- "$directory"; fi
done < <(find "$base/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -rn | cut -d' ' -f2-)
logger -t gamecore-certificates 'Verified IP certificate deployed; nginx reloaded; online node refreshed if needed'
