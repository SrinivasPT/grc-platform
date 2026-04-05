# Infrastructure

Local development environment and CI/CD configuration for the GRC Platform.

---

## Quick Start

Two script flavours are provided in `scripts/` — pick the one for your OS:

| OS | Script | Purpose |
|----|--------|---------|
| **Ubuntu / Linux / macOS** | `scripts/dev-setup.sh` | Full idempotent setup |
| **Windows (PowerShell)** | `scripts/dev-setup.ps1` | Full idempotent setup |

### Ubuntu / Linux (canonical)

```bash
cd infrastructure/scripts
./dev-setup.sh
```

### Windows

```powershell
cd infrastructure\scripts
.\dev-setup.ps1
```

Both scripts are **idempotent** — safe to run multiple times without breaking anything.

---

## What the scripts do (in order)

1. Check prerequisites (Docker, Java 21, Node 20, npm, curl)
2. Copy `.env.example` → `.env` if it doesn't exist
3. `docker compose up -d` (all 4 services)
4. Wait for all containers to be healthy
5. Initialise SQL Server database (`IF NOT EXISTS` guards)
6. Apply Liquibase migrations via Docker (skipped if already applied)
7. Start Spring Boot backend in the background (port 8090)
8. Install npm deps + start Vite frontend in the background (port 3000)
9. Verify all URLs and print a summary

---

## Service URLs

| Service | URL | Notes |
|---------|-----|-------|
| GRC Frontend | http://localhost:3000 | Vite dev server |
| GRC API | http://localhost:8090 | Spring Boot |
| GraphQL | http://localhost:8090/graphql | POST; use GraphiQL below |
| GraphiQL Playground | http://localhost:8090/graphiql | Enabled in `local` profile |
| Actuator Health | http://localhost:8090/actuator/health | Requires auth (401 = running) |
| Keycloak | http://localhost:8080 | Admin: see `.env` |
| Neo4j Browser | http://localhost:7474 | Auth: see `.env` |
| SQL Server | localhost:1433 | SA + see `.env` |
| Redis | localhost:6379 | Auth: see `.env` |

---

## Teardown

```bash
# Stop app processes only (Docker keeps running)
./scripts/dev-teardown.sh

# Stop app + Docker services (volumes preserved)
./scripts/dev-teardown.sh --all

# Nuclear reset — destroys all Docker volumes (DATA LOSS)
./scripts/dev-teardown.sh --all --wipe
```

Windows:
```powershell
.\scripts\dev-teardown.ps1 -All -Wipe
```

---

## Flags

| Flag (bash) | Flag (PS1) | Effect |
|-------------|------------|--------|
| `--skip-infra` | `-SkipInfra` | Skip Docker start + health wait |
| `--skip-migrations` | `-SkipMigrations` | Skip Liquibase step |

Useful when Docker is already running: `./dev-setup.sh --skip-infra --skip-migrations`

---

## Prerequisites

- Docker Engine + Compose plugin (`sudo apt-get install -y docker-compose-plugin`)
- Java 21 JDK (`sudo apt-get install -y openjdk-21-jdk`)
- Gradle wrapper (`gradle wrapper --gradle-version=9.2.0` if `gradle-wrapper.jar` is absent)
- Node.js 20+ (`curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - && sudo apt-get install -y nodejs`)

---

## CI/CD (Tekton)

Tekton tasks and pipelines are in `tekton/`. Apply to the cluster:

```bash
kubectl apply -f tekton/task-gradle-build.yaml
kubectl apply -f tekton/task-integration-test.yaml
kubectl apply -f tekton/task-liquibase-migrate.yaml
kubectl apply -f tekton/task-sonar-scan.yaml
kubectl apply -f tekton/pipeline-build.yaml
```

