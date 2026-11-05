import type { ApplicationBundleData } from './types';

export function expectedResumeDownloadPath(bundle: ApplicationBundleData): string | null {
  if (!bundle.resumeId || !Number.isInteger(bundle.resumeVersionNumber) || Number(bundle.resumeVersionNumber) < 1) return null;
  return `/api/resumes/${bundle.resumeId}/versions/${bundle.resumeVersionNumber}/download`;
}

export function hasExactResumeDownloadPath(bundle: ApplicationBundleData): boolean {
  const expected = expectedResumeDownloadPath(bundle);
  return expected !== null && bundle.resumeDownloadUrl === expected;
}
