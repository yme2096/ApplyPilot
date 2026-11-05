import { execFileSync } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';

const origin = process.env.APPLYPILOT_E2E_WEB ?? 'http://localhost:15174';
const syntheticEmail = `perf-${randomUUID()}@example.test`;
const syntheticPassword = randomBytes(32).toString('base64url');
const results = {};

async function call(path, { method = 'GET', token, body, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${origin}/api${path}`, {
    method, headers,
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
    signal: AbortSignal.timeout(60_000)
  });
  let data = null;
  const text = await response.text();
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) throw new Error(`${method} ${path} returned HTTP ${response.status}`);
  return { status: response.status, data };
}

async function measure(label, fn, samples = 20) {
  const timings = [];
  for (let i = 0; i < samples; i++) {
    const start = performance.now();
    await fn();
    timings.push(performance.now() - start);
  }
  timings.sort((a, b) => a - b);
  results[label] = {
    samples,
    p50_ms: Number(timings[Math.floor((timings.length - 1) * 0.50)].toFixed(2)),
    p95_ms: Number(timings[Math.floor((timings.length - 1) * 0.95)].toFixed(2)),
    min_ms: Number(timings[0].toFixed(2)),
    max_ms: Number(timings.at(-1).toFixed(2)),
    throughput_requests_per_second: Number((samples / (timings.reduce((a, b) => a + b, 0) / 1000)).toFixed(2))
  };
}

function makePdf() {
  const content = 'BT /F1 12 Tf 50 750 Td (Synthetic performance fixture. Verified Python and SQL skills.) Tj ET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`
  ];
  let pdf = '%PDF-1.4\n'; const offsets = [0];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  offsets.slice(1).forEach((offset) => { pdf += `${String(offset).padStart(10, '0')} 00000 n \n`; });
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf, 'ascii');
}

const registered = await call('/auth/register', { method: 'POST', body: { email: syntheticEmail, password: syntheticPassword } });
const token = registered.data.accessToken;
const refreshToken = registered.data.refreshToken;
const jwtPayload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
const userId = jwtPayload.sub;

await measure('login', async () => { await call('/auth/login', { method: 'POST', body: { email: syntheticEmail, password: syntheticPassword } }); }, 10);
await call('/profile', { method: 'PUT', token, body: { fullName: 'Synthetic Performance Candidate', phone: null, location: 'Remote', graduationYear: null, linkedinUrl: null, githubUrl: null, portfolioUrl: null, preferredRoles: [], preferredLocations: [], jobType: null, salaryPreference: null } });
await measure('profile_retrieval', () => call('/profile', { token }));

const form = new FormData();
form.append('file', new File([makePdf()], 'synthetic-performance.pdf', { type: 'application/pdf' }));
const resume = (await call('/resumes', { method: 'POST', token, form })).data;
await measure('resume_metadata_retrieval', () => call('/resumes', { token }));

const jobCount = Number(process.env.APPLYPILOT_PERF_JOBS ?? 250);
const createdJobs = [];
const startedJobs = performance.now();
for (let batch = 0; batch < jobCount; batch += 10) {
  const current = Array.from({ length: Math.min(10, jobCount - batch) }, (_, offset) => {
    const n = batch + offset;
    return call('/jobs', { method: 'POST', token, body: {
      title: `Synthetic Performance Role ${n}`,
      company: `Synthetic Company ${n % 25}`,
      location: n % 3 === 0 ? 'Remote' : 'Test City',
      jobUrl: `https://example.test/perf/${userId}/${n}`,
      description: 'Required: Python and SQL. Preferred: Docker. Synthetic workload only.',
      source: 'E2E synthetic performance fixture',
      requiredSkills: ['Python', 'SQL'], preferredSkills: ['Docker']
    } });
  });
  createdJobs.push(...await Promise.all(current));
}
const creationSeconds = (performance.now() - startedJobs) / 1000;
results.synthetic_job_creation = {
  records: createdJobs.length,
  elapsed_seconds: Number(creationSeconds.toFixed(2)),
  throughput_records_per_second: Number((createdJobs.length / creationSeconds).toFixed(2))
};
const firstJob = createdJobs[0].data;
await measure('job_discovery_250_records', () => call('/jobs', { token }), 15);
await measure('job_matching', () => call(`/jobs/${firstJob.id}/match`, { token }), 20);

const bundleItems = createdJobs.slice(0, 25).map(({ data }) => ({
  jobId: data.id, resumeId: resume.id, resumeVersionNumber: 1, applicationAnswers: []
}));
const bulkStart = performance.now();
const bundles = await call('/bundles/bulk', { method: 'POST', token, body: { items: bundleItems } });
results.bulk_preparation_25 = { requests: 1, jobs: bundleItems.length, latency_ms: Number((performance.now() - bulkStart).toFixed(2)), status: bundles.status };
for (const bundle of bundles.data) {
  await call('/applications', { method: 'POST', token, body: { jobId: bundle.jobId, bundleId: bundle.id, status: 'SAVED' } });
}
await measure('application_dashboard_25_records', () => call('/applications/dashboard', { token }), 15);
await measure('job_discovery_after_bulk', () => call('/jobs', { token }), 10);

const sql = `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT status, COUNT(*) FROM application_tracking WHERE user_id='${userId}' GROUP BY status`;
const plan = execFileSync('docker', ['exec', 'applypilot-e2e-postgres-1', 'psql', '-U', 'e2e_user', '-d', 'applypilot_e2e', '-Atc', sql], { encoding: 'utf8' }).trim();
const explain = JSON.parse(plan)[0];
results.dashboard_group_by_query = {
  planning_ms: Number(explain['Planning Time'].toFixed(3)),
  execution_ms: Number(explain['Execution Time'].toFixed(3)),
  plan_node: explain.Plan['Node Type'],
  shared_hit_blocks: explain.Plan['Shared Hit Blocks'] ?? 0,
  shared_read_blocks: explain.Plan['Shared Read Blocks'] ?? 0
};
await call('/auth/logout', { method: 'POST', body: { refreshToken } });
process.stdout.write(`${JSON.stringify({ environment: 'isolated synthetic Compose database', jobs: jobCount, results }, null, 2)}\n`);
