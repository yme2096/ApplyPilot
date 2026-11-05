import { describe, expect, it } from 'vitest';
import { applicationStatuses, percent, requireAppliedConfirmation, untrackedJobs } from './applicationTracking';

describe('application tracking dashboard policy', () => {
  it('exposes supported statuses and requires confirmation for APPLIED', () => {
    expect(applicationStatuses).toEqual(['SAVED','PREPARING','READY_FOR_REVIEW','APPLIED','SHORTLISTED','INTERVIEW','REJECTED','OFFER','WITHDRAWN']);
    expect(requireAppliedConfirmation('APPLIED', false)).toBe(false);
    expect(requireAppliedConfirmation('APPLIED', true)).toBe(true);
    expect(requireAppliedConfirmation('INTERVIEW', false)).toBe(true);
  });
  it('formats finite metrics and suppresses already tracked jobs', () => {
    expect(percent(12.345)).toBe('12.3%');
    expect(percent(undefined)).toBe('0.0%');
    expect(untrackedJobs([{id:'a'},{id:'b'}],new Set(['a']))).toEqual([{id:'b'}]);
  });
});
