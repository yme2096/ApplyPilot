import type { ApplicationBundleData } from '../types';

export function selectableBundles(bundles: ApplicationBundleData[]): ApplicationBundleData[] {
  const submittedJobIds = new Set(bundles
    .filter((bundle) => bundle.status === 'SUBMITTED_BY_CANDIDATE')
    .map((bundle) => bundle.jobId));
  const seenJobIds = new Set<string>();
  return bundles.filter((bundle) => {
    if (bundle.status !== 'READY' || submittedJobIds.has(bundle.jobId) || seenJobIds.has(bundle.jobId)) return false;
    seenJobIds.add(bundle.jobId);
    return true;
  });
}

export function matchesBundleResume(bundle: ApplicationBundleData, file: { bundleId?: string; resumeId?: string; version?: number; success?: boolean }): boolean {
  return file.success === true && bundle.status === 'READY' && Boolean(bundle.resumeId) && Number.isInteger(bundle.resumeVersionNumber)
    && file.bundleId === bundle.id && file.resumeId === bundle.resumeId && file.version === bundle.resumeVersionNumber;
}
