import { useEffect, useState } from 'react';
import { api } from '../api';
import {
  FolderArchive,
  Plus,
  FileText,
  ExternalLink,
  CheckCircle2,
  Clock,
  Trash2,
  ChevronDown,
  ChevronUp,
  Download,
  LoaderCircle,
  HelpCircle,
  AlertCircle
} from 'lucide-react';
import BulkFinalReview from './BulkFinalReview';
import { bulkCreateItems, planBulkJobIds, saveBulkReview, type BulkReviewPatch } from './bulkPreparation';

export type ApplicationBundle = {
  id: string;
  preparationBatchId?: string | null;
  jobId: string;
  jobTitle: string;
  company: string;
  jobUrl: string | null;
  resumeId: string | null;
  resumeVersionNumber: number | null;
  resumeDownloadUrl: string | null;
  status: 'DRAFT' | 'READY' | 'IN_REVIEW' | 'SUBMITTED_BY_CANDIDATE';
  applicationAnswers: Array<{ question: string; answer: string; category?: string }>;
  requiredDocuments: Array<{ docType: string; filename: string; storageKey?: string }>;
  certificationsSnapshot?: Array<{ id: string; name: string; issuer?: string | null; issueDate?: string | null; expirationDate?: string | null; credentialId?: string | null; linksCandidateProvided: boolean }>;
  certificationsApproved?: boolean;
  coverLetterText: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

type SavedJob = {
  id: string;
  title: string;
  company: string | null;
  jobUrl: string | null;
};

type Resume = {
  id: string;
  filename: string;
};
type CertificationOption={id:string;name:string;issuer:string|null;expirationStatus:string};

export type ResumeVersion = {
  version: number;
  sha256: string;
  structuredData?: Record<string, unknown>;
};

export default function ApplicationBundles() {
  const [bundles, setBundles] = useState<ApplicationBundle[]>([]);
  const [jobs, setJobs] = useState<SavedJob[]>([]);
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [resumeVersions, setResumeVersions] = useState<ResumeVersion[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState<string>('');

  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  // Create form state
  const [formJobId, setFormJobId] = useState('');
  const [formResumeId, setFormResumeId] = useState('');
  const [formVersion, setFormVersion] = useState<number | ''>('');
  const [formCoverLetter, setFormCoverLetter] = useState('');
  const [formNotes, setFormNotes] = useState('');
  const [bulkSelectedJobs, setBulkSelectedJobs] = useState<string[]>([]);
  const [bulkResumeId, setBulkResumeId] = useState('');
  const [bulkVersions, setBulkVersions] = useState<ResumeVersion[]>([]);
  const [bulkVersionByJob, setBulkVersionByJob] = useState<Record<string, number | ''>>({});
  const [bulkReviewBundles, setBulkReviewBundles] = useState<ApplicationBundle[]>([]);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [trackedApplicationJobs, setTrackedApplicationJobs] = useState<Set<string>>(new Set());
  const [certifications,setCertifications]=useState<CertificationOption[]>([]);
  const [selectedCertifications,setSelectedCertifications]=useState<string[]>([]);

  // Add screening answer state
  const [ansQuestion, setAnsQuestion] = useState('');
  const [ansAnswer, setAnsAnswer] = useState('');
  const [ansCategory, setAnsCategory] = useState('Technical');

  const loadData = async () => {
    try {
      const [bRes, jRes, rRes, aRes] = await Promise.all([
        api.get('/bundles'),
        api.get('/jobs'),
        api.get('/resumes'),
        api.get('/applications').catch(() => ({ data: [] }))
      ]);
      const cRes=await api.get('/certifications').catch(()=>({data:[]}));
      setBundles(bRes.data);
      setBulkReviewBundles(bRes.data.filter((bundle: ApplicationBundle) => bundle.preparationBatchId && bundle.status === 'DRAFT'));
      setJobs(jRes.data);
      setResumes(rRes.data);
      setCertifications(cRes.data);
      setTrackedApplicationJobs(new Set(aRes.data.map((a: {job_id:string}) => a.job_id)));
      if (rRes.data.length > 0 && !selectedResumeId) {
        setSelectedResumeId(rRes.data[0].id);
      }
    } catch {
      setMessage('Could not load application bundles.');
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  useEffect(() => {
    if (formResumeId) {
      void api.get(`/resumes/${formResumeId}/versions`).then((res) => {
        setResumeVersions(res.data);
        if (res.data.length > 0) {
          setFormVersion(res.data[0].version);
        }
      }).catch(() => {});
    }
  }, [formResumeId]);

  useEffect(() => {
    if (!bulkResumeId) { setBulkVersions([]); return; }
    void api.get(`/resumes/${bulkResumeId}/versions`).then((res) => setBulkVersions(res.data)).catch(() => setMessage('Could not load resume versions for bulk preparation.'));
  }, [bulkResumeId]);

  const eligibleBulkJobs = jobs.filter((job) => !bundles.some((bundle) => bundle.jobId === job.id));

  const toggleBulkJob = (jobId: string) => setBulkSelectedJobs((current) => current.includes(jobId) ? current.filter((id) => id !== jobId) : planBulkJobIds([...current, jobId], bundles.map((bundle) => bundle.jobId)));

  const handleCreateBulk = async () => {
    const safeJobIds = planBulkJobIds(bulkSelectedJobs, bundles.map((bundle) => bundle.jobId));
    const items = bulkCreateItems(safeJobIds, bulkResumeId, bulkVersionByJob).map(item=>({...item,certificationIds:selectedCertifications}));
    if (!items.length || items.length !== safeJobIds.length) { setMessage('Choose one exact resume version for each selected job before preparing.'); return; }
    setBulkBusy(true);
    setMessage('');
    try {
      const response = await api.post('/bundles/bulk', { items });
      const created = response.data as ApplicationBundle[];
      setBundles((current) => [...created, ...current]);
      setBulkReviewBundles(created);
      setBulkSelectedJobs([]);
      setBulkVersionByJob({});
      setMessage(`${created.length} individual draft preparations created. Review and approve them below before using them.`);
    } catch (err: any) {
      setMessage(err.response?.data?.message ?? 'Bulk preparation failed. No application was submitted; refresh existing bundles before retrying.');
      await loadData();
    } finally { setBulkBusy(false); }
  };

  const handleApproveBulkReview = async (patches: BulkReviewPatch[]) => {
    const result = await saveBulkReview(patches, async (patch) => {
      await api.patch(`/bundles/${patch.id}`, {
        applicationAnswers: patch.applicationAnswers,
        coverLetterText: patch.coverLetterText || null,
        notes: patch.notes || null,
        resumeId: patch.resumeId,
        resumeVersionNumber: patch.resumeVersionNumber,
        resumeVersionApproved: true,
        status: 'READY'
        ,certificationsApproved: true
      });
    });
    const saved = new Set(result.saved);
    setBundles((current) => current.map((bundle) => saved.has(bundle.id) ? { ...bundle, status: 'READY' } : bundle));
    if (result.failed.length === 0) setBulkReviewBundles([]);
    else setBulkReviewBundles((current) => current.filter((bundle) => result.failed.includes(bundle.id)));
    return result;
  };

  const handleCancelBulk = async (ids: string[]) => {
    if (!ids.length) { setBulkReviewBundles([]); return; }
    if (!confirm('Cancel these unapproved draft preparations? Saved jobs and resume versions will remain untouched.')) return;
    const results = await Promise.allSettled(ids.map((id) => api.delete(`/bundles/${id}`)));
    const cancelled = new Set(ids.filter((_id, index) => results[index].status === 'fulfilled'));
    setBundles((current) => current.filter((bundle) => !cancelled.has(bundle.id)));
    setBulkReviewBundles((current) => current.filter((bundle) => !cancelled.has(bundle.id)));
    setMessage(cancelled.size === ids.length ? 'Unapproved drafts cancelled. Saved jobs and resumes were preserved.' : 'Some drafts could not be cancelled; retry or remove those drafts individually.');
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formJobId) {
      setMessage('Please select a target job.');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      await api.post('/bundles', {
        jobId: formJobId,
        resumeId: formResumeId || null,
        resumeVersionNumber: formVersion !== '' ? formVersion : null,
        coverLetterText: formCoverLetter.trim() || null,
        notes: formNotes.trim() || null,
        applicationAnswers: []
        ,certificationIds: selectedCertifications
      });
      setShowCreate(false);
      setFormJobId('');
      setFormResumeId('');
      setFormVersion('');
      setFormCoverLetter('');
      setFormNotes('');
      await loadData();
      setMessage('Application bundle created.');
    } catch (err: any) {
      setMessage(err.response?.data?.message ?? 'Could not create bundle.');
    } finally {
      setBusy(false);
    }
  };

  const handleUpdateStatus = async (bundleId: string, newStatus: string) => {
    const target=bundles.find(bundle=>bundle.id===bundleId);
    if(target&&['READY','SUBMITTED_BY_CANDIDATE'].includes(newStatus)&&(target.certificationsSnapshot?.length??0)>0&&!target.certificationsApproved){setMessage('Review and approve the selected certification snapshot in this bundle before moving it forward.');return;}
    if(newStatus==='SUBMITTED_BY_CANDIDATE'&&!window.confirm('Confirm that you personally submitted this application outside ApplyPilot?'))return;
    try {
      await api.patch(`/bundles/${bundleId}`, { status: newStatus });
      await loadData();
    } catch {
      setMessage('Could not update status.');
    }
  };

  const approveCertifications=async(bundle:ApplicationBundle)=>{try{await api.patch(`/bundles/${bundle.id}`,{certificationsApproved:true});await loadData();setMessage('Selected certification metadata approved for this preparation. No certificate file was attached.');}catch(err:any){setMessage(err.response?.data?.message??'Could not approve certification snapshot.');}};

  const handleAddAnswer = async (bundleId: string) => {
    if (!ansQuestion.trim() || !ansAnswer.trim()) return;
    try {
      await api.post(`/bundles/${bundleId}/answers`, {
        question: ansQuestion.trim(),
        answer: ansAnswer.trim(),
        category: ansCategory
      });
      setAnsQuestion('');
      setAnsAnswer('');
      await loadData();
      setMessage('Screening answer saved.');
    } catch {
      setMessage('Could not save screening answer.');
    }
  };

  const handleDelete = async (bundleId: string) => {
    if (!confirm('Remove this application bundle? The underlying saved job and resume files remain untouched.')) return;
    try {
      await api.delete(`/bundles/${bundleId}`);
      await loadData();
      setMessage('Application bundle removed.');
    } catch {
      setMessage('Could not delete bundle.');
    }
  };

  const trackApplication = async (bundle: ApplicationBundle) => {
    try {
      await api.post('/applications', { jobId: bundle.jobId, bundleId: bundle.id, status: 'PREPARING' });
      setMessage('Application tracking started with this exact resume and answer snapshot.');
      setTrackedApplicationJobs((current) => new Set(current).add(bundle.jobId));
    } catch (err: any) {
      setMessage(err.response?.data?.message ?? 'Could not start application tracking.');
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'SUBMITTED_BY_CANDIDATE':
        return { label: 'Submitted by Candidate', bg: '#dcfce7', color: '#15803d' };
      case 'READY':
        return { label: 'Ready for Application', bg: '#e0e7ff', color: '#4338ca' };
      case 'IN_REVIEW':
        return { label: 'In Review', bg: '#fef9c3', color: '#a16207' };
      default:
        return { label: 'Drafting', bg: '#f1f5f9', color: '#475569' };
    }
  };

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <div className="eyebrow">APPLICATION PREPARATION</div>
          <h2>Application Bundles</h2>
          <p>Package job-specific tailored resumes, screening answers, and documents for candidate-controlled submission.</p>
        </div>
        <button className="button dark" onClick={() => setShowCreate(!showCreate)}>
          <Plus size={16} /> {showCreate ? 'Close Form' : 'Prepare New Bundle'}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '16px', marginTop: '16px' }}>
          <div style={{ fontWeight: 700, fontSize: '13px', color: '#1e293b', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <FolderArchive size={16} /> Create Job Application Bundle
          </div>

          <div className="fields" style={{ margin: '10px 0' }}>
            <label>
              Target Job Opportunity *
              <select required value={formJobId} onChange={(e) => setFormJobId(e.target.value)}>
                <option value="">-- Select Saved Job --</option>
                {jobs.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.title} {j.company ? `(${j.company})` : ''}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Tailored Resume Document
              <select value={formResumeId} onChange={(e) => setFormResumeId(e.target.value)}>
                <option value="">-- Optional: Attach Resume --</option>
                {resumes.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.filename}
                  </option>
                ))}
              </select>
            </label>

            {formResumeId && resumeVersions.length > 0 && (
              <label>
                Resume Version
                <select value={formVersion} onChange={(e) => setFormVersion(Number(e.target.value))}>
                  {resumeVersions.map((v) => (
                    <option key={v.version} value={v.version}>
                      Version {v.version} {v.structuredData?.tailored ? `(Tailored: ${String(v.structuredData.targetRole ?? '')})` : ''}
                    </option>
                  ))}
                </select>
              </label>
            )}

            <label>
              Preparation Notes / Checklist
              <input
                type="text"
                placeholder="e.g. Include GitHub link in cover note, tailored for senior scope"
                value={formNotes}
                onChange={(e) => setFormNotes(e.target.value)}
              />
            </label>
          </div>

          {certifications.length>0&&<fieldset className="bundle-cert-select"><legend>Certifications to include in the generated resume metadata snapshot</legend><p>Selection copies metadata and evidence references only. Certificate PDFs are never attached to employer forms by ApplyPilot.</p>{certifications.map(cert=><label key={cert.id}><input type="checkbox" checked={selectedCertifications.includes(cert.id)} onChange={e=>setSelectedCertifications(current=>e.target.checked?[...current,cert.id]:current.filter(id=>id!==cert.id))}/><span>{cert.name}{cert.issuer?` · ${cert.issuer}`:''}</span></label>)}</fieldset>}

          <label className="review-label" style={{ margin: '8px 0' }}>
            Cover Letter / Application Note Draft
            <textarea
              rows={4}
              placeholder="Draft role-specific introduction or note for employer..."
              value={formCoverLetter}
              onChange={(e) => setFormCoverLetter(e.target.value)}
            />
          </label>

          <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
            <button className="button dark" type="submit" disabled={busy || !formJobId}>
              {busy ? <LoaderCircle size={15} className="spin" /> : <Plus size={15} />} Create Bundle
            </button>
            <button type="button" className="button outline" onClick={() => setShowCreate(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      <section style={{ marginTop: 16, padding: 16, background: '#f8fafc', border: '1px solid #cbd5e1', borderRadius: 8 }}>
        <div className="eyebrow">MULTI-JOB PREPARATION</div>
        <h3 style={{ margin: '4px 0' }}>Prepare several separate drafts</h3>
        <p style={{ fontSize: 12, color: '#64748b' }}>Choose saved jobs and an exact resume version for each one. Each gets its own draft answers and final review. Existing job preparations are excluded to prevent duplicates.</p>
        <label>Resume document for this batch
          <select value={bulkResumeId} onChange={(event) => { setBulkResumeId(event.target.value); setBulkVersionByJob({}); }}>
            <option value="">Choose a resume document</option>
            {resumes.map((resume) => <option key={resume.id} value={resume.id}>{resume.filename}</option>)}
          </select>
        </label>
        {certifications.length>0&&<fieldset className="bundle-cert-select"><legend>Certification metadata for each selected job</legend><p>Each draft receives a separate immutable metadata snapshot. Candidates approve it per job in final review.</p>{certifications.map(cert=><label key={cert.id}><input type="checkbox" checked={selectedCertifications.includes(cert.id)} onChange={e=>setSelectedCertifications(current=>e.target.checked?[...current,cert.id]:current.filter(id=>id!==cert.id))}/><span>{cert.name}{cert.issuer?` · ${cert.issuer}`:''}</span></label>)}</fieldset>}
        {eligibleBulkJobs.length === 0 ? <div className="records-empty">All saved jobs already have a preparation, or there are no saved jobs.</div> : <div style={{ display: 'grid', gap: 7, margin: '12px 0' }}>
          {eligibleBulkJobs.map((job) => <div key={job.id} style={{ display: 'grid', gridTemplateColumns: 'minmax(180px,1fr) minmax(150px,220px)', alignItems: 'center', gap: 10, padding: 8, background: '#fff', border: '1px solid #e2e8f0', borderRadius: 6 }}>
            <label style={{ display: 'flex', gap: 7, alignItems: 'center' }}><input type="checkbox" checked={bulkSelectedJobs.includes(job.id)} onChange={() => toggleBulkJob(job.id)} />{job.title}{job.company ? ` · ${job.company}` : ''}</label>
            <select aria-label={`Resume version for ${job.title}`} disabled={!bulkSelectedJobs.includes(job.id) || !bulkResumeId} value={bulkVersionByJob[job.id] ?? ''} onChange={(event) => setBulkVersionByJob((current) => ({ ...current, [job.id]: event.target.value ? Number(event.target.value) : '' }))}>
              <option value="">Choose exact version</option>
              {bulkVersions.map((version) => <option key={version.version} value={version.version}>Version {version.version}{version.structuredData?.tailored ? ` · ${String(version.structuredData.targetRole || 'Tailored')}` : ''}</option>)}
            </select>
          </div>)}
        </div>}
        <button type="button" className="button dark" disabled={bulkBusy || !bulkSelectedJobs.length || !bulkResumeId} onClick={() => void handleCreateBulk()}>
          {bulkBusy ? <LoaderCircle size={15} className="spin" /> : <FolderArchive size={15} />} Create {bulkSelectedJobs.length || ''} draft preparations
        </button>
      </section>

      <BulkFinalReview bundles={bulkReviewBundles} onSave={handleApproveBulkReview} onCancel={handleCancelBulk} />

      {message && (
        <div className="notice" style={{ margin: '14px 0' }}>
          {message}
          <button onClick={() => setMessage('')}>×</button>
        </div>
      )}

      {/* Notice on submission integrity */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '6px', padding: '10px 12px', margin: '16px 0', fontSize: '11px', color: '#475569' }}>
        <AlertCircle size={15} style={{ color: '#3b82f6', flexShrink: 0 }} />
        <span>
          <strong>Candidate-Controlled Submission:</strong> Bundles help you organize tailored documents and screening answers. You retain full control over when and how you submit on the employer portal.
        </span>
      </div>

      {/* Bundles List */}
      <div style={{ display: 'grid', gap: '12px', marginTop: '14px' }}>
        {bundles.length > 0 ? (
          bundles.map((b) => {
            const isExpanded = expandedId === b.id;
            const badge = getStatusBadge(b.status);

            return (
              <div
                key={b.id}
                style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: '8px',
                  background: '#fff',
                  padding: '16px',
                  transition: '0.15s ease'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px', flexWrap: 'wrap' }}>
                  <div style={{ flex: 1, minWidth: '220px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                      <strong style={{ fontSize: '14px', color: '#0f172a' }}>{b.jobTitle}</strong>
                      <span
                        style={{
                          fontSize: '10px',
                          fontWeight: 700,
                          padding: '2px 8px',
                          borderRadius: '12px',
                          background: badge.bg,
                          color: badge.color
                        }}
                      >
                        {badge.label}
                      </span>
                    </div>

                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '4px' }}>
                      {b.company && <span>{b.company} · </span>}
                      {b.resumeVersionNumber != null && (
                        <span>Attached: Resume v{b.resumeVersionNumber} · </span>
                      )}
                      <span>Created {new Date(b.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {!trackedApplicationJobs.has(b.jobId) && <button className="button outline" onClick={() => void trackApplication(b)} aria-label={`Track ${b.jobTitle} application`}><Plus size={13}/> Track</button>}
                    <select
                      value={b.status}
                      onChange={(e) => void handleUpdateStatus(b.id, e.target.value)}
                      disabled={Boolean(b.preparationBatchId) && b.status === 'DRAFT'}
                      title={b.preparationBatchId && b.status === 'DRAFT' ? 'Review and approve this job in the consolidated batch review above.' : undefined}
                      style={{
                        height: '28px',
                        fontSize: '11px',
                        borderRadius: '6px',
                        border: '1px solid #cbd5e1',
                        padding: '0 6px',
                        background: '#f8fafc',
                        color: '#334155',
                        fontWeight: 600
                      }}
                    >
                      <option value="DRAFT">DRAFT</option>
                      <option value="READY">READY</option>
                      <option value="IN_REVIEW">IN_REVIEW</option>
                      <option value="SUBMITTED_BY_CANDIDATE">SUBMITTED (By Candidate)</option>
                    </select>

                    {b.jobUrl && (
                      <a
                        href={b.jobUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="iconbutton"
                        style={{ width: '28px', height: '28px' }}
                        title="Open posting"
                      >
                        <ExternalLink size={13} />
                      </a>
                    )}

                    <button
                      className="iconbutton"
                      style={{ width: '28px', height: '28px' }}
                      onClick={() => setExpandedId(isExpanded ? null : b.id)}
                      title="Expand details"
                    >
                      {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </button>

                    <button
                      className="iconbutton"
                      style={{ width: '28px', height: '28px', color: '#ef4444' }}
                      onClick={() => void handleDelete(b.id)}
                      title="Delete bundle"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                </div>

                {isExpanded && (
                  <div style={{ marginTop: '14px', borderTop: '1px solid #f1f5f9', paddingTop: '12px' }}>
                    {b.notes && (
                      <div style={{ fontSize: '11px', color: '#334155', marginBottom: '10px' }}>
                        <strong>Checklist & Notes:</strong> {b.notes}
                      </div>
                    )}

                    {(b.certificationsSnapshot?.length??0)>0&&<section className="bundle-cert-snapshot" aria-label="Certification metadata snapshot"><b>Selected certification metadata ({b.certificationsSnapshot?.length})</b><p>Metadata only. No certificate PDF is attached. Links remain candidate-provided.</p><ul>{b.certificationsSnapshot?.map(cert=><li key={cert.id}>{cert.name}{cert.issuer?` · ${cert.issuer}`:''}{cert.expirationDate?` · Expires ${cert.expirationDate}`:''}</li>)}</ul><span>{b.certificationsApproved?'Candidate approved this snapshot.':'Candidate approval required before READY or candidate-submitted status.'}</span>{!b.certificationsApproved&&<button type="button" className="button outline" onClick={()=>void approveCertifications(b)}>I reviewed and approve these certification details</button>}</section>}

                    {b.coverLetterText && (
                      <div style={{ marginBottom: '12px' }}>
                        <div style={{ fontSize: '11px', fontWeight: 600, color: '#475569', marginBottom: '4px' }}>Cover Note Draft:</div>
                        <div style={{ fontSize: '11px', color: '#334155', background: '#f8fafc', padding: '10px', borderRadius: '6px', whiteSpace: 'pre-wrap' }}>
                          {b.coverLetterText}
                        </div>
                      </div>
                    )}

                    {/* Screening Answers Section */}
                    <div style={{ marginTop: '12px' }}>
                      <div style={{ fontSize: '12px', fontWeight: 700, color: '#1e293b', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <HelpCircle size={14} /> Screening Questions & Prepared Answers ({b.applicationAnswers.length})
                      </div>

                      {b.applicationAnswers.length > 0 && (
                        <div style={{ display: 'grid', gap: '6px', marginBottom: '10px' }}>
                          {b.applicationAnswers.map((ans, idx) => (
                            <div key={idx} style={{ background: '#f1f5f9', borderRadius: '6px', padding: '8px 10px', fontSize: '11px' }}>
                              <div style={{ fontWeight: 600, color: '#0f172a' }}>{ans.question}</div>
                              <div style={{ color: '#334155', marginTop: '2px' }}>{ans.answer}</div>
                              {ans.category && (
                                <span style={{ fontSize: '9px', background: '#e2e8f0', color: '#64748b', padding: '1px 5px', borderRadius: '4px', marginTop: '4px', display: 'inline-block' }}>
                                  {ans.category}
                                </span>
                              )}
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Add answer form inline */}
                      <div style={{ background: '#f8fafc', border: '1px dashed #cbd5e1', borderRadius: '6px', padding: '10px', marginTop: '8px' }}>
                        <div style={{ fontSize: '11px', fontWeight: 600, marginBottom: '6px', color: '#475569' }}>Add or Update Screening Answer:</div>
                        <div style={{ display: 'grid', gap: '6px' }}>
                          <input
                            type="text"
                            placeholder="Screening Question (e.g. Years of Python, US Work Auth...)"
                            value={ansQuestion}
                            onChange={(e) => setAnsQuestion(e.target.value)}
                            style={{ height: '30px', fontSize: '11px', border: '1px solid #cbd5e1', borderRadius: '4px', padding: '0 8px' }}
                          />
                          <input
                            type="text"
                            placeholder="Your Verified Answer"
                            value={ansAnswer}
                            onChange={(e) => setAnsAnswer(e.target.value)}
                            style={{ height: '30px', fontSize: '11px', border: '1px solid #cbd5e1', borderRadius: '4px', padding: '0 8px' }}
                          />
                          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                            <select
                              value={ansCategory}
                              onChange={(e) => setAnsCategory(e.target.value)}
                              style={{ height: '28px', fontSize: '11px', borderRadius: '4px', border: '1px solid #cbd5e1', padding: '0 6px' }}
                            >
                              <option value="Technical">Technical</option>
                              <option value="Eligibility">Work Authorization</option>
                              <option value="Experience">Experience</option>
                              <option value="Logistics">Notice / Salary / Location</option>
                            </select>
                            <button
                              type="button"
                              className="button dark"
                              style={{ minHeight: '28px', fontSize: '10px', padding: '0 10px' }}
                              onClick={() => void handleAddAnswer(b.id)}
                              disabled={!ansQuestion.trim() || !ansAnswer.trim()}
                            >
                              <Plus size={12} /> Save Answer
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        ) : (
          <div className="records-empty">
            No application bundles prepared yet. Select a saved opportunity and click &quot;Prepare New Bundle&quot; to assemble your tailored documents.
          </div>
        )}
      </div>
    </div>
  );
}
