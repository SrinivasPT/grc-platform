#!/usr/bin/env bash
# GRC Platform — Local Development Setup Script
# OS: Ubuntu Linux (bash)
#
# This script is IDEMPOTENT — running it multiple times is safe:
#   - Docker services are started/resumed (not recreated if healthy)
#   - DB init uses IF NOT EXISTS guards
#   - Liquibase tracks applied changesets and skips already-applied ones
#   - Backend/frontend processes are skipped if already running on their ports
#
# Usage:
#   ./infrastructure/scripts/dev-setup.sh
#   ./infrastructure/scripts/dev-setup.sh --skip-infra   (skip Docker, go straight to app)
#   ./infrastructure/scripts/dev-setup.sh --skip-migrations

set -euo pipefail

# ─── Paths ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${INFRA_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"
FRONTEND_DIR="${REPO_ROOT}/frontend"
LOGS_DIR="${INFRA_DIR}/logs"
PID_DIR="${INFRA_DIR}/.pids"
COMPOSE_FILE="${INFRA_DIR}/docker-compose.yml"

# ─── Ports ─────────────────────────────────────────────────────────────────────
BACKEND_PORT=8090
FRONTEND_PORT=3000   # Configured in frontend/vite.config.ts

# ─── Flags ─────────────────────────────────────────────────────────────────────
SKIP_INFRA=false
SKIP_MIGRATIONS=false
for arg in "$@"; do
  case "$arg" in
    --skip-infra)       SKIP_INFRA=true ;;
    --skip-migrations)  SKIP_MIGRATIONS=true ;;
  esac
done

# ─── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'
log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[ OK ]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERR ]${NC} $*" >&2; }
log_step()  { echo -e "\n${BOLD}${BLUE}▶ $*${NC}"; }

# ─── 1. Prerequisite checks ────────────────────────────────────────────────────
check_prereqs() {
  log_step "Checking prerequisites"
  local missing=0

  if ! command -v docker &>/dev/null; then
    log_error "docker not found. Install: sudo apt-get install -y docker.io"
    missing=1
  fi
  if ! docker compose version &>/dev/null 2>&1; then
    log_error "docker compose plugin not found. Install: sudo apt-get install -y docker-compose-plugin"
    missing=1
  fi
  if ! command -v java &>/dev/null; then
    log_error "java not found. Install: sudo apt-get install -y openjdk-21-jdk"
    missing=1
  else
    local java_ver
    java_ver=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [[ "${java_ver}" -lt 21 ]]; then
      log_error "Java 21+ required (found ${java_ver}). Install: sudo apt-get install -y openjdk-21-jdk"
      missing=1
    fi
  fi
  if ! command -v node &>/dev/null; then
    log_error "node not found. Install via nvm (https://github.com/nvm-sh/nvm) or sudo apt-get install -y nodejs"
    missing=1
  fi
  if ! command -v npm &>/dev/null; then
    log_error "npm not found. Install alongside node."
    missing=1
  fi
  if ! command -v curl &>/dev/null; then
    log_error "curl not found. Install: sudo apt-get install -y curl"
    missing=1
  fi

  [[ $missing -eq 0 ]] || { log_error "Fix the above prerequisites and re-run."; exit 1; }
  log_ok "All prerequisites satisfied."
}

# ─── 2. Environment file setup ─────────────────────────────────────────────────
setup_env() {
  log_step "Setting up environment"
  if [[ ! -f "${INFRA_DIR}/.env" ]]; then
    log_info "Copying .env.example → .env (fill in real passwords before production use)"
    cp "${INFRA_DIR}/.env.example" "${INFRA_DIR}/.env"
    log_warn ".env created with default placeholder values."
    log_warn "Press Enter to continue with defaults, or Ctrl+C to abort and edit ${INFRA_DIR}/.env first."
    read -r
  else
    log_ok ".env already exists — skipping copy."
  fi

  # Export all non-comment, non-empty variables from .env
  set -a
  # shellcheck source=/dev/null
  source "${INFRA_DIR}/.env"
  set +a
  log_ok "Environment variables loaded from .env."
}

