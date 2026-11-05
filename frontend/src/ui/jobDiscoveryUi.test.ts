import { describe, expect, it } from 'vitest';
import { aiDraftFallbackMessage, eligibilityLabel, jobImportEndpoint, matchSummary } from './jobDiscoveryUi';

describe('job discovery UI behavior', () => {
  it('routes each import choice to its supported API endpoint', () => {
    expect(jobImportEndpoint('url')).toBe('/jobs/import/url');
    expect(jobImportEndpoint('greenhouse')).toBe('/jobs/import/greenhouse');
    expect(jobImportEndpoint('lever')).toBe('/jobs/import/lever');
    expect(jobImportEndpoint('rss')).toBe('/jobs/import/rss');
  });

  it('summarizes match criteria without hiding matched or missing requirements', () => {
    expect(matchSummary(75, ['Java', 'SQL'], ['Kubernetes']))
      .toBe('Explainable Criteria Alignment (75%) · 2 matched · 1 need review');
  });

  it('shows unknown eligibility as needing candidate review', () => {
    expect(eligibilityLabel('UNKNOWN')).toBe('Eligibility: UNKNOWN · candidate review needed');
    expect(eligibilityLabel('NOT_ELIGIBLE')).toBe('Eligibility: NOT ELIGIBLE');
  });

  it('keeps AI-disabled and unavailable states actionable', () => {
    expect(aiDraftFallbackMessage({ response: { data: { message: 'Local AI is disabled.' } } }))
      .toBe('Local AI is disabled.');
    expect(aiDraftFallbackMessage(new Error('network down')))
      .toBe('Local AI is unavailable. You can write your answer yourself.');
  });
});
