import { ApplicationBundleData, CandidateProfile } from '../types';
import { matchesBundleResume, selectableBundles } from './workflow';

document.addEventListener('DOMContentLoaded', () => { void initialize(); });

async function initialize(): Promise<void> {
  const el = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
  const authSection = el<HTMLDivElement>('auth-section');
  const mainSection = el<HTMLDivElement>('main-section');
  const feedback = el<HTMLDivElement>('feedback');
  const loginError = el<HTMLDivElement>('login-error');
  const bundleSelect = el<HTMLSelectElement>('bundle-select');
  const autofillButton = el<HTMLButtonElement>('autofill-btn');
  const attachResumeButton = el<HTMLButtonElement>('attach-resume-btn');
  const resumeApproved = el<HTMLInputElement>('resume-approved');
  const legalConfirmed = el<HTMLInputElement>('legal-confirmed');
  const submittedConfirmed = el<HTMLInputElement>('submitted-confirmed');
  const submittedButton = el<HTMLButtonElement>('submitted-btn');
  const resumeInfo = el<HTMLDivElement>('resume-review-info');
  let bundles: ApplicationBundleData[] = [];
  let currentTabId: number | undefined;

  const authStatus = await chrome.runtime.sendMessage({ type: 'GET_AUTH_STATUS' });
  authSection.style.display = authStatus.authenticated ? 'none' : 'block';
  mainSection.style.display = authStatus.authenticated ? 'block' : 'none';
  if (!authStatus.authenticated) return;
  el<HTMLSpanElement>('user-email').textContent = authStatus.email || 'Candidate account';

  const profileResult = await chrome.runtime.sendMessage({ type: 'FETCH_PROFILE' });
  if (!profileResult.success) {
    feedback.textContent = profileResult.error || 'Could not load verified profile data.';
    autofillButton.disabled = true;
  }
  const candidateProfile = profileResult.success ? profileResult.profile as CandidateProfile : undefined;

  const bundleResult = await chrome.runtime.sendMessage({ type: 'FETCH_BUNDLES' });
  if (bundleResult.success && Array.isArray(bundleResult.bundles)) {
    const allBundles = bundleResult.bundles as ApplicationBundleData[];
    bundles = selectableBundles(allBundles);
    const blockedDuplicates = allBundles.filter((bundle) => bundle.status === 'READY' && allBundles.some((prior) => prior.jobId === bundle.jobId && prior.status === 'SUBMITTED_BY_CANDIDATE'));
    if (blockedDuplicates.length) feedback.textContent = 'A candidate-confirmed submission already exists for one or more jobs; those bundles are hidden to reduce duplicate applications.';
    for (const bundle of bundles) {
      const option = document.createElement('option');
      option.value = bundle.id;
      option.textContent = `${bundle.jobTitle} (${bundle.company || 'Employer'}) · resume v${bundle.resumeVersionNumber ?? 'original'}`;
      bundleSelect.appendChild(option);
    }
  } else {
    feedback.textContent = bundleResult.error || 'Could not load application bundles.';
  }

  const [activeTab] = await chrome.tabs.query({ active: true, currentWindow: true });
  currentTabId = activeTab?.id;
  let detectedPage: any;
  if (currentTabId == null) {
    el<HTMLDivElement>('ats-badge').textContent = 'No active webpage';
  } else {
    try {
      detectedPage = await detectActivePage(currentTabId);
      const badge = el<HTMLDivElement>('ats-badge');
      const title = el<HTMLDivElement>('job-title');
      const info = el<HTMLDivElement>('ats-info');
      if (detectedPage?.detected) {
        badge.textContent = `Detected ${String(detectedPage.ats).toUpperCase()} application form`;
        badge.className = 'status-badge detected';
        title.textContent = detectedPage.jobTitle || 'Application page';
        info.textContent = `${detectedPage.fieldsCount} fields detected. Unsupported questions will be left for you.`;
      } else {
        badge.textContent = 'No supported application form detected';
        title.textContent = 'This page is not ready for field preparation';
        info.textContent = 'Open an application form on a supported public portal. Sign-in, CAPTCHA, and access restrictions are not bypassed.';
      }
    } catch {
      el<HTMLDivElement>('ats-badge').textContent = 'Form detector unavailable on this page';
      el<HTMLDivElement>('job-title').textContent = 'Unsupported or restricted page';
      el<HTMLDivElement>('ats-info').textContent = 'Reload the page and try a supported application form. ApplyPilot does not bypass access controls.';
    }
  }

  const updateReadyState = () => {
    const selected = bundles.find((bundle) => bundle.id === bundleSelect.value);
    const hasApprovedResume = Boolean(selected?.resumeId && selected.resumeVersionNumber && resumeApproved.checked);
    resumeInfo.textContent = selected
      ? selected.resumeId && selected.resumeVersionNumber
        ? `Selected application bundle uses resume version ${selected.resumeVersionNumber}. Attach only after reviewing this exact version.`
        : 'This bundle has no selected resume version. Choose a prepared bundle with an approved resume.'
      : 'Choose a READY bundle prepared in ApplyPilot before autofill.';
    autofillButton.disabled = !candidateProfile || !detectedPage?.detected || currentTabId == null || !hasApprovedResume;
    attachResumeButton.disabled = !detectedPage?.detected || !detectedPage?.hasResumeUpload || currentTabId == null || !hasApprovedResume;
    submittedButton.disabled = !selected || !submittedConfirmed.checked;
  };
  bundleSelect.addEventListener('change', updateReadyState);
  resumeApproved.addEventListener('change', updateReadyState);
  submittedConfirmed.addEventListener('change', updateReadyState);
  updateReadyState();

  attachResumeButton.addEventListener('click', async () => {
    const selected = bundles.find((bundle) => bundle.id === bundleSelect.value);
    if (!selected || currentTabId == null || !resumeApproved.checked) return;
    attachResumeButton.disabled = true;
    feedback.textContent = `Retrieving the exact resume version ${selected.resumeVersionNumber}…`;
    try {
      const file = await chrome.runtime.sendMessage({ type: 'GET_APPROVED_RESUME', bundleId: selected.id });
      if (!file?.success) throw new Error(file?.error || 'The approved resume could not be retrieved.');
      if (!matchesBundleResume(selected, file)) throw new Error('The returned resume version did not match the selected bundle.');
      const result = await chrome.tabs.sendMessage(currentTabId, { type: 'ATTACH_APPROVED_RESUME', bytes: file.bytes, filename: file.filename, mimeType: file.mimeType, version: file.version });
      feedback.textContent = result?.success
        ? `Resume v${result.version} attached and confirmed by the page as ${result.filename}. Review it before proceeding.`
        : (result?.error || 'The portal did not confirm attachment. Attach the selected version manually.');
    } catch (error) {
      feedback.textContent = error instanceof Error ? error.message : 'Resume attachment failed. Attach the selected version manually.';
    } finally {
      updateReadyState();
    }
  });

  el<HTMLFormElement>('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    loginError.textContent = 'Connecting to local ApplyPilot…';
    const email = el<HTMLInputElement>('login-email').value.trim();
    const passwordInput = el<HTMLInputElement>('login-pass');
    const result = await chrome.runtime.sendMessage({ type: 'LOGIN', email, password: passwordInput.value });
    passwordInput.value = '';
    loginError.textContent = result.success ? 'Connected. Reopen the extension to continue.' : (result.error || 'Sign in failed.');
    if (result.success) window.location.reload();
  });

  el<HTMLButtonElement>('logout-btn').addEventListener('click', async () => {
    await chrome.runtime.sendMessage({ type: 'LOGOUT' });
    window.location.reload();
  });

  autofillButton.addEventListener('click', async () => {
    const selected = bundles.find((bundle) => bundle.id === bundleSelect.value);
    if (!selected || !candidateProfile || currentTabId == null) return;
    autofillButton.disabled = true;
    feedback.textContent = 'Preparing verified fields. Existing entries and unsupported questions will be left unchanged.';
    try {
      const result = await chrome.tabs.sendMessage(currentTabId, {
        type: 'TRIGGER_AUTOFILL', profile: candidateProfile, bundle: selected,
        confirmLegalAnswers: legalConfirmed.checked
      });
      if (!result?.success) throw new Error('Preparation did not complete.');
      const state = await chrome.runtime.sendMessage({ type: 'SYNC_BUNDLE_STATUS', bundleId: selected.id, status: 'IN_REVIEW' });
      const details = `${result.filledCount} fields prepared; ${result.uncertainCount} need review; ${result.unsupportedCount} left for manual entry.`;
      feedback.textContent = `${details} Review every highlighted field. The resume was not attached; attach the approved version manually. ${state.success ? 'Bundle is marked IN_REVIEW.' : (state.error || 'Bundle review status could not be synced.')}`;
    } catch {
      feedback.textContent = 'Field preparation failed. Your existing entries were preserved; retry after checking the page.';
    } finally {
      autofillButton.disabled = false;
    }
  });

  submittedButton.addEventListener('click', async () => {
    const selected = bundles.find((bundle) => bundle.id === bundleSelect.value);
    if (!selected || !submittedConfirmed.checked) return;
    submittedButton.disabled = true;
    const result = await chrome.runtime.sendMessage({ type: 'SYNC_BUNDLE_STATUS', bundleId: selected.id, status: 'SUBMITTED_BY_CANDIDATE' });
    feedback.textContent = result.success
      ? 'Candidate-confirmed submission recorded. ApplyPilot did not click Submit.'
      : (result.error || 'Could not record candidate-confirmed submission.');
    submittedButton.disabled = false;
  });
}

async function detectActivePage(tabId: number): Promise<any> {
  try {
    return await chrome.tabs.sendMessage(tabId, { type: 'DETECT_PAGE' });
  } catch {
    // activeTab grants temporary access only after the user opens the extension on that tab.
    // This enables the same conservative generic detector without broad all-sites permission.
    await chrome.scripting.executeScript({ target: { tabId }, files: ['dist/content.js'] });
    return chrome.tabs.sendMessage(tabId, { type: 'DETECT_PAGE' });
  }
}
