#!/usr/bin/env bash
set -euo pipefail

if [[ ${1:-} == "--plan" ]]; then
  echo "Plan: validate Gradle wrapper, compile the Android app, and run unit tests."
  exit 0
fi

command -v docker >/dev/null || { echo "docker is required" >&2; exit 2; }
docker compose build app-runner
docker compose run --rm app-runner gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
