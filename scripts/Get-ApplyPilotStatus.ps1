$ErrorActionPreference = 'Continue'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repoRoot
try {
    $dockerReady = $false
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        docker info --format '{{.ServerVersion}}' *> $null
        $dockerReady = ($LASTEXITCODE -eq 0)
    }
    if ($dockerReady) {
        Write-Host 'Docker Engine: READY' -ForegroundColor Green
        $composeRows = @(& docker compose ps --format json)
        $composeExitCode = $LASTEXITCODE
        if ($composeExitCode -eq 0) {
            Write-Host 'Compose services:'
            Write-Host ('{0,-22} {1,-14} {2}' -f 'SERVICE','STATE','HEALTH')
            foreach ($line in $composeRows) {
                if ([string]::IsNullOrWhiteSpace([string]$line)) { continue }
                try { $row = ConvertFrom-Json -InputObject $line -ErrorAction Stop }
                catch { Write-Host "Could not parse Compose service row: $line" -ForegroundColor Yellow; continue }
                $serviceName = [string]$row.Service
                $serviceState = [string]$row.State
                $healthProperty = $row.PSObject.Properties['Health']
                $serviceHealth = if ($null -eq $healthProperty -or [string]::IsNullOrWhiteSpace([string]$healthProperty.Value)) { 'not configured' } else { [string]$healthProperty.Value }
                Write-Host ('{0,-22} {1,-14} {2}' -f $serviceName,$serviceState,$serviceHealth)
            }
        } else { Write-Host 'Compose service list: UNAVAILABLE' -ForegroundColor Yellow }
        foreach ($check in @(
            @{ Name='PostgreSQL'; Service='postgres'; Args=@('sh','-c','pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"') },
            @{ Name='Redis'; Service='redis'; Args=@('redis-cli','ping') },
            @{ Name='Document worker'; Service='document-worker'; Args=@('python','-c',"import urllib.request; urllib.request.urlopen('http://localhost:8000/health',timeout=2)") }
        )) {
            & docker compose exec -T $check.Service @($check.Args) *> $null
            $state = if ($LASTEXITCODE -eq 0) { 'READY' } else { 'NOT READY' }
            $color = if ($LASTEXITCODE -eq 0) { 'Green' } else { 'Yellow' }
            Write-Host ("{0}: {1}" -f $check.Name,$state) -ForegroundColor $color
        }
    } else {
        Write-Host 'Docker Engine: UNAVAILABLE (open Docker Desktop and wait for Engine)' -ForegroundColor Yellow
    }
    foreach ($probe in @(@{Name='API';Url='http://localhost:8081/api/health'},@{Name='Web';Url='http://localhost:5174/'})) {
        try { $response=Invoke-WebRequest -Uri $probe.Url -TimeoutSec 4 -UseBasicParsing; Write-Host ("{0}: HTTP {1} ({2})" -f $probe.Name,[int]$response.StatusCode,$probe.Url) -ForegroundColor Green }
        catch { Write-Host ("{0}: UNAVAILABLE ({1})" -f $probe.Name,$probe.Url) -ForegroundColor Yellow }
    }
    if ($dockerReady) {
        foreach ($volume in 'applypilot_postgres_data','applypilot_redis_data','applypilot_resume_data') {
            docker volume inspect $volume *> $null
            Write-Host ("Volume {0}: {1}" -f $volume,($(if($LASTEXITCODE -eq 0){'PRESENT'}else{'MISSING'})))
        }
        $query='psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT max(version::integer) FROM flyway_schema_history WHERE success"'
        $versionOutput=@(& docker compose exec -T postgres sh -c $query 2>$null)
        $versionExitCode=$LASTEXITCODE
        $version= (($versionOutput -join "`n")).Trim()
        if($versionExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($version)){Write-Host "Flyway current version: V$version"}else{Write-Host 'Database/Flyway version: UNAVAILABLE (query returned no version)'}
        $counts='psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atqc "SELECT ''users=''||(SELECT count(*) FROM users)||'',jobs=''||(SELECT count(*) FROM saved_jobs)||'',resumes=''||(SELECT count(*) FROM resumes)||'',applications=''||(SELECT count(*) FROM application_tracking)"'
        $dataOutput=@(& docker compose exec -T postgres sh -c $counts 2>$null)
        $countsExitCode=$LASTEXITCODE
        $data=(($dataOutput -join "`n")).Trim()
        if($countsExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($data)){Write-Host "Database aggregate counts (read-only): $data"}else{Write-Host 'Database aggregate counts: UNAVAILABLE (query returned no value)'}
    }
    Write-Host ''
    Write-Host 'URLs: web http://localhost:5174 | API http://localhost:8081 | PostgreSQL localhost:5433 | Redis localhost:6379'
} finally { Pop-Location }
