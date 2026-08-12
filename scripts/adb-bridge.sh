#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pid_file="${repo_root}/.adb-bridge.pid"
log_file="${repo_root}/.adb-bridge.log"
adb_port="${ADB_PORT:-5037}"

find_adb() {
  if [[ -n "${HOST_ADB:-}" ]]; then
    printf '%s\n' "${HOST_ADB}"
    return
  fi

  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi

  if [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
    printf '%s\n' "${ANDROID_HOME}/platform-tools/adb"
    return
  fi

  if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
    printf '%s\n' "${ANDROID_SDK_ROOT}/platform-tools/adb"
    return
  fi

  if [[ -x /opt/android_sdk/platform-tools/adb ]]; then
    printf '%s\n' /opt/android_sdk/platform-tools/adb
    return
  fi

  echo "Could not find host adb. Set HOST_ADB=/path/to/adb or ANDROID_HOME." >&2
  exit 1
}

adb_bin="$(find_adb)"

usage() {
  cat <<'EOF'
Usage: scripts/adb-bridge.sh <start|status|stop|restart>

Starts a host adb server that listens on localhost so the Docker app-runner
can connect through ADB_SERVER_SOCKET.

Environment:
  HOST_ADB=/path/to/adb    Override adb binary discovery.
  ADB_PORT=5037            Override adb server port.
  ADB_BRIDGE_BIND_ALL=1    Bind adb to all interfaces. This exposes adb to
                           your local network.
EOF
}

bridge_pid() {
  if [[ -f "${pid_file}" ]]; then
    cat "${pid_file}"
  fi
}

bridge_running() {
  local pid
  pid="$(bridge_pid || true)"
  [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1
}

show_devices() {
  if ! "${adb_bin}" -H 127.0.0.1 -P "${adb_port}" devices -l; then
    echo "Host adb server is not reachable on 127.0.0.1:${adb_port}." >&2
    return 1
  fi
}

start_bridge() {
  if bridge_running; then
    echo "adb bridge is already running with pid $(bridge_pid)."
    show_devices || true
    return
  fi

  rm -f "${pid_file}"

  adb_server_args=(-P "${adb_port}" nodaemon server)
  bind_all="${ADB_BRIDGE_BIND_ALL:-0}"
  bind_address="127.0.0.1"
  if [[ "${bind_all}" == "1" ]]; then
    adb_server_args=(-a "${adb_server_args[@]}")
    bind_address="0.0.0.0"
  fi

  echo "Starting adb bridge on ${bind_address}:${adb_port}."
  if [[ "${bind_all}" == "1" ]]; then
    echo "This exposes adb to your local network. Use scripts/adb-bridge.sh stop when finished."
  elif [[ "$(uname)" == "Linux" ]]; then
    echo "Note: native Linux Docker may not reach a localhost-only adb bridge through host.docker.internal."
    echo "Use COMPOSE_FILE=docker-compose.yml:docker-compose.vmvm.yml for host networking, or ADB_BRIDGE_BIND_ALL=1 if you accept LAN exposure."
  fi

  "${adb_bin}" -P "${adb_port}" kill-server >/dev/null 2>&1 || true
  nohup "${adb_bin}" "${adb_server_args[@]}" >"${log_file}" 2>&1 &
  echo "$!" >"${pid_file}"

  for _ in $(seq 1 40); do
    if show_devices >/dev/null 2>&1; then
      echo "adb bridge is ready."
      echo "Container adb socket: tcp:host.docker.internal:${adb_port}"
      show_devices || true
      return
    fi
    sleep 0.25
  done

  echo "adb bridge did not become ready. Last log lines:" >&2
  tail -40 "${log_file}" >&2 || true
  exit 1
}

stop_bridge() {
  local pid
  pid="$(bridge_pid || true)"

  if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
    kill "${pid}" >/dev/null 2>&1 || true
    for _ in $(seq 1 20); do
      if ! kill -0 "${pid}" >/dev/null 2>&1; then
        break
      fi
      sleep 0.1
    done
    echo "Stopped adb bridge pid ${pid}."
  else
    echo "No adb bridge pid is running."
  fi

  rm -f "${pid_file}"
}

case "${1:-status}" in
  start)
    start_bridge
    ;;
  status)
    if bridge_running; then
      echo "adb bridge pid: $(bridge_pid)"
    else
      echo "adb bridge pid: not running"
    fi
    echo "Expected container adb socket: tcp:host.docker.internal:${adb_port}"
    show_devices || true
    ;;
  stop)
    stop_bridge
    ;;
  restart)
    stop_bridge
    start_bridge
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