# ─── 3. Docker infrastructure ──────────────────────────────────────────────────
start_infrastructure() {
  log_step "Starting Docker Compose services"
  docker compose -f "${COMPOSE_FILE}" --project-directory "${INFRA_DIR}" up -d
  log_ok "Docker services started (or already running)."
}

wait_for_container_healthy() {
  local container="$1"
  local max_wait="${2:-180}"
  local elapsed=0

  log_info "Waiting for ${container} to become healthy (up to ${max_wait}s)..."
  while [[ $elapsed -lt $max_wait ]]; do
    local status
    status=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' \
      "${container}" 2>/dev/null || echo "missing")
    case "$status" in
      healthy|no-healthcheck)
        log_ok "${container} — ${status}."
        return 0
        ;;
      starting) ;;
      unhealthy)
        log_error "${container} reported unhealthy. Check: docker logs ${container}"
        return 1
        ;;
    esac
    sleep 5
    elapsed=$((elapsed + 5))
    printf '.'
  done
  echo ''
  log_error "${container} did not become healthy within ${max_wait}s."
  return 1
}

wait_for_all_healthy() {
  log_step "Waiting for all services to be healthy"
  wait_for_container_healthy "grc_sqlserver" 120
  wait_for_container_healthy "grc_neo4j"     180
  wait_for_container_healthy "grc_redis"      60
  wait_for_container_healthy "grc_keycloak"  180
}

# ─── 4. Database initialisation (idempotent) ───────────────────────────────────
init_database() {
  log_step "Initialising SQL Server database (idempotent)"

  # Verify the init script exists inside the container (mounted at start)
  docker exec grc_sqlserver \
    /opt/mssql-tools18/bin/sqlcmd \
      -S localhost \
      -U sa \
      -P "${SQLSERVER_SA_PASSWORD}" \
      -v GRC_DB_PASSWORD="${GRC_DB_PASSWORD}" \
      -i /docker-entrypoint-initdb.d/01-init-db.sql \
      -No

  log_ok "Database initialised (IF NOT EXISTS guards ensure idempotency)."
}

# ─── 5. Liquibase migrations (idempotent) ──────────────────────────────────────
run_migrations() {
  log_step "Applying Liquibase migrations (idempotent)"

  # Detect the compose project network (folder-name_grc_net)
  local network
  network=$(docker network ls --format '{{.Name}}' | grep 'grc_net' | head -1)
  if [[ -z "${network}" ]]; then
    log_error "Could not find grc_net Docker network. Is Docker Compose running?"
    return 1
  fi
  log_info "Using Docker network: ${network}"

  # Mount backend/db/migrations as /liquibase/changelog and add it to searchPath
  # Run as sa so Liquibase has DDL rights for schema creation
  docker run --rm \
    --network "${network}" \
    -v "${BACKEND_DIR}/db/migrations:/liquibase/changelog:ro" \
    liquibase/liquibase:4.28 \
      --url="jdbc:sqlserver://grc_sqlserver:1433;databaseName=grcplatform;encrypt=true;trustServerCertificate=true" \
      --username=sa \
      --password="${SQLSERVER_SA_PASSWORD}" \
      --changeLogFile=changelog-master.xml \
      --searchPath=/liquibase/changelog \
      --contexts=main \
      --logLevel=info \
      update

  log_ok "Liquibase migrations applied (already-applied changesets skipped)."
}

# ─── 6. Backend (Spring Boot) ─────────────────────────────────────────────────
is_port_listening() {
  local port="$1"
  # Use nc (netcat) for a cross-tool check; fall back to /proc/net/tcp
  if command -v nc &>/dev/null; then
    nc -z localhost "${port}" 2>/dev/null
  elif command -v ss &>/dev/null; then
    ss -tlnp 2>/dev/null | grep -q ":${port} "
  else
    grep -q "$(printf ':%.4X' "${port}")" /proc/net/tcp 2>/dev/null
  fi
}

