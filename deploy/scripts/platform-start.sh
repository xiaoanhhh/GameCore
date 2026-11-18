#!/usr/bin/env bash
# 可选平台单独进程，资源预算与游戏节点分开计算，DB配置由受保护EnvironmentFile提供。
set -euo pipefail
umask 027
release=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
java=${JAVA_HOME:+${JAVA_HOME}/bin/}java
exec "$java" -Xms64m -Xmx256m -Xss512k -XX:MaxDirectMemorySize=32m -XX:MaxMetaspaceSize=160m \
  -XX:ReservedCodeCacheSize=64m -XX:ActiveProcessorCount=2 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError \
  "-Xlog:gc*,safepoint:file=${GAMECORE_LOG_DIR:-/var/log/gamecore-platform}/gc.log:time,uptime,level,tags:filecount=5,filesize=8M" \
  -Djava.awt.headless=true -jar "$release/platform/platform-service.jar"
