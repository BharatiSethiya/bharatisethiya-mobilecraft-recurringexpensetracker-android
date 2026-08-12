#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../" && pwd)/load-env.sh"
cd "${MOBILECRAFT_REPO_ROOT}"

LP_BACKUP=""
if [[ -f "local.properties" ]]; then
  LP_BACKUP="$(mktemp)"
  cp "local.properties" "${LP_BACKUP}"
  echo "sdk.dir=/opt/android-sdk-linux" > local.properties
  trap 'mv "${LP_BACKUP}" "local.properties"' EXIT
fi

docker compose build app-runner
docker compose run --rm app-runner gradle --no-daemon :app:assembleDebug "$@"
status=$?
if [[ -n "${LP_BACKUP}" ]]; then
  mv "${LP_BACKUP}" "local.properties"
  trap - EXIT
fi
exit ${status}