start_backend() {
  log_step "Starting Spring Boot backend"
  mkdir -p "${LOGS_DIR}" "${PID_DIR}"

  # ── Bootstrap Gradle wrapper jar if missing (not committed to repo) ──────
  if [[ ! -f "${BACKEND_DIR}/gradle/wrapper/gradle-wrapper.jar" ]]; then
    log_warn "gradle-wrapper.jar not found — attempting to generate it..."
    local gradle_cmd=""
    # Try SDKMAN Gradle first (modern version)
    if [[ -f "${HOME}/.sdkman/bin/sdkman-init.sh" ]]; then
      # shellcheck source=/dev/null
      source "${HOME}/.sdkman/bin/sdkman-init.sh"
    fi
    if command -v gradle &>/dev/null && gradle --version 2>/dev/null | grep -q 'Gradle [7-9]'; then
      gradle_cmd="gradle"
    elif command -v gradle &>/dev/null; then
      # Even an older gradle can create a wrapper (ignoring the build script by running from empty dir)
      gradle_cmd="gradle"
    fi
    if [[ -n "${gradle_cmd}" ]]; then
      (
        cd "${BACKEND_DIR}"
        ${gradle_cmd} wrapper --gradle-version=9.2.0 2>&1 | tail -3
      ) || true
    fi
    if [[ ! -f "${BACKEND_DIR}/gradle/wrapper/gradle-wrapper.jar" ]]; then
      log_error "Could not generate gradle-wrapper.jar automatically."
      log_error "Run manually: sdk install gradle && cd backend && gradle wrapper --gradle-version=9.2.0"
      exit 1
    fi
    log_ok "gradle-wrapper.jar generated."
  fi

  if is_port_listening "${BACKEND_PORT}"; then
    log_ok "Backend already listening on port ${BACKEND_PORT} — skipping start."
    return 0
  fi

  log_info "Launching backend (./gradlew :platform-api:bootRun) — logs → ${LOGS_DIR}/backend.log"

  # Ensure gradlew is executable (git may not preserve the bit on some clones)
  chmod +x "${BACKEND_DIR}/gradlew"

  # Export env vars the backend needs at runtime
  export GRC_DB_USER=sa
  export GRC_DB_PASSWORD="${SQLSERVER_SA_PASSWORD}"
  export REDIS_PASSWORD="${REDIS_PASSWORD}"
  export KEYCLOAK_ISSUER_URI="http://localhost:8080/realms/grc-platform"
  export SPRING_PROFILES_ACTIVE=local

  (
    cd "${BACKEND_DIR}"
    ./gradlew :platform-api:bootRun \
      >> "${LOGS_DIR}/backend.log" 2>&1
  ) &
  local pid=$!
  echo "${pid}" > "${PID_DIR}/backend.pid"
  log_ok "Backend started in background (PID ${pid})."
}

# ─── 7. Frontend (Vite) ────────────────────────────────────────────────────────
start_frontend() {
  log_step "Starting Vite frontend"
  mkdir -p "${LOGS_DIR}" "${PID_DIR}"

  if is_port_listening "${FRONTEND_PORT}"; then
    log_ok "Frontend already listening on port ${FRONTEND_PORT} — skipping start."
    return 0
  fi

  log_info "Installing npm dependencies (idempotent)..."
  (cd "${FRONTEND_DIR}" && npm install --silent)

  log_info "Launching frontend (npm run dev) — logs → ${LOGS_DIR}/frontend.log"
  (
    cd "${FRONTEND_DIR}"
    npm run dev >> "${LOGS_DIR}/frontend.log" 2>&1
  ) &
  local pid=$!
  echo "${pid}" > "${PID_DIR}/frontend.pid"
  log_ok "Frontend started in background (PID ${pid})."
}

