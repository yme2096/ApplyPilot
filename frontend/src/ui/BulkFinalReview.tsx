import { useEffect, useState } from 'react';
import { CheckCircle2, Plus, Trash2 } from 'lucide-react';
import type { ApplicationBundle } from './ApplicationBundles';
import type { BulkReviewPatch } from './bulkPreparation';

type Draft = Omit<BulkReviewPatch, 'status'> & { approve: boolean };

export default function BulkFinalReview({
  bundles,
  onSave,
  onCancel
}: {
  bundles: ApplicationBundle[];
  onSave: (patches: BulkReviewPatch[]) => Promise<{ saved: string[]; failed: string[] }>;
  onCancel: (ids: string[]) => Promise<void>;
}) {
  const [drafts, setDrafts] = useState<Record<string, Draft>>(() => Object.fromEntries(bundles.map((bundle) => [bundle.id, {
    id: bundle.id,
    applicationAnswers: (bundle.applicationAnswers ?? []).map((answer) => ({ ...answer })),
    coverLetterText: bundle.coverLetterText || '',
    notes: bundle.notes || '',
    resumeId: bundle.resumeId || '',
    resumeVersionNumber: bundle.resumeVersionNumber || 0,
    approve: false
  }])));
  const [savedIds, setSavedIds] = useState<string[]>([]);
  const [message, setMessage] = useState('Review each job separately. Changes stay drafts until you approve the selected resume version and save the review.');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setDrafts((current) => Object.fromEntries(bundles.map((bundle) => [bundle.id, current[bundle.id] ?? {
      id: bundle.id,
      applicationAnswers: (bundle.applicationAnswers ?? []).map((answer) => ({ ...answer })),
      coverLetterText: bundle.coverLetterText || '',
      notes: bundle.notes || '',
      resumeId: bundle.resumeId || '',
      resumeVersionNumber: bundle.resumeVersionNumber || 0,
      approve: false
    }])));
    const bundleIds = new Set(bundles.map((bundle) => bundle.id));
    setSavedIds((current) => current.filter((id) => bundleIds.has(id)));
  }, [bundles]);

  const update = (id: string, changes: Partial<Draft>) => setDrafts((current) => ({ ...current, [id]: { ...current[id], ...changes } }));
  const pending = bundles.filter((bundle) => !savedIds.includes(bundle.id));
  const readyToSave = pending.length > 0 && pending.every((bundle) => {
    const draft = drafts[bundle.id];
    return Boolean(draft && draft.approve && draft.resumeId && draft.resumeVersionNumber > 0 && draft.applicationAnswers.every((answer) => answer.question.trim() && answer.answer.trim()));
  });

  const save = async () => {
    const patches = pending.filter((bundle) => drafts[bundle.id].approve).map((bundle) => ({ ...drafts[bundle.id], status: 'READY' as const }));
    if (!patches.length) return;
    setBusy(true);
    const result = await onSave(patches);
    setSavedIds((current) => [...current, ...result.saved]);
    setMessage(result.failed.length
      ? `${result.saved.length} saved as READY; ${result.failed.length} need retry. Successful jobs will not be recreated.`
      : 'All reviewed jobs are READY. You can still edit them individually before use.');
    setBusy(false);
  };

  if (!bundles.length) return null;
  return <section style={{ marginTop: 18, padding: 16, border: '1px solid #bfdbfe', borderRadius: 8, background: '#eff6ff' }}>
    <div className="eyebrow">CONSOLIDATED FINAL REVIEW</div>
    <h3 style={{ margin: '4px 0' }}>Review {bundles.length} separate job preparations</h3>
    <p style={{ fontSize: 12, color: '#475569' }}>{message}</p>
    <div style={{ display: 'grid', gap: 12 }}>
      {bundles.map((bundle) => {
        const draft = drafts[bundle.id] ?? {
          id: bundle.id,
          applicationAnswers: (bundle.applicationAnswers ?? []).map((answer) => ({ ...answer })),
          coverLetterText: bundle.coverLetterText || '',
          notes: bundle.notes || '',
          resumeId: bundle.resumeId || '',
          resumeVersionNumber: bundle.resumeVersionNumber || 0,
          approve: false
        };
        const saved = savedIds.includes(bundle.id);
        return <article key={bundle.id} style={{ background: '#fff', border: '1px solid #dbeafe', borderRadius: 7, padding: 12 }}>
          <strong>{bundle.jobTitle}</strong><span style={{ color: '#64748b' }}> · {bundle.company || 'Company unavailable'}</span>
          <div style={{ fontSize: 11, color: '#475569', margin: '4px 0 8px' }}>Resume {bundle.resumeVersionNumber ? `v${draft.resumeVersionNumber}` : 'missing'} · Status {saved ? 'READY' : 'DRAFT'}</div>
          <label className="review-label">Cover letter / application note
            <textarea rows={3} value={draft.coverLetterText} disabled={saved} onChange={(event) => update(bundle.id, { coverLetterText: event.target.value })} />
          </label>
          <label className="review-label">Preparation notes
            <input value={draft.notes} disabled={saved} onChange={(event) => update(bundle.id, { notes: event.target.value })} />
          </label>
          <div style={{ marginTop: 8, fontWeight: 600, fontSize: 12 }}>Application answers for this job</div>
          {draft.applicationAnswers.map((answer, index) => <div key={`${bundle.id}-${index}`} style={{ display: 'grid', gap: 5, marginTop: 6 }}>
            <input aria-label={`${bundle.jobTitle} answer question ${index + 1}`} placeholder="Question" value={answer.question} disabled={saved} onChange={(event) => update(bundle.id, { applicationAnswers: draft.applicationAnswers.map((row, rowIndex) => rowIndex === index ? { ...row, question: event.target.value } : row) })} />
            <textarea aria-label={`${bundle.jobTitle} answer ${index + 1}`} placeholder="Candidate-reviewed answer" value={answer.answer} disabled={saved} onChange={(event) => update(bundle.id, { applicationAnswers: draft.applicationAnswers.map((row, rowIndex) => rowIndex === index ? { ...row, answer: event.target.value } : row) })} />
            {!saved && <button type="button" className="button outline" onClick={() => update(bundle.id, { applicationAnswers: draft.applicationAnswers.filter((_row, rowIndex) => rowIndex !== index) })}><Trash2 size={13} /> Remove answer</button>}
          </div>)}
          {!saved && <button type="button" className="button outline" style={{ marginTop: 7 }} onClick={() => update(bundle.id, { applicationAnswers: [...draft.applicationAnswers, { question: '', answer: '', category: 'General' }] })}><Plus size={13} /> Add answer for this job</button>}
          {!saved && <label className="review-check" style={{ marginTop: 8 }}><input type="checkbox" checked={draft.approve} onChange={(event) => update(bundle.id, { approve: event.target.checked })} />I reviewed and approve resume v{draft.resumeVersionNumber} and the answers for {bundle.jobTitle}. This only marks the bundle READY; it does not submit an application.</label>}
          {saved && <div style={{ color: '#15803d', fontSize: 12 }}><CheckCircle2 size={14} /> Reviewed and saved as READY</div>}
        </article>;
      })}
    </div>
    <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
      {pending.length > 0 && <button type="button" className="button dark" disabled={!readyToSave || busy} onClick={() => void save()}>{busy ? 'Saving review…' : `Approve ${pending.length} reviewed preparations`}</button>}
      <button type="button" className="button outline" disabled={busy} onClick={() => void onCancel(pending.map((bundle) => bundle.id))}>Cancel remaining drafts</button>
    </div>
  </section>;
}
