import { describe, expect, it, vi } from 'vitest';
import { bulkCreateItems, planBulkJobIds, saveBulkReview, type BulkReviewPatch } from './bulkPreparation';

describe('bulk application preparation', () => {
  it('prevents duplicate jobs and duplicate selections from entering a batch', () => {
    expect(planBulkJobIds(['job-a', 'job-b', 'job-a', 'job-c'], ['job-b'])).toEqual(['job-a', 'job-c']);
  });

  it('requires an exact resume version for each separate job item', () => {
    expect(bulkCreateItems(['job-a', 'job-b'], 'resume-a', { 'job-a': 2, 'job-b': '' })).toEqual([
      { jobId: 'job-a', resumeId: 'resume-a', resumeVersionNumber: 2, applicationAnswers: [] }
    ]);
    expect(bulkCreateItems(['job-a'], '', { 'job-a': 2 })).toEqual([]);
  });

  it('reports per-job failure and retries only unresolved review saves', async () => {
    const patches: BulkReviewPatch[] = ['bundle-a', 'bundle-b'].map((id) => ({
      id, applicationAnswers: [], coverLetterText: '', notes: '', resumeId: 'resume-a', resumeVersionNumber: 3, status: 'READY'
    }));
    const save = vi.fn().mockRejectedValueOnce(new Error('temporary error')).mockResolvedValue({});
    const first = await saveBulkReview(patches, save);
    expect(first).toEqual({ saved: ['bundle-b'], failed: ['bundle-a'] });
    const retry = await saveBulkReview(patches.filter((item) => first.failed.includes(item.id)), save);
    expect(retry).toEqual({ saved: ['bundle-a'], failed: [] });
    expect(save).toHaveBeenCalledTimes(3);
  });
});
