import { ApplicationBundleData, CandidateProfile } from '../types';
import { hasExactResumeDownloadPath } from '../resume-version';

const API_BASE = 'http://localhost:8081/api';
const SESSION_KEYS = ['accessToken', 'refreshToken', 'email'];

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  const handlers: Record<string, () => Promise<unknown>> = {
    LOGIN: () => handleLogin(message.email, message.password),
    LOGOUT: () => handleLogout(),
    GET_AUTH_STATUS: () => handleGetAuthStatus(),
    FETCH_PROFILE: () => handleFetchProfile(),
    FETCH_BUNDLES: () => handleFetchBundles(),
    GET_APPROVED_RESUME: () => handleGetApprovedResume(message.bundleId),
    SYNC_BUNDLE_STATUS: () => handleUpdateBundleStatus(message.bundleId, message.status)
  };
  const handler = handlers[message.type];
  if (!handler) {
    sendResponse({ success: false, error: 'Unsupported ApplyPilot action.' });
    return false;
  }
  handler().then(sendResponse).catch(() => sendResponse({ success: false, error: 'ApplyPilot could not complete that request.' }));
  return true;
});

async function handleLogin(email: string, password: string): Promise<{ success: boolean; error?: string }> {
  try {
    const response = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (!response.ok) return { success: false, error: response.status === 401 ? 'Email or password was not accepted.' : 'ApplyPilot could not sign you in.' };
    const session = await response.json();
    await chrome.storage.session.set({ accessToken: session.accessToken, refreshToken: session.refreshToken, email: session.email });
    return { success: true };
  } catch {
    return { success: false, error: 'Cannot reach ApplyPilot at http://localhost:8081. Start the local app and retry.' };
  }
}

async function handleLogout(): Promise<{ success: boolean }> {
  const session = await chrome.storage.session.get(['refreshToken']);
  if (session.refreshToken) {
    try {
      await fetch(`${API_BASE}/auth/logout`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken })
      });
    } catch { /* Local sign-out still clears the browser session when the API is offline. */ }
  }
  await chrome.storage.session.remove(SESSION_KEYS);
  return { success: true };
}

async function handleGetAuthStatus(): Promise<{ authenticated: boolean; email?: string }> {
  const session = await chrome.storage.session.get(SESSION_KEYS);
  return { authenticated: Boolean(session.accessToken), email: session.email };
}

