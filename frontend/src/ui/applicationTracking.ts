export const applicationStatuses = ['SAVED','PREPARING','READY_FOR_REVIEW','APPLIED','SHORTLISTED','INTERVIEW','REJECTED','OFFER','WITHDRAWN'] as const;
export type ApplicationStatus = typeof applicationStatuses[number];
export function requireAppliedConfirmation(status: ApplicationStatus, confirmed: boolean): boolean {
  return status !== 'APPLIED' || confirmed;
}
export function percent(value: unknown): string {
  const n = typeof value === 'number' && Number.isFinite(value) ? value : 0;
  return `${n.toFixed(1)}%`;
}
export function untrackedJobs<T extends {id:string}>(jobs:T[], trackedJobIds:Set<string>):T[] {
  return jobs.filter(job => !trackedJobIds.has(job.id));
}
