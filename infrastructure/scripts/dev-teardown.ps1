#Requires -Version 5.1
<#
.SYNOPSIS
    GRC Platform — Teardown script (Windows PowerShell)

.DESCRIPTION
    Stops backend and frontend processes started by dev-setup.ps1.
    Optionally stops Docker services.

.PARAMETER All
    Also stop Docker Compose services (volumes preserved).

.PARAMETER Wipe
    Requires -All. Destroys Docker volumes too (DATA LOSS — prompts for confirmation).

.EXAMPLE
    .\dev-teardown.ps1
    .\dev-teardown.ps1 -All
    .\dev-teardown.ps1 -All -Wipe
#>
[CmdletBinding()]
param(
    [switch]$All,
    [switch]$Wipe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$InfraDir    = Split-Path -Parent $ScriptDir
$LogsDir     = Join-Path $InfraDir 'logs'
$PidDir      = Join-Path $InfraDir '.pids'
$ComposeFile = Join-Path $InfraDir 'docker-compose.yml'

function Write-Ok   { param($msg) Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Inf  { param($msg) Write-Host "[INFO] $msg" }

function Stop-ManagedProcess {
    param([string]$Name)
    $pidFile = Join-Path $PidDir "$Name.pid"

    if (-not (Test-Path $pidFile)) {
        Write-Inf "${Name}: no PID file — already stopped or not started by this script."
        return
    }

    $pid = [int](Get-Content $pidFile -Raw).Trim()
    $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue

    if ($proc) {
        Write-Inf "Stopping $Name (PID $pid)..."
        $proc | Stop-Process -Force
        $proc.WaitForExit(15000) | Out-Null
        Write-Ok "$Name stopped."
    } else {
        Write-Inf "$Name (PID $pid) is not running — nothing to stop."
    }

    Remove-Item $pidFile -Force
}

function Move-Logs {
    if (Test-Path $LogsDir) {
        $stamp   = Get-Date -Format 'yyyyMMdd_HHmmss'
        $archive = Join-Path $LogsDir "archive_$stamp"
        New-Item -ItemType Directory -Force -Path $archive | Out-Null
        foreach ($f in @('backend.log', 'frontend.log')) {
            $src = Join-Path $LogsDir $f
            if (Test-Path $src) { Move-Item $src $archive }
        }
        Write-Inf "Logs archived to $archive"
    }
}

Write-Host ''
Write-Host '  GRC Platform — Teardown (Windows / PowerShell)' -ForegroundColor Yellow
Write-Host "  $(Get-Date)"
Write-Host ''

Stop-ManagedProcess 'backend'
Stop-ManagedProcess 'frontend'
Move-Logs

if ($All) {
    if ($Wipe) {
        Write-Warn 'WARNING: -Wipe will destroy ALL Docker volumes (SQL Server, Neo4j, Redis, Keycloak data).'
        Write-Warn "Type 'yes' to confirm:"
        $confirm = Read-Host
        if ($confirm -eq 'yes') {
            Write-Inf 'Stopping Docker services and destroying volumes...'
            docker compose -f $ComposeFile --project-directory $InfraDir down -v
            Write-Ok 'Docker services and volumes destroyed.'
        } else {
            Write-Warn 'Volume wipe aborted. Stopping services without destroying volumes...'
            docker compose -f $ComposeFile --project-directory $InfraDir down
            Write-Ok 'Docker services stopped (volumes preserved).'
        }
    } else {
        Write-Inf 'Stopping Docker services (volumes preserved)...'
        docker compose -f $ComposeFile --project-directory $InfraDir down
        Write-Ok 'Docker services stopped.'
    }
} else {
    Write-Inf 'Docker services left running. Use -All to stop them.'
}

Write-Host ''
Write-Host 'Teardown complete.' -ForegroundColor Green
Write-Host ''
