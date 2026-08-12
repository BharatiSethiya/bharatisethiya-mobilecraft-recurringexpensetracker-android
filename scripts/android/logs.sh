#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../" && pwd)/load-env.sh"
cd "${MOBILECRAFT_REPO_ROOT}"
: "${APP_PACKAGE:?}"
docker compose run --rm app-runner adb logcat --pid="$(docker compose run --rm app-runner adb shell pidof ${APP_PACKAGE} | tr -d '\r')" 2>/dev/null || docker compose run --rm app-runner adb logcat | grep "${APP_PACKAGE}"
