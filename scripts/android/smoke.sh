#!/usr/bin/env bash
set -euo pipefail
package=com.bharatisethiya.recurringexpensetracker
command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
device_count=$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')
[[ "$device_count" -ge 1 ]] || { echo "expected at least one Android device; found $device_count" >&2; exit 2; }
adb shell am start -W -n $package/.MainActivity >/dev/null
sleep 2
echo "smoke passed: $package launched"
