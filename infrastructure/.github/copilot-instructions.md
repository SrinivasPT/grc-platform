# Infrastructure — Copilot Instructions

Extends the global `.github/copilot-instructions.md`. All global rules apply.

## OS & Shell (Ubuntu Linux)

- All shell scripts use `#!/usr/bin/env bash` and `set -euo pipefail`.
- When generating terminal commands for this module, **always use bash / Ubuntu syntax**.
- Do not generate PowerShell or Windows commands unless adding to the `*.ps1` companion script.
- The canonical dev setup script is `infrastructure/scripts/dev-setup.sh`.

---

## Purpose

`infrastructure/` contains all deployment and environment configuration:
- Docker Compose for local development
- Tekton pipeline definitions for CI/CD
- Keycloak realm configuration
- Kubernetes manifests (future: Phase 5)

---

## Docker Compose Rules

- **Never hardcode credentials** in `docker-compose.yml`. All secrets via `${ENV_VAR}` resolved from `infrastructure/.env`.
- `infrastructure/.env` is gitignored. `infrastructure/.env.example` is committed with placeholder values.
- All services must have `restart: unless-stopped` and health checks.
- Volume names: `grc_{service}_data` (e.g., `grc_sqlserver_data`, `grc_neo4j_data`).
- Network: all services on `grc_net` bridge network — no default bridge.

---

## Tekton Pipeline Rules

- Pipeline YAML: `kebab-case` names (e.g., `pipeline-build`, `task-gradle-build`).
- Every pipeline step must set `resources.requests` and `resources.limits`.
- Secrets in Tekton: use `secretKeyRef` — never `value:` with literal credentials.
- Pipeline parameters: always document with `description` field.


- Task results: use `$(results.RESULT_NAME.path)` — never echo to stdout and parse.

---

## Quality Gate Pipeline Requirements

All of these must pass before a merge is allowed:
1. `./gradlew build` — compiles all modules
2. `./gradlew test` — all unit tests green
3. `./gradlew integrationTest` — Testcontainers integration tests green
4. SonarQube scan — 0 Critical/Blocker, coverage ≥ 90% service layer
5. Checkmarx SAST — 0 High/Critical findings
6. `./gradlew :db:liquibaseValidate` — all migration XML valid

---

## Keycloak Configuration

- Realm is defined in `infrastructure/keycloak/realm-export.json`.
- The realm export is the **source of truth** — never configure Keycloak via UI without updating the export.
- Client credentials in the realm export use placeholder vars (`${KEYCLOAK_CLIENT_SECRET}`) — resolved at runtime.
- Identity provider (Ping Identity SAML) is pre-configured in the realm export.

---

## Environment Variable Catalog

| Variable | Description | Example |
|----------|-------------|---------|
| `SQLSERVER_SA_PASSWORD` | SQL Server SA password | (from vault) |
| `NEO4J_AUTH` | Neo4j auth string | `neo4j/password` |
| `REDIS_PASSWORD` | Redis password | (from vault) |
| `KEYCLOAK_ADMIN` | Keycloak admin user | `admin` |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak admin password | (from vault) |
| `GRC_DB_URL` | App DB connection URL | `jdbc:sqlserver://sqlserver:1433;databaseName=grc` |
| `GRC_DB_USER` | App DB username | `grc_app` |
| `GRC_DB_PASSWORD` | App DB password | (from vault) |

---

## Agent Checklist (Infrastructure)

1. Does the new service have a healthcheck in `docker-compose.yml`?
2. Are all credentials sourced from `${ENV_VAR}` — no literals?
3. Does the Tekton task set resource limits?
4. Is the Keycloak realm export updated if realm configuration changed?
