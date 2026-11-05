[CmdletBinding()]
param(
    [string]$BackupDirectory = (Join-Path $env:LOCALAPPDATA 'ApplyPilot\backups')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repoRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI is not installed.' }
    docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Engine is unavailable. Open Docker Desktop and wait for it to become ready.' }

    foreach ($volume in 'applypilot_postgres_data', 'applypilot_resume_data') {
        docker volume inspect $volume *> $null
        if ($LASTEXITCODE -ne 0) { throw "Required existing data volume is missing: $volume. Backup stopped to avoid using an empty replacement." }
    }
    docker compose exec -T postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL is not ready; no backup was written.' }

    $dbUser = (& docker compose exec -T postgres printenv POSTGRES_USER).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $dbUser) { throw 'Could not read the configured database role from the running container.' }
    $dbName = (& docker compose exec -T postgres printenv POSTGRES_DB).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $dbName) { throw 'Could not read the configured database name from the running container.' }
    if ($dbUser -notmatch '^[A-Za-z0-9_.-]+$' -or $dbName -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Database role/name uses characters this Windows-safe backup command does not accept; no backup was started.' }

    [System.IO.Directory]::CreateDirectory($BackupDirectory) | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $dbPath = Join-Path $BackupDirectory "applypilot-$stamp.dump"
    $resumePath = Join-Path $BackupDirectory "applypilot-resumes-$stamp.tar.gz"
    $manifestPath = Join-Path $BackupDirectory "applypilot-$stamp.manifest.json"
    foreach ($path in $dbPath, $resumePath, $manifestPath) {
        if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite an existing backup: $path" }
    }

    # pg_dump is binary. Copy the redirected process stream directly to a new file.
    $dockerPath = (Get-Command docker).Source
    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $dockerPath
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.Arguments = "compose exec -T postgres pg_dump -Fc -U $dbUser -d $dbName"
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Could not start pg_dump.' }
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $stream = [System.IO.File]::Open($dbPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try { $process.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
    $process.WaitForExit()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) { throw "pg_dump failed with exit code $($process.ExitCode). $($stderr.Trim())" }

    $workerImage = (& docker compose images -q document-worker | Select-Object -First 1).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $workerImage) { throw 'The existing document-worker image is unavailable; PostgreSQL archive was kept, but a complete backup was not produced.' }
    $tempName = 'applypilot-backup-' + [guid]::NewGuid().ToString('N').Substring(0,12)
    # The worker image has no ENTRYPOINT and its CMD is uvicorn; override the CMD
    # explicitly so the archiver stays alive for docker exec/cp instead of launching uvicorn.
    docker run -d --pull never --name $tempName --entrypoint sleep --mount 'type=volume,source=applypilot_resume_data,target=/data,readonly' $workerImage 900 *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the temporary read-only resume-volume archiver.' }
    try {
        $archiveCode = "import tarfile; t=tarfile.open('/tmp/resumes.tar.gz','w:gz'); t.add('/data',arcname='.'); t.close()"
        docker exec $tempName python -c $archiveCode *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Could not archive the resume volume; the database dump was retained.' }
        docker exec $tempName python -c "import tarfile; t=tarfile.open('/tmp/resumes.tar.gz','r:gz'); list(t); t.close()" *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Resume archive validation failed; the database dump was retained.' }
        $stagedResumePath = Join-Path $env:TEMP ('applypilot-resumes-' + [guid]::NewGuid().ToString('N') + '.tar.gz')
        docker cp "${tempName}:/tmp/resumes.tar.gz" $stagedResumePath *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Could not stage the resume archive outside the volume.' }
        [System.IO.File]::Copy($stagedResumePath, $resumePath, $false)
        $stagedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $stagedResumePath).Hash
        $savedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resumePath).Hash
        if ($stagedHash -ne $savedHash) { throw 'Resume archive copy hash mismatch; both files were retained.' }
        [System.IO.File]::Delete($stagedResumePath)
    } finally {
        docker rm -f $tempName *> $null
    }

    # Validate the PostgreSQL archive using the already installed local Compose image, without mounting a data volume.
    $pgImage = 'postgres:16-alpine'
    docker image inspect $pgImage *> $null
    if ($LASTEXITCODE -ne 0) { throw 'The existing postgres:16-alpine image is unavailable; backup files were retained but archive validation is incomplete.' }
    $checkName = 'applypilot-pg-verify-' + [guid]::NewGuid().ToString('N').Substring(0,12)
    docker run -d --pull never --name $checkName $pgImage sleep 900 *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the temporary PostgreSQL archive verifier.' }
    try {
        docker cp $dbPath "${checkName}:/tmp/database.dump" *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Could not copy database archive into temporary verifier.' }
        docker exec $checkName pg_restore --list /tmp/database.dump *> $null
        if ($LASTEXITCODE -ne 0) { throw 'pg_restore rejected the database archive. Files were retained for investigation.' }
    } finally {
        docker rm -f $checkName *> $null
    }

    $dbHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dbPath).Hash.ToLowerInvariant()
    $resumeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resumePath).Hash.ToLowerInvariant()
    $manifest = [ordered]@{
        createdAtUtc = [DateTime]::UtcNow.ToString('o')
        databaseArchive = [ordered]@{ file = [IO.Path]::GetFileName($dbPath); bytes = (Get-Item $dbPath).Length; sha256 = $dbHash; pgRestoreList = 'PASS' }
        resumeVolumeArchive = [ordered]@{ file = [IO.Path]::GetFileName($resumePath); bytes = (Get-Item $resumePath).Length; sha256 = $resumeHash; tarRead = 'PASS' }
        database = $dbName
        volume = 'applypilot_resume_data'
    }
    [System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
    Write-Host 'PASS: PostgreSQL archive and resume-volume archive were created and validated.' -ForegroundColor Green
    Write-Host "Database archive: $dbPath"
    Write-Host "Resume archive:   $resumePath"
    Write-Host "Manifest:         $manifestPath"
    Write-Host "Database SHA-256: $dbHash"
    Write-Host "Resume SHA-256:   $resumeHash"
    Write-Host 'Backups contain candidate data. Keep this folder private and do not upload it.'
} finally {
    Pop-Location
}
