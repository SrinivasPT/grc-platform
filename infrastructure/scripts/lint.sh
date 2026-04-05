#!/usr/bin/env bash
# lint.sh — Run all linters across the GRC Platform (Java + TypeScript/React)
# Usage: ./infrastructure/scripts/lint.sh [--fix]
#
# Options:
#   --fix   Attempt auto-fix where supported (ESLint --fix, tsc is check-only)
#
# Exit codes:
#   0 — all linters passed
#   1 — one or more linters reported errors

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
FRONTEND_DIR="${ROOT_DIR}/frontend"

FIX_MODE=false
if [[ "${1:-}" == "--fix" ]]; then
  FIX_MODE=true
fi

# ─── Colour helpers ───────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[PASS]${RESET}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[FAIL]${RESET}  $*"; }
header()  { echo -e "\n${BOLD}${CYAN}══════════════════════════════════════════${RESET}"; \
            echo -e "${BOLD}${CYAN}  $*${RESET}"; \
            echo -e "${BOLD}${CYAN}══════════════════════════════════════════${RESET}"; }

OVERALL_EXIT=0

# ─── Tool availability checks ─────────────────────────────────────────────────
header "Checking required tools"

require_tool() {
  local tool="$1"
  local install_hint="$2"
  if ! command -v "${tool}" &>/dev/null; then
    error "Tool not found: ${tool}. ${install_hint}"
    OVERALL_EXIT=1
    echo "$tool"   # return sentinel to caller
  else
    info "Found: $(command -v "${tool}")"
    echo ""
  fi
}

MISSING_JAVA=$(require_tool "java"    "Install: sudo apt-get install -y openjdk-21-jdk")
MISSING_NODE=$(require_tool "node"    "Install: https://nodejs.org  (recommend nvm)")
MISSING_NPM=$( require_tool "npm"     "Install: sudo apt-get install -y npm")

# Gradle wrapper is checked separately — it only needs java
if [[ ! -x "${BACKEND_DIR}/gradlew" ]]; then
  error "Gradle wrapper not found at backend/gradlew. Run 'gradle wrapper' inside backend/."
  OVERALL_EXIT=1
  MISSING_GRADLE="gradlew"
else
  info "Found Gradle wrapper: ${BACKEND_DIR}/gradlew"
  MISSING_GRADLE=""
fi

if [[ "${OVERALL_EXIT}" -ne 0 ]]; then
  error "One or more required tools are missing. Install them and re-run."
  exit "${OVERALL_EXIT}"
fi

# ─── Java / Gradle linting ────────────────────────────────────────────────────
header "Java — Gradle check (compile + test + Checkstyle)"

RUN_GRADLE=true
if [[ -n "${MISSING_JAVA}" || -n "${MISSING_GRADLE}" ]]; then
  warn "Skipping Java linting — missing tools."
  RUN_GRADLE=false
fi

if [[ "${RUN_GRADLE}" == "true" ]]; then
  info "Running: ./gradlew check (in ${BACKEND_DIR})"
  (
    cd "${BACKEND_DIR}"
    if ./gradlew check --continue 2>&1; then
      success "Gradle check passed"
    else
      error "Gradle check reported errors"
      OVERALL_EXIT=1
    fi
  ) || OVERALL_EXIT=1
fi

# ─── TypeScript type checking ─────────────────────────────────────────────────
header "TypeScript — type check (tsc --noEmit)"

RUN_TSC=true
if [[ -n "${MISSING_NODE}" || -n "${MISSING_NPM}" ]]; then
  warn "Skipping TypeScript checks — missing node/npm."
  RUN_TSC=false
fi

if [[ "${RUN_TSC}" == "true" ]]; then
  if [[ ! -d "${FRONTEND_DIR}/node_modules" ]]; then
    info "node_modules not found — running npm ci first..."
    (cd "${FRONTEND_DIR}" && npm ci --silent)
  fi

  info "Running: npm run typecheck (in ${FRONTEND_DIR})"
  (
    cd "${FRONTEND_DIR}"
    if npm run typecheck 2>&1; then
      success "TypeScript type check passed"
    else
      error "TypeScript type check reported errors"
      OVERALL_EXIT=1
    fi
  ) || OVERALL_EXIT=1
fi

# ─── ESLint (TypeScript / React) ─────────────────────────────────────────────
header "ESLint — TypeScript / React"

if [[ "${RUN_TSC}" == "true" ]]; then
  if [[ "${FIX_MODE}" == "true" ]]; then
    info "Running: npm run lint -- --fix (in ${FRONTEND_DIR})"
    ESLINT_CMD="npm run lint -- --fix"
  else
    info "Running: npm run lint (in ${FRONTEND_DIR})"
    ESLINT_CMD="npm run lint"
  fi

  (
    cd "${FRONTEND_DIR}"
    if eval "${ESLINT_CMD}" 2>&1; then
      success "ESLint passed"
    else
      error "ESLint reported errors"
      if [[ "${FIX_MODE}" == "false" ]]; then
        warn "Tip: run with --fix to auto-correct fixable issues"
      fi
      OVERALL_EXIT=1
    fi
  ) || OVERALL_EXIT=1
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
header "Summary"
if [[ "${OVERALL_EXIT}" -eq 0 ]]; then
  success "All linters passed"
else
  error "One or more linters reported failures — see output above"
fi

exit "${OVERALL_EXIT}"
