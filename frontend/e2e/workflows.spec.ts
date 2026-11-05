import { expect, test, type Page } from '@playwright/test';
import { randomBytes, randomUUID } from 'node:crypto';

const password = randomBytes(32).toString('base64url');
const emailFor = (label: string) => `e2e-${label}-${randomUUID()}@example.test`;

async function register(page: Page, email = emailFor('candidate')) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Create an account' }).click();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  const response = page.waitForResponse((r) => r.url().includes('/api/auth/register'));
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  expect((await response).status()).toBe(201);
  await expect(page.getByRole('heading', { name: 'Your next opportunity starts here.' })).toBeVisible();
  return email;
}

async function login(page: Page, email: string) {
  await page.goto('/');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  const response = page.waitForResponse((r) => r.url().includes('/api/auth/login'));
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Your next opportunity starts here.' })).toBeVisible();
}

async function api(page: Page, method: string, path: string, body?: unknown, file?: { name: string; mimeType: string; data: number[] }) {
  return page.evaluate(async ({ method, path, body, file }) => {
    const headers: Record<string, string> = {};
    const token = sessionStorage.getItem('accessToken');
    if (token) headers.Authorization = `Bearer ${token}`;
    let payload: BodyInit | undefined;
    if (file) {
      const form = new FormData();
      form.append('file', new File([new Uint8Array(file.data)], file.name, { type: file.mimeType }));
      payload = form;
    } else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    const response = await fetch(`/api${path}`, { method, headers, body: payload });
    const text = await response.text();
    let data: unknown = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: response.status, data };
  }, { method, path, body, file });
}

function syntheticPdf() {
  const content = [
    'BT', '/F1 12 Tf', '50 750 Td', '(Candidate: Synthetic Example) Tj',
    '0 -20 Td', '(Verified skills: Python and SQL) Tj',
    '0 -20 Td', '(Sample project: built a small data dashboard.) Tj', 'ET'
  ].join('\n');
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`
  ];
  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  for (let i = 0; i < objects.length; i++) {
    offsets.push(Buffer.byteLength(pdf));
    pdf += `${i + 1} 0 obj\n${objects[i]}\nendobj\n`;
  }
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets.slice(1)) pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf, 'ascii');
}

async function createManualJob(page: Page, title: string, skills: string[]) {
  await page.getByRole('button', { name: 'Job discovery' }).click();
  await page.getByRole('button', { name: 'Manual Entry' }).click();
  await page.getByLabel('Job Title *').fill(title);
  await page.getByLabel('Company / Organization').fill('Synthetic Test Company');
  await page.getByLabel('Location / Remote').fill('Remote');
  await page.getByLabel('Job Posting URL').fill(`https://example.test/jobs/${encodeURIComponent(title)}`);
  await page.getByLabel('Job Description & Qualifications').fill(`Requirements: ${skills.join(', ')}. Applicants must currently be enrolled in a degree program.`);
  const response = page.waitForResponse((r) => r.url().includes('/api/jobs') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Save Opportunity' }).click();
  expect((await response).status()).toBe(201);
}

test('same-origin readiness and browser CSP allow only the declared app resources', async ({ page, request }) => {
  const health = await request.get('/api/health');
  expect(health.status()).toBe(200);
  expect((await health.json()).status).toBe('UP');
  const violations: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('violates the following Content Security Policy')) violations.push(message.text());
  });
  const response = await page.goto('/');
  expect(response?.status()).toBe(200);
  const csp = response?.headers()['content-security-policy'] ?? '';
  expect(csp).toContain("script-src 'self'");
  expect(csp).toContain('https://fonts.googleapis.com');
  expect(csp).toContain('https://fonts.gstatic.com');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.waitForTimeout(500);
  expect(violations).toEqual([]);
});

test('authenticated Applications workspace fits mobile, tablet, and desktop viewports', async ({ page }) => {
  await register(page);
  await page.getByRole('button', { name: 'Applications' }).click();
  for (const viewport of [{ width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 1280, height: 900 }]) {
    await page.setViewportSize(viewport);
    await expect(page.getByRole('main')).toBeVisible();
    const width = await page.evaluate(() => ({ viewport: document.documentElement.clientWidth, content: document.documentElement.scrollWidth }));
    expect(width.content).toBeLessThanOrEqual(width.viewport + 2);
  }
});

