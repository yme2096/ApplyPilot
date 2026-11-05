import { useEffect, useState } from 'react';
import { api } from '../api';
import { eligibilityLabel, jobImportEndpoint, matchSummary } from './jobDiscoveryUi';
import {
  BriefcaseBusiness,
  Plus,
  Search,
  ExternalLink,
  Sparkles,
  Trash2,
  CheckCircle2,
  Clock,
  ChevronDown,
  ChevronUp,
  LoaderCircle,
  FileText,
  DownloadCloud,
  Rss,
  FolderArchive,
  Info,
  AlertTriangle
} from 'lucide-react';

export type Job = {
  id: string;
  title: string;
  company: string | null;
  location: string | null;
  jobUrl: string | null;
  description: string | null;
  source: string | null;
  jobType: string | null;
  requiredSkills: string[];
  preferredSkills: string[];
  postedAt: string | null;
  matchScore: number | null;
  notes: string | null;
  status: 'saved' | 'applied' | 'interviewing' | 'offered' | 'rejected' | 'withdrawn';
  savedAt: string;
};

type MatchResult = {
  score: number;
  matchingRequirements: string[];
  missingRequirements: string[];
  partialMatches: string[];
  eligibilityStatus: 'ELIGIBLE' | 'NOT_ELIGIBLE' | 'UNKNOWN';
  eligibilityReasons: string[];
  relevantProjects: string[];
  roleAlignment: string;
  locationAlignment: string;
  disclaimer: string;
};

type QuestionSuggestion = { question: string; category: string; jobRequirement: string | null; aiGenerated: boolean };
type CompanyResearch = { companyName: string; status: string; sourceUrl: string; retrievedAt: string; facts: { label: string; value: string; sourceUrl: string; retrievedAt: string }[] };

