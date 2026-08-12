#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pid_file="${repo_root}/.emulator.pid"
log_file="${repo_root}/.emulator.log"
adb_bridge="${repo_root}/scripts/adb-bridge.sh"

find_android_tool() {
  local name="$1"
  local env_override="$2"
  shift 2

  if [[ -n "${env_override}" ]]; then
    printf '%s\n' "${env_override}"
    return
  fi

  for candidate in "$@"; do
    if [[ -x "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return
    fi
  done

  if command -v "${name}" >/dev/null 2>&1; then
    command -v "${name}"
    return
  fi

  echo "Could not find ${name}. Set the matching *_BIN override or ANDROID_HOME." >&2
  exit 1
}

android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android_sdk}}"
emulator_bin="$(find_android_tool emulator "${EMULATOR_BIN:-}" \
  "${android_home}/emulator/emulator" \
  "/opt/android_sdk/emulator/emulator")"
adb_bin="$(find_android_tool adb "${HOST_ADB:-${ADB_BIN:-}}" \
  "${android_home}/platform-tools/adb" \
  "/opt/android_sdk/platform-tools/adb")"

usage() {
  cat <<'EOF'
Usage: scripts/host-emulator.sh <list|start|status|stop> [AVD_NAME] [--headless]

Starts a host-native Android emulator and then starts scripts/adb-bridge.sh so
Dockerized adb can install and launch the app.

Environment:
  AVD_NAME=fb_default_emulator  Default AVD name for start.
  HEADLESS_EMULATOR=1           Start without an emulator window.
  EMULATOR_BIN=/path/to/emulator
  HOST_ADB=/path/to/adb
EOF
}

emulator_pid() {
  if [[ -f "${pid_file}" ]]; then
    cat "${pid_file}"
  fi
}

emulator_pid_running() {
  local pid
  pid="$(emulator_pid || true)"
  [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1
}

first_emulator_serial() {
  "${adb_bin}" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

boot_completed() {
  local serial="$1"
  local value
  value="$("${adb_bin}" -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  [[ "${value}" == "1" ]]
}

wait_for_boot() {
  local serial=""

  for _ in $(seq 1 180); do
    serial="$(first_emulator_serial || true)"
    if [[ -n "${serial}" ]] && boot_completed "${serial}"; then
      echo "Emulator ${serial} is booted."
      return
    fi
    sleep 1
  done

  echo "Emulator did not finish booting. Last log lines:" >&2
  tail -80 "${log_file}" >&2 || true
  exit 1
}

start_emulator() {
  local avd="${AVD_NAME:-fb_default_emulator}"
  local headless="${HEADLESS_EMULATOR:-0}"

  for arg in "$@"; do
    case "${arg}" in
      --headless)
        headless=1
        ;;
      --window)
        headless=0
        ;;
      *)
        avd="${arg}"
        ;;
    esac
  done

  if [[ -n "$(first_emulator_serial || true)" ]]; then
    echo "An emulator is already connected:"
    "${adb_bin}" devices -l
    "${adb_bridge}" start
    return
  fi

  local args=("-avd" "${avd}" "-no-snapshot-save" "-no-boot-anim")
  if [[ "${headless}" == "1" ]]; then
    args+=("-no-window" "-gpu" "swiftshader_indirect")
  fi

  echo "Starting Android emulator AVD '${avd}'."
  nohup "${emulator_bin}" "${args[@]}" >"${log_file}" 2>&1 &
  echo "$!" >"${pid_file}"

  if ! emulator_pid_running; then
    echo "Failed to start emulator. Last log lines:" >&2
    tail -80 "${log_file}" >&2 || true
    exit 1
  fi

  wait_for_boot
  "${adb_bridge}" start
}

stop_emulator() {
  local serial
  serial="$(first_emulator_serial || true)"

  if [[ -n "${serial}" ]]; then
    "${adb_bin}" -s "${serial}" emu kill >/dev/null 2>&1 || true
    echo "Requested emulator ${serial} shutdown."
  fi

  if emulator_pid_running; then
    kill "$(emulator_pid)" >/dev/null 2>&1 || true
  fi

  rm -f "${pid_file}"

  if [[ "${KEEP_ADB_BRIDGE:-0}" != "1" ]]; then
    "${adb_bridge}" stop
  fi
}

case "${1:-status}" in
  list)
    "${emulator_bin}" -list-avds
    ;;
  start)
    shift
    start_emulator "$@"
    ;;
  status)
    if emulator_pid_running; then
      echo "emulator pid: $(emulator_pid)"
    else
      echo "emulator pid: not running"
    fi
    "${adb_bin}" devices -l
    "${adb_bridge}" status
    ;;
  stop)
    stop_emulator
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
