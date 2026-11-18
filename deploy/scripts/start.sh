#!/usr/bin/env bash
# 前台exec让systemd直接跟踪JVM；不eval/source配置或拼接JAVA_OPTS。
set -euo pipefail
umask 027
release=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
config=${GAMECORE_CONFIG:-/etc/gamecore/server.properties}
java=${JAVA_HOME:+${JAVA_HOME}/bin/}java
args=(-Xms128m -Xmx512m -Xss512k -XX:MaxDirectMemorySize=128m -XX:MaxMetaspaceSize=128m
      -XX:ReservedCodeCacheSize=64m -XX:+UseG1GC -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError
      -Djava.awt.headless=true -Dio.netty.allocator.numDirectArenas=2 -Dio.netty.allocator.numHeapArenas=2
      -Djdk.httpserver.maxConnections=16 -Dsun.net.httpserver.maxIdleConnections=8
      -Dsun.net.httpserver.maxReqHeaders=32 -Dsun.net.httpserver.maxReqHeaderSize=8192
      -Dsun.net.httpserver.maxReqTime=5 -Dsun.net.httpserver.maxRspTime=5 -Dsun.net.httpserver.drainAmount=0
      "-Xlog:gc*,safepoint:file=${GAMECORE_LOG_DIR:-/var/log/gamecore}/gc.log:time,uptime,level,tags:filecount=5,filesize=8M")
if [[ ${1:-} == --check && $# == 1 ]]; then
  exec "$java" "${args[@]}" -cp "$release/server/lib/*" dev.gamecore.server.GameServerMain serve "--config=$config" --check
fi
[[ $# == 0 ]] || { echo 'Usage: start.sh [--check]' >&2; exit 2; }
exec "$java" "${args[@]}" -cp "$release/server/lib/*" dev.gamecore.server.GameServerMain serve "--config=$config"
