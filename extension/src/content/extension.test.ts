// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { detectAtsForm, catalogFields } from './detector';
import { mapCandidateFieldsToForm } from './field-mapper';
import { applyValueToElement, executeAutofill, attachApprovedResume } from './autofill';
import type { ApplicationBundleData, CandidateProfile, DetectedFieldInfo } from '../types';
import { matchesBundleResume, selectableBundles } from '../popup/workflow';
import { hasExactResumeDownloadPath } from '../resume-version';

const profile: CandidateProfile = {
  fullName: 'Asha Rao', email: 'asha@example.test', phone: '+91 9999999999', location: 'Pune',
  education: [{ institution: 'Example University', degree: 'MSc', fieldOfStudy: 'Data Science', graduationYear: 2024 }],
  skills: ['Python', 'SQL'], legallyAuthorized: true, requiresSponsorship: false
};

function fields(markup: string): Record<string, DetectedFieldInfo> {
  document.body.innerHTML = markup;
  return catalogFields(document.body);
}

describe('portal detection and safe field preparation', () => {
  it('detects common Greenhouse application forms and resume inputs', () => {
    document.body.innerHTML = '<form id="application_form"><label for="email">Email</label><input id="email" type="email"><input type="file" name="resume"></form>';
    const detected = detectAtsForm(document);
    expect(detected.ats).toBe('greenhouse');
    expect(detected.isApplicationPage).toBe(true);
    expect(detected.hasResumeUpload).toBe(true);
  });

  it('detects generic forms only when application-like fields exist', () => {
    document.body.innerHTML = '<form><input type="email" name="applicant_email"></form>';
    const detected = detectAtsForm(document);
    expect(detected.ats).toBe('generic');
    expect(detected.isApplicationPage).toBe(true);
  });

  it.each([
    ['Lever', '<form id="application-form"><input name="name"></form>', 'lever'],
    ['Ashby', '<form data-test="application-form"><input name="email"></form>', 'ashby'],
    ['Workday', '<div data-automation-id="applyFlow"><input name="email"></div>', 'workday']
  ])('recognizes the %s detector pattern without claiming portal runtime support', (_name, markup, expected) => {
    document.body.innerHTML = markup;
    expect(detectAtsForm(document).ats).toBe(expected);
  });

  it('maps profile evidence and leaves unknown questions unsupported', () => {
    const detected = fields('<label for="full_name">Full name</label><input id="full_name"><label for="school">University</label><input id="school"><textarea name="favorite_color" aria-label="Favorite color"></textarea>');
    const assignments = mapCandidateFieldsToForm(detected, profile);
    expect(assignments.map((entry) => [entry.fieldLabel, entry.value])).toContainEqual(['Full Name', 'Asha Rao']);
    expect(assignments.map((entry) => [entry.fieldLabel, entry.value])).toContainEqual(['School / Institution', 'Example University']);
    expect(assignments.some((entry) => entry.element.getAttribute('aria-label') === 'Favorite color')).toBe(false);
  });

  it('does not invent missing degree, major, or notice period', () => {
    const candidate = { ...profile, education: [], noticePeriod: undefined };
    const detected = fields('<input name="degree" aria-label="Degree"><input name="field_of_study" aria-label="Field of study"><input name="notice_period" aria-label="Notice period">');
    expect(mapCandidateFieldsToForm(detected, candidate)).toHaveLength(0);
  });

  it('requires explicit candidate confirmation for legal/work authorization fields', () => {
    const detected = fields('<input name="work_authorization" aria-label="Are you legally authorized to work?"><input name="sponsorship" aria-label="Will you require visa sponsorship?">');
    expect(mapCandidateFieldsToForm(detected, profile)).toHaveLength(0);
    const confirmed = mapCandidateFieldsToForm(detected, profile, undefined, { confirmLegalAnswers: true });
    expect(confirmed).toHaveLength(2);
    expect(confirmed.every((entry) => entry.confidence === 'UNCERTAIN' && entry.requiresCandidateConfirmation)).toBe(true);
  });

  it('does not map a screening answer to a legal question without confirmation', () => {
    const detected = fields('<input name="citizenship" aria-label="Are you authorized to work in this country?">');
    const bundle = { applicationAnswers: [{ question: 'Are you authorized to work in this country?', answer: 'Yes' }] } as ApplicationBundleData;
    expect(mapCandidateFieldsToForm(detected, profile, bundle)).toHaveLength(0);
    expect(mapCandidateFieldsToForm(detected, profile, bundle, { confirmLegalAnswers: true })).toHaveLength(1);
  });

  it('selects only a matching dropdown option and leaves unmatched values unchanged', () => {
    const select = document.createElement('select');
    select.innerHTML = '<option value="">Choose</option><option value="remote">Remote</option><option value="onsite">On-site</option>';
    expect(applyValueToElement({ element: select, value: 'Remote', fieldLabel: 'Location preference', confidence: 'HIGH', matchReason: 'exact option' })).toBe(true);
    expect(select.value).toBe('remote');
    const before = select.value;
    expect(applyValueToElement({ element: select, value: 'Hybrid', fieldLabel: 'Location preference', confidence: 'HIGH', matchReason: 'no option' })).toBe(false);
    expect(select.value).toBe(before);
  });

  it('preserves existing user-entered values and checked boxes', () => {
    const input = document.createElement('input');
    input.value = 'Already entered';
    expect(applyValueToElement({ element: input, value: 'Asha Rao', fieldLabel: 'Name', confidence: 'HIGH', matchReason: 'name' })).toBe(false);
    expect(input.value).toBe('Already entered');
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox'; checkbox.checked = true;
    expect(applyValueToElement({ element: checkbox, value: false, fieldLabel: 'Consent', confidence: 'HIGH', matchReason: 'consent' })).toBe(false);
    expect(checkbox.checked).toBe(true);
  });

  it('sets checkboxes only from explicit mapped booleans', () => {
    const checkbox = document.createElement('input'); checkbox.type = 'checkbox';
    expect(applyValueToElement({ element: checkbox, value: true, fieldLabel: 'Verified preference', confidence: 'HIGH', matchReason: 'candidate choice' })).toBe(true);
    expect(checkbox.checked).toBe(true);
  });

  it('does not report an application as submitted after field preparation', () => {
    const detected = fields('<form id="application_form"><input name="email" type="email"></form>');
    const result = executeAutofill(profile, undefined, document);
    expect(result.filledCount).toBeGreaterThan(0);
    expect(result.unsupportedCount).toBeGreaterThanOrEqual(0);
    expect(detected).toBeDefined();
  });

  it('rejects a missing or unverified resume payload without touching the form', () => {
    document.body.innerHTML = '<input type="file" name="resume">';
    expect(attachApprovedResume({ filename: 'resume.pdf', mimeType: 'application/pdf', version: 2 }).success).toBe(false);
    expect(attachApprovedResume({ bytes: new ArrayBuffer(4), filename: 'resume.exe', mimeType: 'application/x-msdownload', version: 2 }).success).toBe(false);
    expect((document.querySelector('input') as HTMLInputElement).files).toHaveLength(0);
  });

  it('reports unsupported and ambiguous upload controls without choosing one', () => {
    const payload = { bytes: new ArrayBuffer(4), filename: 'resume-v2.pdf', mimeType: 'application/pdf', version: 2 };
    document.body.innerHTML = '<input type="file" name="resume"><input type="file" name="cv">';
    expect(attachApprovedResume(payload).success).toBe(false);
    document.body.innerHTML = '<form><input type="text" name="resume"></form>';
    expect(attachApprovedResume(payload).success).toBe(false);
  });

  it('confirms attachment to one empty resume input and preserves an existing file', () => {
    const input = document.createElement('input'); input.type = 'file'; input.name = 'resume'; document.body.replaceChildren(input);
    let selected: File[] = [];
    Object.defineProperty(input, 'files', { configurable: true, get: () => selected, set: (value: FileList) => { selected = Array.from(value); } });
    class TestTransfer {
      private addedFiles: File[] = [];
      items = { add: (file: File) => this.addedFiles.push(file) };
      get files() { return this.addedFiles as unknown as FileList; }
    }
    vi.stubGlobal('DataTransfer', TestTransfer);
    const result = attachApprovedResume({ bytes: new ArrayBuffer(16), filename: 'resume-v4.pdf', mimeType: 'application/pdf', version: 4 });
    expect(result).toMatchObject({ success: true, filename: 'resume-v4.pdf', version: 4 });
    expect(selected[0].name).toBe('resume-v4.pdf');
    const existing = new File(['existing'], 'candidate-choice.pdf', { type: 'application/pdf' });
    selected = [existing];
    expect(attachApprovedResume({ bytes: new ArrayBuffer(16), filename: 'resume-v4.pdf', mimeType: 'application/pdf', version: 4 }).success).toBe(false);
    expect(selected[0]).toBe(existing);
    vi.unstubAllGlobals();
  });

  it('returns a controlled failure when the portal refuses the file assignment', () => {
    const input = document.createElement('input'); input.type = 'file'; input.name = 'resume'; document.body.replaceChildren(input);
    Object.defineProperty(input, 'files', { configurable: true, get: () => [], set: () => undefined });
    class RejectedTransfer {
      items = { add: (_file: File) => undefined };
      files = [] as unknown as FileList;
    }
    vi.stubGlobal('DataTransfer', RejectedTransfer);
    expect(attachApprovedResume({ bytes: new ArrayBuffer(12), filename: 'resume-v2.pdf', mimeType: 'application/pdf', version: 2 }))
      .toMatchObject({ success: false, error: 'The portal did not confirm the selected resume. Attach it manually.' });
    vi.unstubAllGlobals();
  });

  it('prevents selecting already-submitted jobs and collapses duplicate READY bundles', () => {
    const bundle = (id: string, jobId: string, status: ApplicationBundleData['status']) => ({
      id, jobId, jobTitle: 'Engineer', company: 'Example', status, applicationAnswers: []
    }) as ApplicationBundleData;
    const available = selectableBundles([
      bundle('ready-a', 'job-a', 'READY'), bundle('ready-b', 'job-a', 'READY'),
      bundle('ready-c', 'job-b', 'READY'), bundle('submitted-b', 'job-b', 'SUBMITTED_BY_CANDIDATE')
    ]);
    expect(available.map((item) => item.id)).toEqual(['ready-a']);
  });

  it('requires exact bundle, resume ID, and version match before attachment proceeds', () => {
    const bundle = { id: 'bundle-a', jobId: 'job-a', jobTitle: 'Engineer', company: 'Example', resumeId: 'resume-a', resumeVersionNumber: 4, status: 'READY', applicationAnswers: [] } as ApplicationBundleData;
    expect(matchesBundleResume(bundle, { success: true, bundleId: 'bundle-a', resumeId: 'resume-a', version: 4 })).toBe(true);
    expect(matchesBundleResume(bundle, { success: true, bundleId: 'bundle-a', resumeId: 'resume-a', version: 3 })).toBe(false);
    expect(matchesBundleResume(bundle, { success: true, bundleId: 'bundle-a', resumeId: 'resume-b', version: 4 })).toBe(false);
  });

  it('accepts only the selected bundle exact version download path', () => {
    const base = { id: 'bundle-a', jobId: 'job-a', jobTitle: 'Engineer', company: 'Example', resumeId: 'resume-a', resumeVersionNumber: 4, status: 'READY', applicationAnswers: [] } as ApplicationBundleData;
    expect(hasExactResumeDownloadPath({ ...base, resumeDownloadUrl: '/api/resumes/resume-a/versions/4/download' })).toBe(true);
    expect(hasExactResumeDownloadPath({ ...base, resumeDownloadUrl: '/api/resumes/resume-a/versions/3/download' })).toBe(false);
    expect(hasExactResumeDownloadPath({ ...base, resumeDownloadUrl: '/api/resumes/resume-a/download' })).toBe(false);
  });
});
