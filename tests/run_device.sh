#!/usr/bin/env bash
# Kiem thu module NekoDevice (nhan dien may + tu toi uu). Chay: bash tests/run_device.sh
set -euo pipefail
T="$(cd "$(dirname "$0")" && pwd)"; KIT="$(cd "$T/.." && pwd)"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
export JAVA_TOOL_OPTIONS=""
javac -nowarn -encoding UTF-8 --release 8 -Xlint:-options -d "$W" "$KIT/shared/device/NekoDevice.java" "$T/TestDevice.java" 2>&1 | grep -v "Picked up" || true
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$W" TestDevice 2>&1 | grep -v "Picked up"
