#!/usr/bin/env bash

# Source repo defaults first, then user-local overrides. If VMVM host
# networking is selected and the user did not set ADB_SERVER_SOCKET, use the
# host-network ADB endpoint instead of the Docker Desktop default.
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOBILECRAFT_REPO_ROOT="${repo_root}"
export MOBILECRAFT_REPO_ROOT

# Caller-provided values for these settings are restored after sourcing
# .env.example and .env. Add new runner settings here when adding env defaults.
runner_env_vars=(
  ADB_SERVER_SOCKET
  COMPOSE_FILE
  AVD_NAME
  HEADLESS_EMULATOR
  APP_DISPLAY_NAME
  APP_PACKAGE
  APP_APK_PATH
  APP_LAUNCH_ACTIVITY
  GRADLE_TASK
  ANDROID_BUILD_TOOLS_VERSION
)

external_runner_env_names=()
external_runner_env_values=()
external_adb_socket_set=0
for name in "${runner_env_vars[@]}"; do
  if [[ -n "${!name+x}" ]]; then
    external_runner_env_names+=("${name}")
    external_runner_env_values+=("${!name}")
    if [[ "${name}" == "ADB_SERVER_SOCKET" ]]; then
      external_adb_socket_set=1
    fi
  fi
done

env_adb_socket_set=0
if [[ -f "${repo_root}/.env" ]] && grep -Eq '^[[:space:]]*(export[[:space:]]+)?ADB_SERVER_SOCKET=' "${repo_root}/.env"; then
  env_adb_socket_set=1
fi

if [[ -f "${repo_root}/.env.example" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${repo_root}/.env.example"
  set +a
fi

if [[ -f "${repo_root}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${repo_root}/.env"
  set +a
fi

for i in "${!external_runner_env_names[@]}"; do
  printf -v "${external_runner_env_names[$i]}" '%s' "${external_runner_env_values[$i]}"
  export "${external_runner_env_names[$i]}"
done

if [[ "${external_adb_socket_set}" == 0 && "${env_adb_socket_set}" == 0 && "${COMPOSE_FILE:-}" == *docker-compose.vmvm.yml* ]]; then
  ADB_SERVER_SOCKET="tcp:127.0.0.1:5037"
  export ADB_SERVER_SOCKET
fi
