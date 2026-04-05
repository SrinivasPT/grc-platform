# Infrastructure

Local development environment and CI/CD configuration for the GRC Platform.

## Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- Java 21 (for Gradle commands)
- Node.js 20+ / pnpm (for frontend)

---

## Local Development Setup

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env — fill in secure passwords
```

### 2. Start all services

```bash
docker-compose up -d
```

### 3. Initialize the database

On first start, create the app database and user:

```bash
docker exec grc_sqlserver \
  /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "$SQLSERVER_SA_PASSWORD" \
  -i /docker-entrypoint-initdb.d/01-init-db.sql -No
```

### 4. Run Liquibase migrations

```bash
cd ../backend
./gradlew :db:liquibaseUpdate
```

### 5. Start the backend

```bash
./gradlew :platform-api:bootRun
```

### 6. Start the frontend

```bash
cd ../frontend
pnpm install
pnpm dev
```

---

## Service URLs

| Service | URL | Notes |
|---------|-----|-------|
| GRC API | http://localhost:8090 | Spring Boot |
| GRC Frontend | http://localhost:3000 | Vite dev server |
| GraphQL Playground | http://localhost:8090/graphiql | Enable in `application-local.yml` |
| Keycloak | http://localhost:8080 | Admin: see `.env` |
| Neo4j Browser | http://localhost:7474 | Auth: see `.env` |
| SQL Server | localhost:1433 | Auth: SA + see `.env` |
| Redis | localhost:6379 | Auth: see `.env` |

---

## Reset Commands

```bash
# Reset SQL Server only (preserve Neo4j and Redis)
docker-compose rm -sf sqlserver && docker-compose up -d sqlserver

# Full environment reset (WARNING: destroys all data)
docker-compose down -v && docker-compose up -d

# Re-run migrations after reset
cd ../backend && ./gradlew :db:liquibaseUpdate
```

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

See each YAML file for parameter documentation.
