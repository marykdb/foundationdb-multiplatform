#!/usr/bin/env bash
set -euo pipefail

# Start a local fdbserver for tests, ensuring it is installed.
# - Respects FDB_VERSION env var (optional)
# - Writes PID file to build/testdatastore/fdbserver.pid
# - Ensures cluster is configured (single memory) if uninitialized.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BIN_DIR="$ROOT_DIR/foundationdb/bin"
LIB_DIR="$BIN_DIR/lib"

CLUSTER_FILE="$ROOT_DIR/foundationdb/fdb.cluster"
DATA_DIR="$ROOT_DIR/build/testdatastore/data"
LOG_DIR="$ROOT_DIR/build/testdatastore/logs"
PID_FILE="$ROOT_DIR/build/testdatastore/fdbserver.pid"

FDB_LISTEN="${FDB_LISTEN:-127.0.0.1:4500}"

mkdir -p "$DATA_DIR" "$LOG_DIR"

if [[ ! -x "$BIN_DIR/fdbserver" ]] && ! command -v fdbserver >/dev/null 2>&1; then
  bash "$SCRIPT_DIR/install-foundationdb.sh"
fi

export PATH="$BIN_DIR:$PATH"
# Set library paths for JVM tests to pick up libfdb_c; Gradle also sets java.library.path.
case "$(uname -s)" in
  Darwin) export DYLD_LIBRARY_PATH="$LIB_DIR:${DYLD_LIBRARY_PATH:-}" ;;
  Linux) export LD_LIBRARY_PATH="$LIB_DIR:${LD_LIBRARY_PATH:-}" ;;
esac

have() { command -v "$1" >/dev/null 2>&1; }

server_started=false

start_server() {
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE" 2>/dev/null)" 2>/dev/null; then
    server_started=false
    return 0
  fi
  rm -f "$PID_FILE"

  # Ensure log file exists before redirect
  touch "$LOG_DIR/fdbserver.out" || true

  # Create a minimal cluster file if missing; fdbserver will update it.
  if [[ ! -f "$CLUSTER_FILE" ]]; then
    if command -v python3 >/dev/null 2>&1; then
      cluster_id="$(python3 - <<'PY'
import secrets
print(secrets.token_hex(16))
PY
)"
    else
      cluster_id="$(LC_ALL=C tr -dc 'a-f0-9' < /dev/urandom | head -c 32)"
    fi
    echo "test:${cluster_id:-0123456789abcdef}@${FDB_LISTEN}" > "$CLUSTER_FILE"
  fi

  set -x
"$BIN_DIR/fdbserver" \
    --cluster-file "$CLUSTER_FILE" \
    --listen-address "$FDB_LISTEN" \
    --public-address "$FDB_LISTEN" \
    --datadir "$DATA_DIR" \
    --logdir "$LOG_DIR" \
    --locality-machineid maryk-tests \
    --locality-zoneid local-test \
    --knob_max_outstanding=400 \
    --knob_disable_posix_kernel_aio=1 \
    --knob_desired_teams_per_server=1 \
    --knob_min_available_space_ratio=0.001 \
    --knob_enable_tenant_management=1 \
    --memory 512MiB \
    --storage-memory 256MiB \
    --cache-memory 64MiB \
    >"$LOG_DIR/fdbserver.out" 2>&1 &
  set +x
  echo $! > "$PID_FILE"
  server_started=true
}

wait_ready() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  # If we don't have fdbcli available, do a best-effort short wait and return success.
  if [[ ! -x "$BIN_DIR/fdbcli" ]]; then
    sleep 2
    return 0
  fi
  # Wait until the cluster reports as available.
  for i in {1..180}; do
    if "$BIN_DIR/fdbcli" --exec "status minimal" 2>/dev/null | grep -qi "The database is available"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

configure_if_needed() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  # Configure a single-memory test database; ignore errors if it already exists.
  if [[ -x "$BIN_DIR/fdbcli" ]]; then
    local configure_output
    configure_output="$("$BIN_DIR/fdbcli" --timeout 15 --exec "configure new single memory tenant_mode=optional_experimental logs=1 commit_proxies=1 grv_proxies=1" 2>&1 || true)"
    if grep -qi "Database created" <<<"$configure_output"; then
      echo "$configure_output"
    elif grep -qi "Database already exists" <<<"$configure_output"; then
      echo "Reusing existing FoundationDB configuration"
    elif [[ -n "$configure_output" ]]; then
      echo "$configure_output" >&2
    fi
  fi
}

ensure_tenant_mode() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  if [[ -x "$BIN_DIR/fdbcli" ]]; then
    local tenant_output
    tenant_output="$("$BIN_DIR/fdbcli" --timeout 15 --exec "configure tenant_mode=optional_experimental" 2>&1 || true)"
    if [[ -n "$tenant_output" ]]; then
      if grep -qi "error" <<<"$tenant_output" && ! grep -qi "already" <<<"$tenant_output"; then
        echo "$tenant_output" >&2
      else
        echo "$tenant_output"
      fi
    fi
  fi
}

verify_tenant_support() {
  export FDB_CLUSTER_FILE="$CLUSTER_FILE"
  if [[ -x "$BIN_DIR/fdbcli" ]]; then
    local verify_output
    verify_output="$("$BIN_DIR/fdbcli" --timeout 15 --exec "tenant list" 2>&1 || true)"
    if grep -qi "Tenants have been disabled" <<<"$verify_output"; then
      echo "Tenant list failed: $verify_output" >&2
      return 1
    fi
    if [[ -n "$verify_output" ]]; then
      echo "$verify_output"
    fi
  fi
}

start_server

PID="$(cat "$PID_FILE")"
if [[ "$server_started" == "true" ]]; then
  echo "fdbserver started with PID $PID"
else
  echo "Reusing existing fdbserver (PID $PID)"
fi

# Fail fast if server exited immediately
sleep 0.5
if ! kill -0 "$(cat "$PID_FILE" 2>/dev/null)" 2>/dev/null; then
  echo "fdbserver exited immediately; see $LOG_DIR/fdbserver.out" >&2
  exit 1
fi

# Ensure configuration happens before waiting for availability
configure_if_needed || true
ensure_tenant_mode || true
verify_tenant_support || true

if ! wait_ready; then
  echo "fdbserver did not become ready in time" >&2
  exit 1
fi

echo "FoundationDB is ready for tests (PID $(cat "$PID_FILE"))"
