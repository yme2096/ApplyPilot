export type BulkJobChoice = { jobId: string; resumeVersionNumber: number | '' };

export function planBulkJobIds(selectedJobIds: string[], existingJobIds: string[]): string[] {
  const existing = new Set(existingJobIds);
  return [...new Set(selectedJobIds)].filter((jobId) => !existing.has(jobId)).slice(0, 25);
}

export function bulkCreateItems(jobIds: string[], resumeId: string, versions: Record<string, number | ''>) {
  if (!resumeId) return [];
  return jobIds.flatMap((jobId) => {
    const version = versions[jobId];
    return Number.isInteger(version) && Number(version) > 0
      ? [{ jobId, resumeId, resumeVersionNumber: Number(version), applicationAnswers: [] as Array<{ question: string; answer: string; category: string }> }]
      : [];
  });
}

export type BulkReviewPatch = {
  id: string;
  applicationAnswers: Array<{ question: string; answer: string; category?: string }>;
  coverLetterText: string;
  notes: string;
  resumeId: string;
  resumeVersionNumber: number;
  status: 'READY';
};

export async function saveBulkReview(
  patches: BulkReviewPatch[],
  saveOne: (patch: BulkReviewPatch) => Promise<unknown>
): Promise<{ saved: string[]; failed: string[] }> {
  const result: { saved: string[]; failed: string[] } = { saved: [], failed: [] };
  for (const patch of patches) {
    try {
      await saveOne(patch);
      result.saved.push(patch.id);
    } catch {
      result.failed.push(patch.id);
    }
  }
  return result;
}
