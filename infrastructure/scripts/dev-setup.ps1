#Requires -Version 5.1
<#
.SYNOPSIS
    GRC Platform — Local Development Setup Script (Windows PowerShell)

.DESCRIPTION
    Idempotent setup script for Windows. Running multiple times is safe:
    - Docker services are started/resumed (not recreated if already healthy)
    - DB init uses IF NOT EXISTS guards
    - Liquibase tracks applied changesets and skips already-applied ones
    - Backend / frontend processes are skipped if their ports are already in use

.PARAMETER SkipInfra
    Skip Docker Compose start and wait-for-healthy steps.

.PARAMETER SkipMigrations
    Skip the Liquibase migration step.

.EXAMPLE
    .\dev-setup.ps1
    .\dev-setup.ps1 -SkipInfra
    .\dev-setup.ps1 -SkipMigrations
#>
[CmdletBinding()]
param(
    [switch]$SkipInfra,
    [switch]$SkipMigrations
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ─── Paths ─────────────────────────────────────────────────────────────────────
$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$InfraDir    = Split-Path -Parent $ScriptDir
$RepoRoot    = Split-Path -Parent $InfraDir
$BackendDir  = Join-Path $RepoRoot   'backend'
$FrontendDir = Join-Path $RepoRoot   'frontend'
$LogsDir     = Join-Path $InfraDir   'logs'
$PidDir      = Join-Path $InfraDir   '.pids'
$ComposeFile = Join-Path $InfraDir   'docker-compose.yml'
$EnvFile     = Join-Path $InfraDir   '.env'
$EnvExample  = Join-Path $InfraDir   '.env.example'

# ─── Ports ─────────────────────────────────────────────────────────────────────
$BackendPort  = 8090
$FrontendPort = 3000  # Configured in frontend/vite.config.ts

# ─── Helpers ───────────────────────────────────────────────────────────────────
function Write-Step   { param($msg) Write-Host "`n▶ $msg" -ForegroundColor Cyan }
function Write-Ok     { param($msg) Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Write-Warn   { param($msg) Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err    { param($msg) Write-Host "[ERR ] $msg" -ForegroundColor Red }
function Write-Inf    { param($msg) Write-Host "[INFO] $msg" }

function Test-CommandExists {
    param([string]$Name)
    $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-PortListening {
    param([int]$Port)
    $conn = New-Object System.Net.Sockets.TcpClient
    try {
        $conn.Connect('127.0.0.1', $Port)
        return $conn.Connected
    } catch {
        return $false
    } finally {
        $conn.Dispose()
    }
}

# ─── Load .env file ────────────────────────────────────────────────────────────
function Import-EnvFile {
    param([string]$Path)
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line -split '=', 2
            if ($parts.Count -eq 2) {
                $key   = $parts[0].Trim()
                $value = $parts[1].Trim()
                [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
}

# ─── 1. Prerequisites ──────────────────────────────────────────────────────────
function Test-Prerequisites {
    Write-Step 'Checking prerequisites'
    $missing = 0

    if (-not (Test-CommandExists 'docker')) {
        Write-Err 'docker not found. Install Docker Desktop from https://www.docker.com/products/docker-desktop/'
        $missing++
    } else {
        $composeOk = docker compose version 2>$null
        if (-not $composeOk) {
            Write-Err 'docker compose plugin not found. Update Docker Desktop to include Compose v2.'
            $missing++
        }
    }

    if (-not (Test-CommandExists 'java')) {
        Write-Err 'java not found. Install OpenJDK 21 from https://adoptium.net/'
        $missing++
    } else {
        $javaOut = java -version 2>&1 | Out-String
        if ($javaOut -notmatch '"21') {
            Write-Err "Java 21 required. Found: $javaOut"
            $missing++
        }
    }

    if (-not (Test-CommandExists 'node')) {
        Write-Err 'node not found. Install from https://nodejs.org/ or via nvm-windows.'
        $missing++
    }
    if (-not (Test-CommandExists 'npm')) {
        Write-Err 'npm not found. Comes bundled with Node.js.'
        $missing++
    }

    if ($missing -gt 0) {
        Write-Err "Fix $missing missing prerequisite(s) and re-run."
        exit 1
    }
    Write-Ok 'All prerequisites satisfied.'
}

# ─── 2. Environment setup ──────────────────────────────────────────────────────
function Set-EnvironmentFile {
    Write-Step 'Setting up environment'
    if (-not (Test-Path $EnvFile)) {
        Write-Inf "Copying .env.example → .env"
        Copy-Item $EnvExample $EnvFile
        Write-Warn '.env created with placeholder values. Edit it before production use.'
        Write-Warn 'Press Enter to continue with defaults, or Ctrl+C to abort.'
        Read-Host | Out-Null
    } else {
        Write-Ok '.env already exists — skipping copy.'
    }
    Import-EnvFile $EnvFile
    Write-Ok 'Environment variables loaded.'
}

# ─── 3. Docker infrastructure ──────────────────────────────────────────────────
function Start-Infrastructure {
    Write-Step 'Starting Docker Compose services'
    docker compose -f $ComposeFile --project-directory $InfraDir up -d
    Write-Ok 'Docker services started (or already running).'
}

function Wait-ContainerHealthy {
    param([string]$Container, [int]$MaxSeconds = 180)
    Write-Inf "Waiting for $Container to become healthy (up to ${MaxSeconds}s)..."
    $elapsed = 0
    while ($elapsed -lt $MaxSeconds) {
        $status = docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' $Container 2>$null
        switch ($status) {
            'healthy'        { Write-Ok "$Container — healthy."; return }
            'no-healthcheck' { Write-Ok "$Container — no healthcheck (assumed ready)."; return }
            'unhealthy'      { Write-Err "$Container is unhealthy. Run: docker logs $Container"; throw "Unhealthy container: $Container" }
        }
        Start-Sleep -Seconds 5
        $elapsed += 5
        Write-Host -NoNewline '.'
    }
    Write-Host ''
    throw "$Container did not become healthy within ${MaxSeconds}s."
}

function Wait-AllHealthy {
    Write-Step 'Waiting for all services to be healthy'
    Wait-ContainerHealthy 'grc_sqlserver' 120
    Wait-ContainerHealthy 'grc_neo4j'     180
    Wait-ContainerHealthy 'grc_redis'      60
    Wait-ContainerHealthy 'grc_keycloak'  180
}

# ─── 4. Database initialisation ────────────────────────────────────────────────
function Initialize-Database {
    Write-Step 'Initialising SQL Server database (idempotent)'
    $saPass = [System.Environment]::GetEnvironmentVariable('SQLSERVER_SA_PASSWORD', 'Process')
    $appPass = [System.Environment]::GetEnvironmentVariable('GRC_DB_PASSWORD', 'Process')
    docker exec grc_sqlserver `
        /opt/mssql-tools18/bin/sqlcmd `
        -S localhost -U sa -P $saPass `
        -v GRC_DB_PASSWORD=$appPass `
        -i /docker-entrypoint-initdb.d/01-init-db.sql -No
    Write-Ok 'Database initialised.'
}

# ─── 5. Liquibase migrations ───────────────────────────────────────────────────
function Invoke-Migrations {
    Write-Step 'Applying Liquibase migrations (idempotent)'
    $network = docker network ls --format '{{.Name}}' | Where-Object { $_ -like '*grc_net*' } | Select-Object -First 1
    if (-not $network) {
        throw 'Could not find grc_net Docker network. Is Docker Compose running?'
    }
    Write-Inf "Using Docker network: $network"

    $saPass = [System.Environment]::GetEnvironmentVariable('SQLSERVER_SA_PASSWORD', 'Process')
    $migDir = Join-Path $BackendDir 'db\migrations'

    docker run --rm `
        --network $network `
        -v "${migDir}:/liquibase/changelog:ro" `
        liquibase/liquibase:4.28 `
        --url="jdbc:sqlserver://grc_sqlserver:1433;databaseName=grcplatform;encrypt=true;trustServerCertificate=true" `
        --username=sa `
        --password=$saPass `
        --changeLogFile=changelog-master.xml `
        --searchPath=/liquibase/changelog `
        --contexts=main `
        --logLevel=info `
        update

    Write-Ok 'Liquibase migrations applied.'
}

# ─── 6. Backend ────────────────────────────────────────────────────────────────
function Start-Backend {
    Write-Step 'Starting Spring Boot backend'
    New-Item -ItemType Directory -Force -Path $LogsDir, $PidDir | Out-Null
    $logFile = Join-Path $LogsDir 'backend.log'
    $pidFile = Join-Path $PidDir  'backend.pid'

    if (Test-PortListening $BackendPort) {
        Write-Ok "Backend already listening on port $BackendPort — skipping."
        return
    }

    $saPass = [System.Environment]::GetEnvironmentVariable('SQLSERVER_SA_PASSWORD', 'Process')
    $redisPass = [System.Environment]::GetEnvironmentVariable('REDIS_PASSWORD', 'Process')

    $env:GRC_DB_USER   = 'sa'
    $env:GRC_DB_PASSWORD = $saPass
    $env:REDIS_PASSWORD  = $redisPass
    $env:KEYCLOAK_ISSUER_URI = 'http://localhost:8080/realms/grc-platform'
    $env:SPRING_PROFILES_ACTIVE = 'local'

    Write-Inf "Launching backend — logs → $logFile"
    $proc = Start-Process -FilePath (Join-Path $BackendDir 'gradlew.bat') `
        -ArgumentList ':platform-api:bootRun' `
        -WorkingDirectory $BackendDir `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError  ($logFile -replace '\.log$', '-err.log') `
        -WindowStyle Hidden `
        -PassThru

    $proc.Id | Out-File $pidFile -Encoding ASCII
    Write-Ok "Backend started (PID $($proc.Id))."
}

# ─── 7. Frontend ───────────────────────────────────────────────────────────────
function Start-Frontend {
    Write-Step 'Starting Vite frontend'
    New-Item -ItemType Directory -Force -Path $LogsDir, $PidDir | Out-Null
    $logFile = Join-Path $LogsDir 'frontend.log'
    $pidFile = Join-Path $PidDir  'frontend.pid'

    if (Test-PortListening $FrontendPort) {
        Write-Ok "Frontend already listening on port $FrontendPort — skipping."
        return
    }

    Write-Inf 'Installing npm dependencies (idempotent)...'
    Push-Location $FrontendDir
    & npm install --silent
    Pop-Location

    Write-Inf "Launching frontend — logs → $logFile"
    $proc = Start-Process -FilePath 'npm' `
        -ArgumentList 'run', 'dev' `
        -WorkingDirectory $FrontendDir `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError  ($logFile -replace '\.log$', '-err.log') `
        -WindowStyle Hidden `
        -PassThru

    $proc.Id | Out-File $pidFile -Encoding ASCII
    Write-Ok "Frontend started (PID $($proc.Id))."
}

# ─── 8. Verify ─────────────────────────────────────────────────────────────────
function Wait-Url {
    param([string]$Url, [string]$Label, [int]$MaxSeconds = 180)
    Write-Inf "Waiting for $Label → $Url"
    $elapsed = 0
    while ($elapsed -lt $MaxSeconds) {
        try {
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if ($resp.StatusCode -lt 500) {
                Write-Ok "$Label is reachable (HTTP $($resp.StatusCode))."
                return
            }
        } catch { }
        Start-Sleep -Seconds 5
        $elapsed += 5
        Write-Host -NoNewline '.'
    }
    Write-Host ''
    Write-Warn "$Label not reachable after ${MaxSeconds}s. Check: Get-Content $LogsDir\backend.log -Tail 50"
}

function Confirm-Services {
    Write-Step 'Verifying service health'
    Start-Sleep -Seconds 8
    Wait-Url "http://localhost:${BackendPort}/actuator/health" 'Spring Boot actuator' 180
    Wait-Url "http://localhost:${FrontendPort}"                'Vite dev server'       120
}

# ─── 9. Summary ────────────────────────────────────────────────────────────────
function Show-Summary {
    $keycloakAdmin = [System.Environment]::GetEnvironmentVariable('KEYCLOAK_ADMIN', 'Process')
    Write-Host ''
    Write-Host '╔══════════════════════════════════════════════════════════╗' -ForegroundColor Green
    Write-Host '║        GRC Platform — Dev Environment Ready              ║' -ForegroundColor Green
    Write-Host '╚══════════════════════════════════════════════════════════╝' -ForegroundColor Green
    Write-Host ''
    Write-Host '  Application' -ForegroundColor Cyan
    Write-Host "  Frontend      →  http://localhost:$FrontendPort"
    Write-Host "  GRC API       →  http://localhost:$BackendPort"
    Write-Host "  GraphQL       →  http://localhost:$BackendPort/graphql  (POST)"
    Write-Host "  Health        →  http://localhost:$BackendPort/actuator/health"
    Write-Host ''
    Write-Host '  Infrastructure' -ForegroundColor Cyan
    Write-Host "  Keycloak      →  http://localhost:8080  (admin: $keycloakAdmin)"
    Write-Host '  Neo4j Browser →  http://localhost:7474'
    Write-Host '  SQL Server    →  localhost:1433'
    Write-Host '  Redis         →  localhost:6379'
    Write-Host ''
    Write-Host '  Logs' -ForegroundColor Yellow
    Write-Host "  Backend       →  $LogsDir\backend.log"
    Write-Host "  Frontend      →  $LogsDir\frontend.log"
    Write-Host ''
    Write-Host '  Stop all      →  .\dev-teardown.ps1' -ForegroundColor Yellow
    Write-Host ''
}

# ─── Main ──────────────────────────────────────────────────────────────────────
Write-Host ''
Write-Host '  GRC Platform — Local Dev Setup (Windows / PowerShell)' -ForegroundColor Cyan
Write-Host "  $(Get-Date)"
Write-Host ''

Test-Prerequisites
Set-EnvironmentFile

if (-not $SkipInfra) {
    Start-Infrastructure
    Wait-AllHealthy
    Initialize-Database
} else {
    Write-Warn '-SkipInfra: Docker services step skipped.'
}

if (-not $SkipMigrations) {
    Invoke-Migrations
} else {
    Write-Warn '-SkipMigrations: Liquibase step skipped.'
}

Start-Backend
Start-Frontend
Confirm-Services
Show-Summary
