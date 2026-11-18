#!/usr/bin/env bash
set -euo pipefail
[[ $# == 0 ]] || exit 2
exec systemctl restart gamecore.service