async function authenticatedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const session = await chrome.storage.session.get(['accessToken']);
  if (!session.accessToken) throw new Error('SESSION_MISSING');
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${session.accessToken}`);
  return fetch(`${API_BASE}${path}`, { ...init, headers });
}

async function handleFetchProfile(): Promise<{ success: boolean; profile?: CandidateProfile; error?: string }> {
  try {
    const [profileResp, skillsResp, educationResp, experienceResp, projectResp] = await Promise.all([
      authenticatedFetch('/profile'), authenticatedFetch('/profile/records/skills'),
      authenticatedFetch('/profile/records/education'), authenticatedFetch('/profile/records/experience'),
      authenticatedFetch('/profile/records/projects')
    ]);
    if ([profileResp, skillsResp, educationResp, experienceResp, projectResp].some((r) => r.status === 401)) {
      await handleLogout();
      return { success: false, error: 'Your session expired. Sign in again.' };
    }
    if ([profileResp, skillsResp, educationResp, experienceResp, projectResp].some((r) => !r.ok)) {
      return { success: false, error: 'Could not load verified profile records. Retry before filling.' };
    }
    const [base, skills, education, experience, projects] = await Promise.all([
      profileResp.json(), skillsResp.json(), educationResp.json(), experienceResp.json(), projectResp.json()
    ]);
    const session = await chrome.storage.session.get(['email']);
    const profile: CandidateProfile = {
      ...base,
      email: session.email,
      skills: skills.filter((row: any) => row.verified === true).map((row: any) => String(row.name)),
      education: education.map((row: any) => ({
        institution: row.institution, degree: row.degree, fieldOfStudy: row.field_of_study,
        graduationYear: row.graduation_year == null ? undefined : Number(row.graduation_year)
      })),
      experience: experience.map((row: any) => ({
        company: row.company, title: row.title, description: row.description,
        startDate: row.start_date, endDate: row.end_date
      })),
      projects: projects.map((row: any) => ({ name: row.name, description: row.description, technologies: row.technologies }))
    };
    return { success: true, profile };
  } catch (error) {
    return { success: false, error: error instanceof Error && error.message === 'SESSION_MISSING'
      ? 'Sign in to load your verified profile.' : 'ApplyPilot could not load your profile. Check the local API and retry.' };
  }
}

async function handleFetchBundles(): Promise<{ success: boolean; bundles: ApplicationBundleData[]; error?: string }> {
  try {
    const response = await authenticatedFetch('/bundles');
    if (response.status === 401) {
      await handleLogout();
      return { success: false, bundles: [], error: 'Your session expired. Sign in again.' };
    }
    if (!response.ok) return { success: false, bundles: [], error: 'Could not load application bundles.' };
    return { success: true, bundles: await response.json() };
  } catch {
    return { success: false, bundles: [], error: 'Cannot reach ApplyPilot. Check that the local API is running.' };
  }
}

async function handleGetApprovedResume(bundleId: string): Promise<{ success: boolean; bundleId?: string; resumeId?: string; version?: number; filename?: string; mimeType?: string; bytes?: ArrayBuffer; error?: string }> {
  try {
    const response = await authenticatedFetch('/bundles');
    if (response.status === 401) {
      await handleLogout();
      return { success: false, error: 'Your session expired. Sign in again.' };
    }
    if (!response.ok) return { success: false, error: 'Could not verify the selected application bundle.' };
    const bundles = await response.json() as ApplicationBundleData[];
    const bundle = bundles.find((item) => item.id === bundleId);
    if (!bundle || bundle.status !== 'READY' || !bundle.resumeId || !Number.isInteger(bundle.resumeVersionNumber) || !bundle.resumeDownloadUrl) {
      return { success: false, error: 'This READY bundle does not identify an exact resume version.' };
    }
    if (!hasExactResumeDownloadPath(bundle)) return { success: false, error: 'Resume version reference did not match the selected bundle.' };
    const fileResponse = await authenticatedFetch(bundle.resumeDownloadUrl.slice('/api'.length), { signal: AbortSignal.timeout(20000) });
    if (fileResponse.status === 401) {
      await handleLogout();
      return { success: false, error: 'Your session expired. Sign in again.' };
    }
    if (!fileResponse.ok) return { success: false, error: 'The exact approved resume file could not be retrieved.' };
    const mimeType = fileResponse.headers.get('content-type')?.split(';')[0].trim().toLowerCase() || '';
    if (!['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'].includes(mimeType)) {
      return { success: false, error: 'The selected resume version is not a supported PDF or DOCX file.' };
    }
    const filename = fileResponse.headers.get('content-disposition')?.match(/filename="?([^";]+)"?/i)?.[1] || `resume-v${bundle.resumeVersionNumber}.${mimeType === 'application/pdf' ? 'pdf' : 'docx'}`;
    const bytes = await fileResponse.arrayBuffer();
    if (!bytes.byteLength || bytes.byteLength > 15 * 1024 * 1024) return { success: false, error: 'Resume file is empty or exceeds the 15 MB attachment limit.' };
    return { success: true, bundleId: bundle.id, resumeId: bundle.resumeId, version: bundle.resumeVersionNumber, filename, mimeType, bytes };
  } catch (error) {
    return { success: false, error: error instanceof DOMException && error.name === 'TimeoutError' ? 'Resume retrieval timed out. Retry when the local API is available.' : 'Could not retrieve the exact resume version. Check the local API and retry.' };
  }
}

async function handleUpdateBundleStatus(bundleId: string, status: string): Promise<{ success: boolean; error?: string }> {
  if (!['IN_REVIEW', 'SUBMITTED_BY_CANDIDATE'].includes(status)) {
    return { success: false, error: 'That application state can only be recorded from ApplyPilot.' };
  }
  try {
    const response = await authenticatedFetch(`/bundles/${encodeURIComponent(bundleId)}`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status })
    });
    if (response.status === 401) {
      await handleLogout();
      return { success: false, error: 'Your session expired. Sign in again.' };
    }
    return response.ok ? { success: true } : { success: false, error: 'Application status was not updated.' };
  } catch {
    return { success: false, error: 'Cannot reach ApplyPilot to update application status.' };
  }
}