test('registration, profile editing, session-scoped token, expiry, refresh, and logout', async ({ page, request }) => {
  const email = await register(page);
  const storage = await page.evaluate(() => ({
    access: sessionStorage.getItem('accessToken'), refresh: sessionStorage.getItem('refreshToken'),
    localAccess: localStorage.getItem('accessToken')
  }));
  expect(storage.access).toBeTruthy();
  expect(storage.refresh).toBeTruthy();
  expect(storage.localAccess).toBeNull();

  await page.getByRole('button', { name: 'My profile' }).click();
  await page.getByLabel('Full name').fill('Synthetic Candidate');
  await page.getByLabel('Location', { exact: true }).fill('Test City');
  await page.getByRole('button', { name: 'Save profile' }).click();
  await expect(page.getByText('Profile saved')).toBeVisible();
  const profile = await api(page, 'GET', '/profile');
  expect(profile.status).toBe(200);
  expect((profile.data as any).fullName).toBe('Synthetic Candidate');

  const refreshToken = await page.evaluate(() => sessionStorage.getItem('refreshToken'));
  const rotated = await request.post('/api/auth/refresh', { data: { refreshToken } });
  expect(rotated.status()).toBe(200);
  const rotatedBody = await rotated.json();
  const oldRefresh = await request.post('/api/auth/refresh', { data: { refreshToken } });
  expect(oldRefresh.status()).toBe(401);
  await request.post('/api/auth/logout', { data: { refreshToken: rotatedBody.refreshToken } });

  await page.evaluate(() => sessionStorage.setItem('accessToken', 'expired-invalid-token'));
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('accessToken'))).toBeNull();
  await login(page, email);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('refreshToken'))).toBeNull();
});

test('permitted public Greenhouse job import and repeat-import deduplication', async ({ page }) => {
  await register(page);
  await page.getByRole('button', { name: 'Job discovery' }).click();
  await page.getByRole('button', { name: 'Import Feed / ATS' }).click();
  await page.getByRole('button', { name: 'Greenhouse Board / URL' }).click();
  await page.getByLabel(/Greenhouse Board Token or Job Posting URL/).fill('https://boards.greenhouse.io/gitlab/jobs/8638232002');
  const importedResponse = page.waitForResponse((r) => r.url().includes('/api/jobs/import/greenhouse'));
  await page.getByRole('button', { name: 'Ingest Opportunities' }).click();
  const imported = await (await importedResponse).json();
  expect(imported.importedCount).toBe(1);
  expect(imported.status).toBe('SUCCESS');
  const repeated = await api(page, 'POST', '/jobs/import/greenhouse', { urlOrToken: 'https://boards.greenhouse.io/gitlab/jobs/8638232002' });
  expect(repeated.status).toBe(200);
  expect((repeated.data as any).importedCount).toBe(0);
  expect((repeated.data as any).skippedCount).toBeGreaterThan(0);
});

test('resume upload and correction, job matching, bulk drafts, consolidated review, and ownership', async ({ page, browser }) => {
  await register(page);
  await page.getByRole('button', { name: 'Profile records' }).click();
  await page.getByRole('button', { name: /^Skills/ }).click();
  await page.getByLabel('Skill', { exact: true }).fill('Python');
  await page.getByLabel('Verified by you').check();
  await page.getByLabel('Evidence or source').fill('Synthetic E2E fixture');
  await page.getByRole('button', { name: 'Add record' }).click();
  await expect(page.getByText('Record saved.')).toBeVisible();

  await page.getByRole('button', { name: 'Resume studio' }).click();
  await page.locator('input[type=file]').last().setInputFiles({
    name: 'synthetic-e2e-resume.pdf', mimeType: 'application/pdf', buffer: syntheticPdf()
  });
  await expect(page.getByText('Resume uploaded and preserved')).toBeVisible({ timeout: 30_000 });
  const resumesResult = await api(page, 'GET', '/resumes');
  expect(resumesResult.status).toBe(200);
  const resume = (resumesResult.data as any[])[0];
  expect(resume.filename).toBe('synthetic-e2e-resume.pdf');
  await expect(page.getByLabel('Resume Text (Candidate editable)')).toBeVisible();
  await page.getByLabel('Resume Text (Candidate editable)').fill('Synthetic Candidate. Verified skills: Python and SQL. Sample project: built a small data dashboard.');
  await page.getByRole('button', { name: 'Save as New Version' }).click();
  await expect(page.getByText('Review saved as a new version. Your original file is unchanged.')).toBeVisible();
  const versions = await api(page, 'GET', `/resumes/${resume.id}/versions`);
  expect(versions.status).toBe(200);
  expect((versions.data as any[]).some((v) => v.version === 2)).toBeTruthy();

  const firstTitle = `E2E Analyst ${randomUUID().slice(0, 6)}`;
  const secondTitle = `E2E Data Engineer ${randomUUID().slice(0, 6)}`;
  await createManualJob(page, firstTitle, ['Python', 'Kubernetes']);
  await createManualJob(page, secondTitle, ['Python', 'SQL']);
  const jobs = await api(page, 'GET', '/jobs');
  expect(jobs.status).toBe(200);
  const first = (jobs.data as any[]).find((job) => job.title === firstTitle);
  const second = (jobs.data as any[]).find((job) => job.title === secondTitle);
  expect(first).toBeTruthy();
  expect(second).toBeTruthy();
  const match = await api(page, 'GET', `/jobs/${first.id}/match`);
  expect(match.status).toBe(200);
  expect((match.data as any).missingRequirements).toContain('Kubernetes');
  expect((match.data as any).eligibilityStatus).toBe('UNKNOWN');

  await page.getByRole('button', { name: 'Application bundles' }).click();
  await page.getByLabel('Resume document for this batch').selectOption(resume.id);
  for (const title of [firstTitle, secondTitle]) {
    const versionSelect = page.getByLabel(`Resume version for ${title}`);
    await page.locator('label').filter({ hasText: title }).locator('input[type=checkbox]').first().check();
    await expect(versionSelect).toBeEnabled();
    await expect(versionSelect.locator('option[value="2"]')).toHaveCount(1);
    await versionSelect.selectOption('2');
  }
  const bulkResponse = page.waitForResponse((r) => r.url().includes('/api/bundles/bulk'));
  await page.getByRole('button', { name: 'Create 2 draft preparations' }).click();
  expect((await bulkResponse).status()).toBe(201);
  await expect(page.getByRole('heading', { name: 'Review 2 separate job preparations' })).toBeVisible();
  const approvalChecks = page.locator('label.review-check input[type=checkbox]');
  await approvalChecks.nth(0).check();
  await approvalChecks.nth(1).check();
  await page.getByRole('button', { name: 'Approve 2 reviewed preparations' }).click();
  await expect(page.getByText('Ready for Application')).toHaveCount(2);
  const bundles = await api(page, 'GET', '/bundles');
  const owned = (bundles.data as any[]).filter((b) => [first.id, second.id].includes(b.jobId));
  expect(owned).toHaveLength(2);
  expect(owned.every((b) => b.status === 'READY' && b.resumeId === resume.id && b.resumeVersionNumber === 2)).toBeTruthy();
  const tracked = await api(page, 'POST', '/applications', { jobId: first.id, bundleId: owned[0].id, status: 'SAVED' });
  expect(tracked.status).toBe(201);
  const prep = await api(page, 'GET', `/applications/${(tracked.data as any).id}/interview-prep`);
  expect(prep.status).toBe(200);
  const questions = typeof (prep.data as any).questions === 'string' ? JSON.parse((prep.data as any).questions) : (prep.data as any).questions;
  const aiDisabled = await api(page, 'POST', `/applications/${(tracked.data as any).id}/interview-prep/draft`, { questionId: questions[0].id });
  expect(aiDisabled.status).toBe(503);

  const context = await browser.newContext({ baseURL: process.env.APPLYPILOT_E2E_WEB ?? 'http://localhost:15174' });
  const other = await context.newPage();
  const otherEmail = await register(other, emailFor('other'));
  expect(otherEmail).toContain('@example.test');
  const token = await other.evaluate(() => sessionStorage.getItem('accessToken'));
  const forbidden = await other.evaluate(async ({ token, resumeId }) => {
    const response = await fetch(`/api/resumes/${resumeId}/versions`, { headers: { Authorization: `Bearer ${token}` } });
    return response.status;
  }, { token, resumeId: resume.id });
  expect(forbidden).toBe(404);
  await context.close();
});

