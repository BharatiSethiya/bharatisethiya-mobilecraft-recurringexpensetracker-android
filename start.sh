#!/usr/bin/env bash
set -euo pipefail
command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
docker compose build app-runner
docker compose run --rm app-runner gradle --no-daemon :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb shell am start -W -n com.bharatisethiya.recurringexpensetracker/.MainActivity >/dev/null
echo "Recurring Expense Tracker started"
