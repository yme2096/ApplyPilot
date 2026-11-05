$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repoRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI is unavailable. No command was run.' }
    docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is unavailable. No command was run.' }
    # `stop` keeps containers and all named volumes. Never use `down -v` for routine shutdown.
    docker compose stop
    if ($LASTEXITCODE -ne 0) { throw 'One or more Compose services could not be stopped.' }
    Write-Host 'ApplyPilot services stopped. PostgreSQL, Redis and resume volumes were retained.' -ForegroundColor Green
} finally { Pop-Location }