test('application tracking, follow-up completion, interview prep, reminders, notification read state', async ({ page }) => {
  await register(page);
  const title = `E2E Product Analyst ${randomUUID().slice(0, 6)}`;
  await createManualJob(page, title, ['SQL']);
  await page.getByRole('button', { name: 'Applications' }).click();
  await page.getByRole('button', { name: 'Track application' }).click();
  await expect(page.getByText('Application tracking started.')).toBeVisible();
  await page.getByLabel(`Status for ${title}`).selectOption('INTERVIEW');
  await expect(page.getByText('Application status updated.')).toBeVisible();
  await page.getByRole('button', { name: 'Follow-up' }).click();
  await expect(page.getByText('Follow-up reminder added.')).toBeVisible();
  await page.getByRole('button', { name: 'Interview prep' }).click();
  const interviewDialog = page.getByRole('dialog', { name: new RegExp(`Interview preparation · ${title}`) });
  await expect(interviewDialog).toBeVisible();
  await expect(interviewDialog.getByRole('heading', { name: new RegExp(`Interview preparation · ${title}`) }).last()).toBeVisible();
  const answer = page.locator('textarea').first();
  await answer.fill('Synthetic answer saved through the authenticated browser flow.');
  await page.getByRole('button', { name: 'Save answer' }).first().click();
  await expect(page.getByText('Interview answer saved.')).toBeVisible();
  await page.getByRole('button', { name: 'Close' }).first().click();

  await expect(page.getByText('Follow up on application')).toBeVisible();
  await page.getByRole('button', { name: 'Complete' }).click();
  await expect(page.getByText('1 completed')).toBeVisible();
  const followups = await api(page, 'GET', '/followups');
  expect((followups.data as any).completed.length).toBe(1);

  const reminder = page.getByLabel('Follow-up reminders');
  await reminder.uncheck();
  await expect(page.getByText('Notification preferences saved.')).toBeVisible();
  const prefs = await api(page, 'GET', '/notification-preferences');
  expect((prefs.data as any).followupReminders).toBe(false);
  const markRead = page.getByRole('button', { name: 'Mark read' }).first();
  await expect(markRead).toBeVisible();
  await markRead.click();
  await expect(page.getByText('No due notifications.')).toBeVisible();
  const applications = await api(page, 'GET', '/applications');
  expect((applications.data as any[])[0].status).toBe('INTERVIEW');
});
