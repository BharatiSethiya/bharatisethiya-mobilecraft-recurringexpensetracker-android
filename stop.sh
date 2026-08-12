#!/usr/bin/env bash
set -euo pipefail
adb shell am force-stop com.bharatisethiya.recurringexpensetracker 2>&1 || true
echo "Stopped"
