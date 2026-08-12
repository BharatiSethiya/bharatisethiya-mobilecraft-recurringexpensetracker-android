#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../" && pwd)/load-env.sh"
cd "${MOBILECRAFT_REPO_ROOT}"
docker compose run --rm app-runner gradle --no-daemon "$@"
