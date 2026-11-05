[CmdletBinding()]
param([int]$TimeoutSeconds = 180, [switch]$CheckOnly)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repoRoot
try {
    if (-not (Test-Path '.env')) { throw 'The .env file is missing. Copy .env.example to .env and set unique local secrets; do not share the file.' }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI is missing. Install Docker Desktop, then rerun this script.' }
    docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        if (Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue) { throw 'Docker Desktop is open, but Docker Engine is not ready. Wait for the engine, then rerun.' }
        throw 'Docker Desktop is closed. Open Docker Desktop, wait for the engine to become ready, then rerun.'
    }

    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration validation failed. No services were started.' }
    foreach ($volume in 'applypilot_postgres_data','applypilot_redis_data','applypilot_resume_data') {
        docker volume inspect $volume *> $null
        if ($LASTEXITCODE -ne 0) { throw "Expected existing volume '$volume' is missing. Refusing to start Compose, which could create an empty replacement." }
    }

    # Require and revalidate the paired database + document backup before starting any service.
    $backupDir = Join-Path $env:LOCALAPPDATA 'ApplyPilot\backups'
    $manifestPath = Join-Path $backupDir 'applypilot-pair-20261104-213400.manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) { throw "Verified paired backup manifest missing: $manifestPath" }
    try { $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -ErrorAction Stop } catch { throw "Paired backup manifest is invalid JSON: $manifestPath" }
    foreach ($field in @('databaseDump','resumeArchive','postgresVersion','flywayVersion','recordCounts')) { if ($null -eq $manifest.$field) { throw "Paired backup manifest is missing '$field'." } }
    $dumpPath = Join-Path $backupDir ([string]$manifest.databaseDump.file)
    $resumeArchivePath = Join-Path $backupDir ([string]$manifest.resumeArchive.file)
    foreach ($path in @($dumpPath,$resumeArchivePath)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Paired backup file missing: $path" }; if ((Get-Item -LiteralPath $path).Length -le 0) { throw "Paired backup file is empty: $path" } }
    $dumpHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dumpPath).Hash.ToLowerInvariant()
    $resumeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resumeArchivePath).Hash.ToLowerInvariant()
    if ($dumpHash -ne $manifest.databaseDump.sha256.ToLowerInvariant()) { throw 'Database dump checksum does not match paired manifest.' }
    if ($resumeHash -ne $manifest.resumeArchive.sha256.ToLowerInvariant()) { throw 'Resume archive checksum does not match paired manifest.' }
    if ($manifest.postgresVersion -ne '16.15' -or $manifest.flywayVersion -ne '11') { throw 'Paired backup metadata is not the expected PostgreSQL 16.15 / Flyway V11 state.' }
    docker image inspect postgres:16-alpine *> $null
    if ($LASTEXITCODE -ne 0) { throw 'The local PostgreSQL image needed to verify the backup is unavailable; no service was started.' }
    $checkName = 'applypilot-prestart-' + [guid]::NewGuid().ToString('N').Substring(0,12)
    docker run -d --pull never --name $checkName postgres:16-alpine sleep 300 *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start temporary backup verifier; no Compose service was started.' }
    try {
        docker cp $dumpPath "${checkName}:/tmp/database.dump" *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Could not stage existing backup for archive validation; no Compose service was started.' }
        docker exec $checkName pg_restore --list /tmp/database.dump *> $null
        if ($LASTEXITCODE -ne 0) { throw 'The newest database backup failed pg_restore validation; no Compose service was started.' }
        docker cp $resumeArchivePath "${checkName}:/tmp/resumes.tar.gz" *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Could not stage resume archive for validation; no Compose service was started.' }
        docker exec $checkName tar -tzf /tmp/resumes.tar.gz *> $null
        if ($LASTEXITCODE -ne 0) { throw 'The paired resume archive failed tar listing; no Compose service was started.' }
    } finally { docker rm -f $checkName *> $null }
    Write-Host "PASS: verified paired backup manifest, database dump, and resume archive ($($manifest.databaseDump.file), $($manifest.resumeArchive.file))." -ForegroundColor Green

    # Normal startup starts dependencies. CheckOnly verifies the existing containers
    # without changing service state, so it is safe to run during local diagnostics.
    if (-not $CheckOnly) {
        docker compose up -d postgres redis document-worker
        if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL, Redis, and document worker.' }
    } else {
        $runningServices = @(& docker compose ps --status running --services)
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect existing Compose services.' }
        foreach ($service in @('postgres','redis','document-worker','api','web')) {
            if ($runningServices -notcontains $service) { throw "CheckOnly requires existing service '$service' to be running; no service was started." }
        }
        Write-Host 'PASS: CheckOnly will verify existing services without starting, rebuilding, or restarting them.' -ForegroundColor Green
    }
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        docker compose exec -T postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' *> $null; $pg = ($LASTEXITCODE -eq 0)
        docker compose exec -T redis redis-cli ping 2>$null | Out-Null; $redis = ($LASTEXITCODE -eq 0)
        docker compose exec -T document-worker python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health',timeout=2)" *> $null; $worker = ($LASTEXITCODE -eq 0)
        if ($pg -and $redis -and $worker) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "PostgreSQL, Redis, or document worker did not become ready within $TimeoutSeconds seconds. No API/web build was started." }
    Write-Host 'PASS: PostgreSQL, Redis and document worker are ready.' -ForegroundColor Green

    $migrationQuery = 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT max(version::integer) FROM flyway_schema_history WHERE success"'
    $flywayOutput = @(& docker compose exec -T postgres sh -c $migrationQuery 2>$null)
    $flywayExitCode = $LASTEXITCODE
    $flyway = (($flywayOutput -join "`n")).Trim()
    if ($flywayExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($flyway)) { throw "Primary database Flyway version query returned no value (exit $flywayExitCode). API startup stopped before migration." }
    if ($flyway -ne '11') { throw "Primary database Flyway version is '$flyway', expected V11. API startup stopped before migration. Back up and review the schema before proceeding." }
    $countsQuery = 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT ''users=''||(SELECT count(*) FROM users)||'',jobs=''||(SELECT count(*) FROM saved_jobs)||'',resumes=''||(SELECT count(*) FROM resumes)||'',applications=''||(SELECT count(*) FROM application_tracking)"'
    $countsOutput = @(& docker compose exec -T postgres sh -c $countsQuery 2>$null)
    $countsExitCode = $LASTEXITCODE
    $counts = (($countsOutput -join "`n")).Trim()
    if ($countsExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($counts)) { throw 'Could not query read-only record counts; API startup stopped.' }
    $expected = $manifest.recordCounts
    $expectedCounts = "users=$($expected.users),jobs=$($expected.jobs),resumes=$($expected.resumes),applications=$($expected.applications)"
    if ($counts -ne $expectedCounts) { throw "Primary DB counts '$counts' do not match paired manifest '$expectedCounts'; API startup stopped." }
    Write-Host "PASS: database Flyway V$flyway; current aggregate counts: $counts" -ForegroundColor Green

    if ($CheckOnly) {
        foreach ($probe in @(@{Name='API';Url='http://localhost:8081/api/health'},@{Name='Web';Url='http://localhost:5174/'})) {
            try { $response=Invoke-WebRequest -Uri $probe.Url -TimeoutSec 4 -UseBasicParsing; if ([int]$response.StatusCode -ne 200) { throw "HTTP $([int]$response.StatusCode)" }; Write-Host ("PASS: {0} HTTP {1}" -f $probe.Name,[int]$response.StatusCode) -ForegroundColor Green }
            catch { throw "CheckOnly failed: $($probe.Name) readiness check failed ($($_.Exception.Message))." }
        }
        Write-Host 'CheckOnly complete. No Compose service was started, rebuilt, or restarted.' -ForegroundColor Green
        return
    }

    docker compose up -d --build api web
    if ($LASTEXITCODE -ne 0) { throw 'API/web build or startup failed. Data volumes were not removed.' }
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $apiReady = $false; $webReady = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        try { $r = Invoke-WebRequest -Uri 'http://localhost:8081/api/health' -TimeoutSec 4 -UseBasicParsing; $apiReady = ([int]$r.StatusCode -eq 200) } catch { $apiReady = $false }
        try { $r = Invoke-WebRequest -Uri 'http://localhost:5174/' -TimeoutSec 4 -UseBasicParsing; $webReady = ([int]$r.StatusCode -eq 200) } catch { $webReady = $false }
        if ($apiReady -and $webReady) { break }
        Start-Sleep -Seconds 2
    }
    if (-not ($apiReady -and $webReady)) { throw 'API/web did not become healthy within the timeout. Run Get-ApplyPilotStatus.ps1 and inspect redacted service logs.' }
    Write-Host ''
    Write-Host 'ApplyPilot is ready.' -ForegroundColor Green
    Write-Host 'Web:       http://localhost:5174'
    Write-Host 'API health http://localhost:8081/api/health'
    Write-Host 'PostgreSQL localhost:5433 | Redis localhost:6379 | worker internal port 8000'
    Write-Host "Flyway before API startup: V$flyway | Counts before startup: $counts"
} finally {
    Pop-Location
}
