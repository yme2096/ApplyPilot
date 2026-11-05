import { execFileSync } from 'node:child_process';
import { request } from '@playwright/test';

function docker(args: string[]) {
  return execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

export default async function globalSetup() {
  const forbiddenVolumes = new Set([
    'applypilot_postgres_data',
    'applypilot_redis_data',
    'applypilot_resume_data'
  ]);
  const apiDetails = JSON.parse(docker(['inspect', 'applypilot-e2e-api-1']))[0];
  const dbUrl = apiDetails.Config.Env.find((entry: string) => entry.startsWith('DATABASE_URL=')) as string | undefined;
  if (dbUrl !== 'DATABASE_URL=jdbc:postgresql://postgres:5432/applypilot_e2e') {
    throw new Error('E2E safety check refused: API is not configured for the isolated applypilot_e2e database.');
  }
  const apiMounts = new Set((apiDetails.Mounts as Array<{ Name?: string }>).map((mount) => mount.Name).filter(Boolean) as string[]);
  const pgDetails = JSON.parse(docker(['inspect', 'applypilot-e2e-postgres-1']))[0];
  const pgMounts = new Set((pgDetails.Mounts as Array<{ Name?: string }>).map((mount) => mount.Name).filter(Boolean) as string[]);
  const redisDetails = JSON.parse(docker(['inspect', 'applypilot-e2e-redis-1']))[0];
  const redisMounts = new Set((redisDetails.Mounts as Array<{ Name?: string }>).map((mount) => mount.Name).filter(Boolean) as string[]);
  const mounted = new Set([...apiMounts, ...pgMounts, ...redisMounts]);
  for (const volume of mounted) {
    if (forbiddenVolumes.has(volume)) throw new Error('E2E safety check refused: primary ApplyPilot data volume is mounted.');
  }
  if (!apiMounts.has('applypilot-e2e-resume-data') || !pgMounts.has('applypilot-e2e-postgres-data') || !redisMounts.has('applypilot-e2e-redis-data'))
    throw new Error('E2E safety check refused: isolated volumes are not attached to the expected services.');
  const database = docker(['exec', 'applypilot-e2e-postgres-1', 'psql', '-U', 'e2e_user', '-d', 'applypilot_e2e', '-Atc', 'SELECT current_database() || \'|\' || current_user']);
  if (database !== 'applypilot_e2e|e2e_user') throw new Error('E2E safety check refused: test database identity did not match.');

  const baseURL = process.env.APPLYPILOT_E2E_WEB ?? 'http://localhost:15174';
  const api = await request.newContext({ baseURL });
  const response = await api.get('/api/health', { timeout: 10_000 });
  if (response.status() !== 200) throw new Error(`Isolated E2E API is not ready (HTTP ${response.status()}).`);
  await api.dispose();
}
