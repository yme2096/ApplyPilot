export type JobImportType = 'url' | 'greenhouse' | 'lever' | 'rss';
export type EligibilityStatus = 'ELIGIBLE' | 'NOT_ELIGIBLE' | 'UNKNOWN';

export function jobImportEndpoint(type: JobImportType): string {
  switch (type) {
    case 'greenhouse': return '/jobs/import/greenhouse';
    case 'lever': return '/jobs/import/lever';
    case 'rss': return '/jobs/import/rss';
    default: return '/jobs/import/url';
  }
}

export function matchSummary(score: number, matched: string[], missing: string[]): string {
  return `Explainable Criteria Alignment (${score}%) · ${matched.length} matched · ${missing.length} need review`;
}

export function eligibilityLabel(status: EligibilityStatus): string {
  return status === 'UNKNOWN' ? 'Eligibility: UNKNOWN · candidate review needed' : `Eligibility: ${status.replace('_', ' ')}`;
}

export function aiDraftFallbackMessage(error: unknown): string {
  const message = (error as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
  return typeof message === 'string' && message.trim()
    ? message
    : 'Local AI is unavailable. You can write your answer yourself.';
}
