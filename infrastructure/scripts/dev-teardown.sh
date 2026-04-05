#!/usr/bin/env bash
# GRC Platform — Teardown script (stops backend, frontend, optionally Docker)
# OS: Ubuntu Linux (bash)
#
# Usage:
#   ./infrastructure/scripts/dev-teardown.sh              # stop app processes only
#   ./infrastructure/scripts/dev-teardown.sh --all        # also stop Docker services
#   ./infrastructure/scripts/dev-teardown.sh --all --wipe # stop + destroy volumes (DATA LOSS)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOGS_DIR="${INFRA_DIR}/logs"
PID_DIR="${INFRA_DIR}/.pids"
COMPOSE_FILE="${INFRA_DIR}/docker-compose.yml"

STOP_DOCKER=false
WIPE_VOLUMES=false
for arg in "$@"; do
  case "$arg" in
    --all)  STOP_DOCKER=true ;;
    --wipe) WIPE_VOLUMES=true ;;
  esac
done

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'
log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }

stop_process() {
  local name="$1"
  local pid_file="${PID_DIR}/${name}.pid"

  if [[ ! -f "${pid_file}" ]]; then
    log_info "${name}: no PID file found — already stopped or not started by this script."
    return 0
  fi

  local pid
  pid=$(cat "${pid_file}")

  if kill -0 "${pid}" 2>/dev/null; then
    log_info "Stopping ${name} (PID ${pid})..."
    # SIGTERM first; give 15s to flush, then SIGKILL
    kill -TERM "${pid}" 2>/dev/null || true
    local waited=0
    while kill -0 "${pid}" 2>/dev/null && [[ $waited -lt 15 ]]; do
      sleep 1; waited=$((waited + 1))
    done
    if kill -0 "${pid}" 2>/dev/null; then
      log_warn "${name} did not exit — force-killing..."
      kill -KILL "${pid}" 2>/dev/null || true
    fi
    log_ok "${name} stopped."
  else
    log_info "${name} (PID ${pid}) is not running — nothing to stop."
  fi

  rm -f "${pid_file}"
}

archive_logs() {
  if [[ -d "${LOGS_DIR}" ]]; then
    local stamp
    stamp=$(date +%Y%m%d_%H%M%S)
    local archive="${LOGS_DIR}/archive_${stamp}"
    mkdir -p "${archive}"
    for f in backend.log frontend.log; do
      [[ -f "${LOGS_DIR}/${f}" ]] && mv "${LOGS_DIR}/${f}" "${archive}/${f}" || true
    done
    log_info "Logs archived to ${archive}/"
  fi
}

main() {
  echo ""
  echo -e "${YELLOW}${BOLD}  GRC Platform — Teardown${NC}"
  echo -e "  $(date)"
  echo ""

  stop_process "backend"
  stop_process "frontend"
  archive_logs

  if [[ "${STOP_DOCKER}" == true ]]; then
    if [[ "${WIPE_VOLUMES}" == true ]]; then
      echo ""
      log_warn "WARNING: --wipe will destroy ALL Docker volumes (SQL Server, Neo4j, Redis, Keycloak data)."
      log_warn "This is irreversible. Type 'yes' to confirm:"
      read -r confirmation
      if [[ "${confirmation}" == "yes" ]]; then
        log_info "Stopping Docker services and destroying volumes..."
        docker compose -f "${COMPOSE_FILE}" --project-directory "${INFRA_DIR}" down -v
        log_ok "Docker services and volumes destroyed."
      else
        log_warn "Aborted volume wipe. Stopping services without destroying volumes..."
        docker compose -f "${COMPOSE_FILE}" --project-directory "${INFRA_DIR}" down
        log_ok "Docker services stopped (volumes preserved)."
      fi
    else
      log_info "Stopping Docker services (volumes preserved)..."
      docker compose -f "${COMPOSE_FILE}" --project-directory "${INFRA_DIR}" down
      log_ok "Docker services stopped."
    fi
  else
    log_info "Docker services left running. Use --all to stop them."
  fi

  echo ""
  echo -e "${GREEN}${BOLD}Teardown complete.${NC}"
  echo ""
}

main "$@"
