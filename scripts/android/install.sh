#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../" && pwd)/load-env.sh"
cd "${MOBILECRAFT_REPO_ROOT}"
: "${APP_PACKAGE:?}"
: "${APP_APK_PATH:?}"
docker compose run --rm app-runner adb install -r "${APP_APK_PATH}"
