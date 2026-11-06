import { execFileSync } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

const origin = process.env.APPLYPILOT_E2E_API ?? 'http://localhost:18081';
const composeArgs = ['compose', '-p', 'applypilot-e2e', '-f', '../docker-compose.e2e.yml'];
const compose = (...args) => execFileSync('docker', [...composeArgs, ...args], { stdio: 'pipe' }).toString();
const email = `recovery-${randomUUID()}@example.test`;
const password = randomBytes(32).toString('base64url');
const results = [];

async function request(path, options = {}) {
  return fetch(`${origin}/api${path}`, { ...options, signal: AbortSignal.timeout(12_000) });
}

function record(name, passed, detail) {
  results.push({ name, result: passed ? 'PASS' : 'FAIL', detail });
  if (!passed) throw new Error(`${name} failed: ${detail}`);
}

async function waitForHealth(expected = 200, timeoutMs = 90_000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    try {
      const response = await fetch(`${origin}/api/health`, { signal: AbortSignal.timeout(6_000) });
      if (response.status === expected) return response;
    } catch { /* service is restarting or intentionally unavailable */ }
    await delay(500);
  }
  throw new Error(`API health did not reach HTTP ${expected} in time`);
}

let failure = null;
try {
  await compose('up', '-d', '--wait', 'postgres', 'redis', 'document-worker', 'api');
  await waitForHealth();
  const registration = await request('/auth/register', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  record('synthetic account registration', registration.status === 201, `HTTP ${registration.status}`);
  const auth = await registration.json();
  const authorization = { Authorization: `Bearer ${auth.accessToken}` };

  await compose('stop', 'redis');
  try {
    const start = performance.now();
    const response = await request('/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    record('Redis outage fails closed for authentication', response.status === 503 && performance.now() - start < 6_000,
      `HTTP ${response.status}, ${Math.round(performance.now() - start)} ms`);
  } finally { await compose('up', '-d', '--wait', 'redis'); }

  await compose('stop', 'postgres');
  try {
    const start = performance.now();
    const response = await fetch(`${origin}/api/health`, { signal: AbortSignal.timeout(7_000) });
    record('PostgreSQL outage returns bounded unhealthy readiness', response.status === 503 && performance.now() - start < 6_000,
      `HTTP ${response.status}, ${Math.round(performance.now() - start)} ms`);
  } finally { await compose('up', '-d', '--wait', 'postgres'); }
  await waitForHealth();

  await compose('stop', 'document-worker');
  try {
    const form = new FormData();
    form.append('file', new Blob(['%PDF-1.4\nSynthetic recovery resume fixture\n%%EOF'], { type: 'application/pdf' }), 'recovery-synthetic.pdf');
    const uploaded = await request('/resumes', { method: 'POST', headers: authorization, body: form });
    record('worker outage preserves uploaded resume metadata', uploaded.status === 200 || uploaded.status === 201, `HTTP ${uploaded.status}`);
    const metadata = await uploaded.json();
    const versionsResponse = await request('/resumes', { headers: authorization });
    const resumes = await versionsResponse.json();
    record('resume survives worker outage', versionsResponse.status === 200 && resumes.some((resume) => resume.id === metadata.id),
      `GET /resumes HTTP ${versionsResponse.status}`);
    const unavailable = await fetch(`${origin}/api/health`, { signal: AbortSignal.timeout(6_000) });
    record('worker outage is reported by readiness', unavailable.status === 503, `HTTP ${unavailable.status}`);
  } finally { await compose('up', '-d', '--wait', 'document-worker'); }

  await compose('restart', 'api');
  await waitForHealth();
  const persisted = await request('/resumes', { headers: authorization });
  record('resume metadata remains after API restart', persisted.status === 200 && (await persisted.json()).some((resume) => resume.filename === 'recovery-synthetic.pdf'),
    `GET /resumes HTTP ${persisted.status}`);
} catch (error) {
  failure = error instanceof Error ? error.message : String(error);
} finally {
  await compose('up', '-d', '--wait', 'postgres', 'redis', 'document-worker', 'api');
}

process.stdout.write(`${JSON.stringify({ environment: 'isolated ApplyPilot E2E Compose; synthetic data only', results, failure }, null, 2)}\n`);
if (failure) process.exitCode = 1;