export default function SavedJobs({
  onTailorJob,
  onPrepareBundle
}: {
  onTailorJob?: (job: Job) => void;
  onPrepareBundle?: (job: Job) => void;
}) {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [showAddForm, setShowAddForm] = useState(false);
  const [showImportForm, setShowImportForm] = useState(false);
  const [expandedJobId, setExpandedJobId] = useState<string | null>(null);
  const [questionSuggestions, setQuestionSuggestions] = useState<Record<string, QuestionSuggestion[]>>({});
  const [loadingQuestionsFor, setLoadingQuestionsFor] = useState<string | null>(null);

  // Ingestion Mode State
  const [importType, setImportType] = useState<'url' | 'greenhouse' | 'lever' | 'rss' | 'company'>('url');
  const [importInput, setImportInput] = useState('');
  const [importing, setImporting] = useState(false);
  const [companyResearch, setCompanyResearch] = useState<CompanyResearch | null>(null);

  // Manual Job Form State
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [location, setLocation] = useState('');
  const [jobUrl, setJobUrl] = useState('');
  const [jobType, setJobType] = useState('Full-time');
  const [description, setDescription] = useState('');
  const [source, setSource] = useState('Manual Entry');
  const [notes, setNotes] = useState('');

  // Live match analysis state
  const [liveMatch, setLiveMatch] = useState<MatchResult | null>(null);
  const [analyzing, setAnalyzing] = useState(false);

  const loadJobs = async () => {
    try {
      const url = filterStatus === 'all' ? '/jobs' : `/jobs?status=${filterStatus}`;
      const { data } = await api.get(url);
      setJobs(data);
    } catch {
      setMessage('Could not load opportunities.');
    }
  };

  useEffect(() => {
    void loadJobs();
  }, [filterStatus]);

  const handleAnalyze = async () => {
    if (!title.trim()) {
      setMessage('Please enter at least a Job Title to analyze match.');
      return;
    }
    setAnalyzing(true);
    try {
      const { data } = await api.post('/jobs/analyze', {
        title: title.trim(),
        location: location.trim(),
        description: description.trim()
      });
      setLiveMatch(data);
    } catch {
      setMessage('Could not analyze job match.');
    } finally {
      setAnalyzing(false);
    }
  };

  const handleInspectMatch = async (job: Job) => {
    try {
      const { data } = await api.get(`/jobs/${job.id}/match`);
      setLiveMatch(data);
      setExpandedJobId(job.id);
    } catch {
      setMessage('Could not compute match analysis.');
    }
  };

  const handleDraftQuestions = async (job: Job) => {
    setLoadingQuestionsFor(job.id);
    setMessage('');
    try {
      const { data } = await api.get(`/jobs/${job.id}/questions`);
      setQuestionSuggestions((current) => ({ ...current, [job.id]: data }));
    } catch {
      setMessage('Could not prepare role-specific question prompts.');
    } finally {
      setLoadingQuestionsFor(null);
    }
  };

  const handleSaveJob = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setBusy(true);
    setMessage('');
    try {
      await api.post('/jobs', {
        title: title.trim(),
        company: company.trim() || null,
        location: location.trim() || null,
        jobUrl: jobUrl.trim() || null,
        jobType: jobType.trim() || null,
        description: description.trim() || null,
        source: source.trim() || 'Manual Entry',
        notes: notes.trim() || null
      });
      setTitle('');
      setCompany('');
      setLocation('');
      setJobUrl('');
      setDescription('');
      setNotes('');
      setLiveMatch(null);
      setShowAddForm(false);
      await loadJobs();
      setMessage('Opportunity saved with transparent criteria alignment score.');
    } catch (err: any) {
      setMessage(err.response?.data?.message ?? 'Could not save job.');
    } finally {
      setBusy(false);
    }
  };

  const handleImport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!importInput.trim()) return;
    setImporting(true);
    setMessage('');
    try {
      if (importType === 'company') {
        const { data } = await api.post('/jobs/company-research', { companyPageUrl: importInput.trim() });
        setCompanyResearch(data);
        setMessage(data.status === 'UNAVAILABLE' ? 'No company facts were available in the public page metadata.' : 'Public company facts retrieved with source links.');
        return;
      }
      const endpoint = jobImportEndpoint(importType as 'url' | 'greenhouse' | 'lever' | 'rss');

      const { data } = await api.post(endpoint, { urlOrToken: importInput.trim() });
      setImportInput('');
      setShowImportForm(false);
      await loadJobs();
      setMessage(data.message ?? `Import finished: ${data.importedCount} jobs imported.`);
    } catch (err: any) {
      setMessage(err.response?.data?.message ?? 'Ingestion failed. Ensure the URL is accessible and permitted.');
    } finally {
      setImporting(false);
    }
  };

  const updateStatus = async (id: string, newStatus: string) => {
    try {
      await api.patch(`/jobs/${id}`, { status: newStatus });
      await loadJobs();
    } catch {
      setMessage('Could not update status.');
    }
  };

  const deleteJob = async (id: string) => {
    if (!confirm('Are you sure you want to remove this job opportunity?')) return;
    try {
      await api.delete(`/jobs/${id}`);
      await loadJobs();
      setMessage('Opportunity removed.');
    } catch {
      setMessage('Could not delete job.');
    }
  };

  const filtered = jobs.filter((j) => {
    const q = query.toLowerCase();
    const t = j.title.toLowerCase();
    const c = (j.company ?? '').toLowerCase();
    const l = (j.location ?? '').toLowerCase();
    return t.includes(q) || c.includes(q) || l.includes(q);
  });

  const getBadgeColor = (score: number | null) => {
    if (score == null) return '#8c93a0';
    if (score >= 75) return '#2e7d32';
    if (score >= 50) return '#1976d2';
    return '#e65100';
  };

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <div className="eyebrow">JOB DISCOVERY & INGESTION</div>
          <h2>Opportunity Pipeline</h2>
          <p>Import public job postings, evaluate verified skill alignment, and assemble application bundles.</p>
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button className="button outline" onClick={() => { setShowImportForm(!showImportForm); setShowAddForm(false); }}>
            <DownloadCloud size={16} /> {showImportForm ? 'Close Ingest' : 'Import Feed / ATS'}
          </button>
          <button className="button dark" onClick={() => { setShowAddForm(!showAddForm); setShowImportForm(false); }}>
            <Plus size={16} /> {showAddForm ? 'Close Form' : 'Manual Entry'}
          </button>
        </div>
      </div>

      {/* Import Form (Greenhouse, Lever, RSS, URL) */}
      {showImportForm && (
        <form onSubmit={handleImport} style={{ background: '#f0f4ff', border: '1px solid #c7d7fe', borderRadius: '8px', padding: '16px', marginTop: '16px' }}>
          <div style={{ fontWeight: 700, fontSize: '13px', color: '#1e3a8a', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <DownloadCloud size={16} /> Public Job Ingestion (Permissioned Sources)
          </div>
          <p style={{ fontSize: '11px', color: '#475569', marginBottom: '12px' }}>
            Import from Greenhouse, Lever, public RSS feeds, or standard structured pages. We respect site boundaries and do not bypass anti-bot or access restricted LinkedIn pages.
          </p>

          <div style={{ display: 'flex', gap: '6px', marginBottom: '12px', flexWrap: 'wrap' }}>
            <button
              type="button"
              className={`button ${importType === 'url' ? 'dark' : 'outline'}`}
              style={{ minHeight: '26px', fontSize: '10px' }}
              onClick={() => setImportType('url')}
            >
              Public Job URL / JSON-LD
            </button>
            <button
              type="button"
              className={`button ${importType === 'greenhouse' ? 'dark' : 'outline'}`}
              style={{ minHeight: '26px', fontSize: '10px' }}
              onClick={() => setImportType('greenhouse')}
            >
              Greenhouse Board / URL
            </button>
            <button
              type="button"
              className={`button ${importType === 'lever' ? 'dark' : 'outline'}`}
              style={{ minHeight: '26px', fontSize: '10px' }}
              onClick={() => setImportType('lever')}
            >
              Lever Postings / URL
            </button>
            <button
              type="button"
              className={`button ${importType === 'rss' ? 'dark' : 'outline'}`}
              style={{ minHeight: '26px', fontSize: '10px' }}
              onClick={() => setImportType('rss')}
            >
              <Rss size={12} /> RSS / Atom Job Feed
            </button>
            <button type="button" className={`button ${importType === 'company' ? 'dark' : 'outline'}`} style={{ minHeight: '26px', fontSize: '10px' }} onClick={() => setImportType('company')}>
              Company information
            </button>
          </div>

          <label className="review-label" style={{ margin: '6px 0' }}>
            {importType === 'greenhouse' && 'Greenhouse Board Token or Job Posting URL (e.g. gitlab, figma, or boards.greenhouse.io/...)'}
            {importType === 'lever' && 'Lever Site Name or Job Posting URL (e.g. jobs.lever.co/company/...)'}
            {importType === 'rss' && 'Public RSS/Atom Job Feed URL'}
            {importType === 'company' && 'Public company website URL with Organization metadata'}
            {importType === 'url' && 'Direct Career Page URL with structured JobPosting or OpenGraph data'}
            <input
              type="text"
              required
              placeholder={
                importType === 'greenhouse'
                  ? 'https://boards.greenhouse.io/gitlab/jobs/12345'
                  : importType === 'lever'
                  ? 'https://jobs.lever.co/netflix/12345'
                  : importType === 'rss'
                  ? 'https://weworkremotely.com/categories/remote-programming-jobs.rss'
                  : importType === 'company'
                  ? 'https://example.com/about'
                  : 'https://example.com/careers/software-engineer'
              }
              value={importInput}
              onChange={(e) => setImportInput(e.target.value)}
              style={{ height: '34px', fontSize: '11px', border: '1px solid #93c5fd', borderRadius: '6px', padding: '0 10px' }}
            />
          </label>

          <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
            <button className="button dark" type="submit" disabled={importing || !importInput.trim()}>
              {importing ? <LoaderCircle size={15} className="spin" /> : <DownloadCloud size={15} />} {importType === 'company' ? 'Retrieve public facts' : 'Ingest Opportunities'}
            </button>
            <button type="button" className="button outline" onClick={() => setShowImportForm(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {companyResearch && (
        <div className="notice" style={{ marginTop: '12px' }}>
          <strong>{companyResearch.companyName || 'Company information'} · {companyResearch.status === 'UNAVAILABLE' ? 'Unavailable' : companyResearch.status === 'PARTIAL' ? 'Partial public metadata' : 'Verified from page metadata'}</strong>
          <div style={{ marginTop: '4px' }}>Retrieved {new Date(companyResearch.retrievedAt).toLocaleString()} from <a href={companyResearch.sourceUrl} target="_blank" rel="noreferrer">the supplied public page</a>.</div>
          {companyResearch.facts.length > 0 ? companyResearch.facts.map((fact, index) => (
            <div key={`${fact.label}-${index}`} style={{ marginTop: '6px' }}><b>{fact.label}:</b> {fact.value} <small>(source: <a href={fact.sourceUrl} target="_blank" rel="noreferrer">page</a>)</small></div>
          )) : <div style={{ marginTop: '4px' }}>No company facts were present in the page metadata. Nothing has been inferred.</div>}
        </div>
      )}

      {/* Manual Add Job Form */}
      {showAddForm && (
        <form onSubmit={handleSaveJob} style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '16px', marginTop: '16px' }}>
          <div style={{ fontWeight: 700, fontSize: '13px', color: '#1e293b', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <BriefcaseBusiness size={16} /> Enter Job Opportunity
          </div>

          <div className="fields" style={{ margin: '10px 0' }}>
            <label>
              Job Title *
              <input
                required
                placeholder="e.g. Full Stack Engineer"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </label>
            <label>
              Company / Organization
              <input
                placeholder="e.g. Acme Corp"
                value={company}
                onChange={(e) => setCompany(e.target.value)}
              />
            </label>
            <label>
              Location / Remote
              <input
                placeholder="e.g. Remote, or New York, NY"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
              />
            </label>
            <label>
              Job Type
              <input
                placeholder="e.g. Full-time, Internship, Contract"
                value={jobType}
                onChange={(e) => setJobType(e.target.value)}
              />
            </label>
            <label>
              Job Posting URL
              <input
                type="url"
                placeholder="https://..."
                value={jobUrl}
                onChange={(e) => setJobUrl(e.target.value)}
              />
            </label>
            <label>
              Source
              <input
                placeholder="e.g. LinkedIn, Company Website, Referral"
                value={source}
                onChange={(e) => setSource(e.target.value)}
              />
            </label>
            <label>
              Notes / Recruiter Contact
              <input
                placeholder="e.g. Follow up with recruiter"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>
          </div>

          <label className="review-label" style={{ margin: '8px 0' }}>
            Job Description & Qualifications
            <textarea
              rows={5}
              placeholder="Paste full job description for automated explainable criteria matching..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>

          {liveMatch && (
            <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '8px', padding: '12px', margin: '12px 0', fontSize: '11px', color: '#166534' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontWeight: 700 }}>
                <span>Criteria Alignment Estimate: {liveMatch.score}%</span>
                <span>{liveMatch.roleAlignment} · {liveMatch.locationAlignment}</span>
              </div>
              <div style={{ fontSize: '10px', color: '#475569', margin: '4px 0 8px', fontStyle: 'italic' }}>
                {liveMatch.disclaimer}
              </div>

              {liveMatch.matchingRequirements.length > 0 && (
                <div style={{ marginTop: '6px', display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                  <span style={{ fontWeight: 600 }}>Matching Requirements:</span>
                  {liveMatch.matchingRequirements.map((s, idx) => (
                    <span key={idx} style={{ background: '#dcfce7', padding: '1px 6px', borderRadius: '4px' }}>{s}</span>
                  ))}
                </div>
              )}
              {liveMatch.missingRequirements.length > 0 && (
                <div style={{ marginTop: '4px', display: 'flex', gap: '4px', flexWrap: 'wrap', color: '#854d0e' }}>
                  <span style={{ fontWeight: 600 }}>Missing Requirements:</span>
                  {liveMatch.missingRequirements.map((s, idx) => (
                    <span key={idx} style={{ background: '#fef9c3', padding: '1px 6px', borderRadius: '4px' }}>{s}</span>
                  ))}
                </div>
              )}
              {liveMatch.eligibilityReasons.length > 0 && (
                <div style={{ marginTop: '6px', color: '#0f766e' }}>
                  <span style={{ fontWeight: 600 }}>Eligibility notes:</span>
                  <ul style={{ margin: '2px 0 0 16px', padding: 0 }}>
                    {liveMatch.eligibilityReasons.map((r, idx) => (
                      <li key={idx}>{r}</li>
                    ))}
                  </ul>
                </div>
              )}
              {liveMatch.relevantProjects.length > 0 && (
                <div style={{ marginTop: '4px', color: '#1d4ed8' }}>
                  <span style={{ fontWeight: 600 }}>Relevant Candidate Projects:</span>
                  <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', marginTop: '2px' }}>
                    {liveMatch.relevantProjects.map((p, idx) => (
                      <span key={idx} style={{ background: '#dbeafe', padding: '1px 6px', borderRadius: '4px' }}>{p}</span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
            <button className="button dark" type="submit" disabled={busy || !title.trim()}>
              {busy ? <LoaderCircle size={15} className="spin" /> : <Plus size={15} />} Save Opportunity
            </button>
            <button
              type="button"
              className="button outline"
              onClick={handleAnalyze}
              disabled={analyzing || !title.trim()}
            >
              {analyzing ? <LoaderCircle size={15} className="spin" /> : <Sparkles size={15} />} Compute Explainable Match
            </button>
            <button type="button" className="button outline" onClick={() => setShowAddForm(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Filter tabs & Search */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '18px 0 12px', flexWrap: 'wrap', gap: '10px' }}>
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
          {['all', 'saved', 'applied', 'interviewing', 'offered', 'rejected', 'withdrawn'].map((st) => (
            <button
              key={st}
              className={`button ${filterStatus === st ? 'dark' : 'outline'}`}
              style={{ minHeight: '28px', padding: '0 10px', fontSize: '11px', textTransform: 'capitalize' }}
              onClick={() => setFilterStatus(st)}
            >
              {st} {st === 'all' ? `(${jobs.length})` : ''}
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', background: '#fff', border: '1px solid #e1e4e9', borderRadius: '6px', padding: '0 8px', height: '32px' }}>
          <Search size={14} style={{ color: '#94a3b8', marginRight: '6px' }} />
          <input
            type="text"
            placeholder="Search saved jobs..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ border: 0, outline: 'none', fontSize: '11px', width: '140px' }}
          />
        </div>
      </div>

      {message && (
        <div className="notice" style={{ marginBottom: '14px' }}>
          {message}
          <button onClick={() => setMessage('')}>×</button>
        </div>
      )}

      {/* Jobs List */}
      <div style={{ display: 'grid', gap: '10px' }}>
        {filtered.length > 0 ? (
          filtered.map((j) => {
            const isExpanded = expandedJobId === j.id;
            return (
              <div
                key={j.id}
                style={{
                  border: '1px solid #e2e8f0',
                  borderRadius: '8px',
                  background: '#fff',
                  padding: '14px',
                  transition: '0.15s ease'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                      <strong style={{ fontSize: '13px', color: '#0f172a' }}>{j.title}</strong>
                      {j.matchScore != null && (
                        <span
                          style={{
                            fontSize: '10px',
                            fontWeight: 700,
                            padding: '2px 8px',
                            borderRadius: '12px',
                            background: `${getBadgeColor(j.matchScore)}15`,
                            color: getBadgeColor(j.matchScore),
                            border: `1px solid ${getBadgeColor(j.matchScore)}40`
                          }}
                        >
                          {Math.round(j.matchScore)}% Criteria Match
                        </span>
                      )}
                      {j.jobType && (
                        <span style={{ fontSize: '9px', background: '#f1f5f9', color: '#475569', padding: '1px 6px', borderRadius: '4px' }}>
                          {j.jobType}
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: '11px', color: '#64748b', marginTop: '3px' }}>
                      {j.company && <span>{j.company} · </span>}
                      {j.location && <span>{j.location} · </span>}
                      <span>Source: {j.source ?? 'Manual'}</span>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <select
                      value={j.status}
                      onChange={(e) => void updateStatus(j.id, e.target.value)}
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
                      <option value="saved">Saved</option>
                      <option value="applied">Applied</option>
                      <option value="interviewing">Interviewing</option>
                      <option value="offered">Offered</option>
                      <option value="rejected">Rejected</option>
                      <option value="withdrawn">Withdrawn</option>
                    </select>

                    {j.jobUrl && (
                      <a
                        href={j.jobUrl}
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
                      onClick={() => {
                        if (isExpanded) {
                          setExpandedJobId(null);
                        } else {
                          void handleInspectMatch(j);
                        }
                      }}
                      title="Inspect match & details"
                    >
                      {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </button>

                    <button
                      className="iconbutton"
                      style={{ width: '28px', height: '28px', color: '#ef4444' }}
                      onClick={() => void deleteJob(j.id)}
                      title="Delete"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                </div>

                {isExpanded && (
                  <div style={{ marginTop: '12px', borderTop: '1px solid #f1f5f9', paddingTop: '10px' }}>
                    {/* Explainable Match Card */}
                    {liveMatch && (
                      <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '6px', padding: '12px', marginBottom: '10px', fontSize: '11px' }}>
                        <div style={{ fontWeight: 700, color: '#1e293b', marginBottom: '4px' }}>
                          {matchSummary(liveMatch.score, liveMatch.matchingRequirements, liveMatch.missingRequirements)}
                        </div>
                        <div style={{ fontSize: '10px', color: '#64748b', fontStyle: 'italic', marginBottom: '8px' }}>
                          {liveMatch.disclaimer}
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '8px' }}>
                          <div>
                            <span style={{ fontWeight: 600, color: '#15803d' }}>Matching Requirements:</span>
                            <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', marginTop: '2px' }}>
                              {liveMatch.matchingRequirements.length > 0 ? (
                                liveMatch.matchingRequirements.map((r, idx) => (
                                  <span key={idx} style={{ background: '#dcfce7', padding: '1px 5px', borderRadius: '4px', fontSize: '10px' }}>{r}</span>
                                ))
                              ) : <span style={{ color: '#94a3b8', fontSize: '10px' }}>None identified</span>}
                            </div>
                          </div>

                          <div>
                            <span style={{ fontWeight: 600, color: '#b45309' }}>Missing Skills / Needs Review:</span>
                            <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', marginTop: '2px' }}>
                              {liveMatch.missingRequirements.length > 0 ? (
                                liveMatch.missingRequirements.map((r, idx) => (
                                  <span key={idx} style={{ background: '#fef3c7', padding: '1px 5px', borderRadius: '4px', fontSize: '10px' }}>{r}</span>
                                ))
                              ) : <span style={{ color: '#94a3b8', fontSize: '10px' }}>None</span>}
                            </div>
                          </div>
                        </div>

                        {liveMatch.eligibilityReasons.length > 0 && (
                          <div style={{ marginTop: '8px', color: '#0f766e' }}>
                            <span style={{ fontWeight: 600 }}>{eligibilityLabel(liveMatch.eligibilityStatus)}</span>
                            <ul style={{ margin: '2px 0 0 16px', padding: 0 }}>
                              {liveMatch.eligibilityReasons.map((e, idx) => (
                                <li key={idx}>{e}</li>
                              ))}
                            </ul>
                          </div>
                        )}

                        {liveMatch.relevantProjects.length > 0 && (
                          <div style={{ marginTop: '6px', color: '#1d4ed8' }}>
                            <span style={{ fontWeight: 600 }}>Relevant Candidate Projects:</span>
                            <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', marginTop: '2px' }}>
                              {liveMatch.relevantProjects.map((p, idx) => (
                                <span key={idx} style={{ background: '#dbeafe', padding: '1px 5px', borderRadius: '4px', fontSize: '10px' }}>{p}</span>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    )}

                    {j.notes && (
                      <div style={{ fontSize: '11px', color: '#334155', marginBottom: '8px' }}>
                        <strong>Notes:</strong> {j.notes}
                      </div>
                    )}
                    {j.description && (
                      <div style={{ fontSize: '11px', color: '#475569', whiteSpace: 'pre-wrap', maxHeight: '180px', overflowY: 'auto', background: '#f8fafc', padding: '10px', borderRadius: '6px' }}>
                        {j.description}
                      </div>
                    )}
                    {questionSuggestions[j.id] && (
                      <div style={{ marginTop: '10px', padding: '10px', background: '#f5f3ff', border: '1px solid #ddd6fe', borderRadius: '6px' }}>
                        <strong style={{ fontSize: '11px', color: '#5b21b6' }}>{questionSuggestions[j.id][0]?.aiGenerated ? 'Ollama-generated draft prompts' : 'Role-specific draft prompts'}</strong>
                        <div style={{ display: 'grid', gap: '6px', marginTop: '6px' }}>
                          {questionSuggestions[j.id].map((suggestion, index) => (
                            <div key={`${suggestion.category}-${index}`} style={{ fontSize: '11px', color: '#334155' }}>
                              <span style={{ textTransform: 'capitalize', color: '#7c3aed', fontWeight: 600 }}>{suggestion.category}: </span>
                              {suggestion.question}
                            </div>
                          ))}
                        </div>
                        <small style={{ display: 'block', marginTop: '6px', color: '#64748b' }}>Draft prompts only. Review and personalize your answers before using them.</small>
                      </div>
                    )}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px', flexWrap: 'wrap', gap: '8px' }}>
                      <small style={{ color: '#94a3b8', fontSize: '10px' }}>
                        Saved: {new Date(j.savedAt).toLocaleDateString()}
                      </small>
                      <div style={{ display: 'flex', gap: '6px' }}>
                        <button
                          className="button outline"
                          style={{ minHeight: '26px', fontSize: '10px', padding: '0 8px' }}
                          disabled={loadingQuestionsFor === j.id}
                          onClick={() => void handleDraftQuestions(j)}
                        >
                          {loadingQuestionsFor === j.id ? <LoaderCircle size={12} className="spin" /> : <Sparkles size={12} />}
                          Draft Questions
                        </button>
                        {onTailorJob && (
                          <button
                            className="button outline"
                            style={{ minHeight: '26px', fontSize: '10px', padding: '0 8px' }}
                            onClick={() => onTailorJob(j)}
                          >
                            <FileText size={12} /> Tailor Resume
                          </button>
                        )}
                        {onPrepareBundle && (
                          <button
                            className="button dark"
                            style={{ minHeight: '26px', fontSize: '10px', padding: '0 8px' }}
                            onClick={() => onPrepareBundle(j)}
                          >
                            <FolderArchive size={12} /> Prepare Bundle
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        ) : (
          <div className="emptysearch">
            <BriefcaseBusiness size={28} />
            <b>No opportunities in this view</b>
            <span>Click &quot;Import Feed / ATS&quot; to import postings or &quot;Manual Entry&quot; to track opportunities.</span>
          </div>
        )}
      </div>
    </div>
  );
}