# ─── 8. Health verification ────────────────────────────────────────────────────
wait_for_url() {
  local url="$1"
  local label="$2"
  local max_seconds="${3:-180}"
  local elapsed=0

  log_info "Waiting for ${label} → ${url}"
  while [[ $elapsed -lt $max_seconds ]]; do
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${url}" 2>/dev/null || echo "000")
    # Accept any response that isn't a connection error (000) or server error (5xx)
    if [[ "${http_code}" != "000" && "${http_code:0:1}" != "5" ]]; then
      log_ok "${label} is reachable (HTTP ${http_code})."
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    printf '.'
  done
  echo ''
  log_warn "${label} not reachable after ${max_seconds}s. Check: tail -f ${LOGS_DIR}/backend.log"
  return 0   # non-fatal
}

verify_services() {
  log_step "Verifying service health"
  # Give processes a few seconds to bind their ports
  sleep 8
  wait_for_url "http://localhost:${BACKEND_PORT}/actuator/health" "Spring Boot actuator" 180
  wait_for_url "http://localhost:${FRONTEND_PORT}"                "Vite dev server"       120
}

# ─── 9. Summary ───────────────────────────────────────────────────────────────
print_summary() {
  echo ""
  echo -e "${GREEN}${BOLD}╔══════════════════════════════════════════════════════════╗${NC}"
  echo -e "${GREEN}${BOLD}║        GRC Platform — Dev Environment Ready              ║${NC}"
  echo -e "${GREEN}${BOLD}╚══════════════════════════════════════════════════════════╝${NC}"
  echo ""
  echo -e "  ${BLUE}${BOLD}Application${NC}"
  echo -e "  Frontend      →  http://localhost:${FRONTEND_PORT}"
  echo -e "  GRC API       →  http://localhost:${BACKEND_PORT}"
  echo -e "  GraphiQL      →  http://localhost:${BACKEND_PORT}/graphql  (POST)"
  echo -e "  Health        →  http://localhost:${BACKEND_PORT}/actuator/health"
  echo ""
  echo -e "  ${BLUE}${BOLD}Infrastructure${NC}"
  echo -e "  Keycloak      →  http://localhost:8080  (admin: ${KEYCLOAK_ADMIN:-admin})"
  echo -e "  Neo4j Browser →  http://localhost:7474"
  echo -e "  SQL Server    →  localhost:1433"
  echo -e "  Redis         →  localhost:6379"
  echo ""
  echo -e "  ${YELLOW}${BOLD}Logs${NC}"
  echo -e "  Backend       →  ${LOGS_DIR}/backend.log"
  echo -e "  Frontend      →  ${LOGS_DIR}/frontend.log"
  echo ""
  echo -e "  ${YELLOW}${BOLD}Useful commands${NC}"
  echo -e "  Stop all      →  ./infrastructure/scripts/dev-teardown.sh"
  echo -e "  Backend logs  →  tail -f ${LOGS_DIR}/backend.log"
  echo -e "  Frontend logs →  tail -f ${LOGS_DIR}/frontend.log"
  echo -e "  Docker logs   →  docker compose -f ${COMPOSE_FILE} logs -f"
  echo ""
}

# ─── Main ─────────────────────────────────────────────────────────────────────
main() {
  echo ""
  echo -e "${BLUE}${BOLD}  GRC Platform — Local Dev Setup (Ubuntu / bash)${NC}"
  echo -e "  $(date)"
  echo ""

  check_prereqs
  setup_env

  if [[ "${SKIP_INFRA}" == false ]]; then
    start_infrastructure
    wait_for_all_healthy
    init_database
  else
    log_warn "--skip-infra: Docker services step skipped."
  fi

  if [[ "${SKIP_MIGRATIONS}" == false ]]; then
    run_migrations
  else
    log_warn "--skip-migrations: Liquibase step skipped."
  fi

  start_backend
  start_frontend
  verify_services
  print_summary
}

main "$@"
