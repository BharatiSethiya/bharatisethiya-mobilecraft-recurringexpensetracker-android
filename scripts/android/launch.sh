#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../" && pwd)/load-env.sh"
cd "${MOBILECRAFT_REPO_ROOT}"
: "${APP_PACKAGE:?}"
docker compose run --rm app-runner adb shell input keyevent KEYCODE_WAKEUP
docker compose run --rm app-runner adb shell wm dismiss-keyguard
docker compose run --rm app-runner adb shell monkey -p "${APP_PACKAGE}" 1
