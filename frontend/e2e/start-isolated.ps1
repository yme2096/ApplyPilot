$ErrorActionPreference = 'Stop'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Push-Location $repo
try {
  $primaryVolumes = @('applypilot_postgres_data', 'applypilot_redis_data', 'applypilot_resume_data')
  $isolatedVolumes = @('applypilot-e2e-postgres-data', 'applypilot-e2e-redis-data', 'applypilot-e2e-resume-data')
  if ($isolatedVolumes | Where-Object { $_ -in $primaryVolumes }) { throw 'Refusing to start E2E environment: a primary volume name was selected.' }
  docker compose -p applypilot-e2e -f docker-compose.e2e.yml config --quiet
  if ($LASTEXITCODE -ne 0) { throw 'Isolated Compose configuration is invalid.' }
  docker compose -p applypilot-e2e -f docker-compose.e2e.yml up -d --build --wait
  if ($LASTEXITCODE -ne 0) { throw 'Isolated E2E services did not become ready.' }
  $api = docker inspect applypilot-e2e-api-1 | ConvertFrom-Json
  $database = ($api[0].Config.Env | Where-Object { $_.StartsWith('DATABASE_URL=') })
  if ($database -ne 'DATABASE_URL=jdbc:postgresql://postgres:5432/applypilot_e2e') { throw 'Refusing to test: API database target is not the isolated database.' }
  $mountsByService = @{
    api = @(docker inspect applypilot-e2e-api-1 | ConvertFrom-Json | ForEach-Object { $_.Mounts | ForEach-Object { $_.Name } })
    postgres = @(docker inspect applypilot-e2e-postgres-1 | ConvertFrom-Json | ForEach-Object { $_.Mounts | ForEach-Object { $_.Name } })
    redis = @(docker inspect applypilot-e2e-redis-1 | ConvertFrom-Json | ForEach-Object { $_.Mounts | ForEach-Object { $_.Name } })
  }
  $allMounted = @($mountsByService.Values | ForEach-Object { $_ })
  foreach ($name in $primaryVolumes) { if ($name -in $allMounted) { throw 'Refusing to test: a primary volume is mounted.' } }
  if ('applypilot-e2e-resume-data' -notin $mountsByService.api -or 'applypilot-e2e-postgres-data' -notin $mountsByService.postgres -or 'applypilot-e2e-redis-data' -notin $mountsByService.redis) {
    throw 'Refusing to test: isolated volumes are not attached to the expected services.'
  }
  docker exec applypilot-e2e-postgres-1 psql -U e2e_user -d applypilot_e2e -Atc "SELECT current_database() || '|' || current_user"
  if ($LASTEXITCODE -ne 0) { throw 'Could not verify isolated database identity.' }
  Write-Output 'Isolated stack is ready. Web: http://localhost:15174; API: http://127.0.0.1:18081; database: applypilot_e2e.'
  Write-Output 'All E2E data is synthetic and stored in applypilot-e2e-* volumes.'
  Write-Output 'Do not add -v to Compose cleanup commands until test-volume names have been rechecked.'
} finally {
  Pop-Location
}
