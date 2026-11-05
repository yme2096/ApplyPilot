import { useEffect, useMemo, useState } from 'react';
import { Award, CalendarClock, Check, Download, ExternalLink, FileUp, LoaderCircle, Plus, Search, ShieldCheck, Trash2 } from 'lucide-react';
import { api } from '../api';

type Cert = {
  id:string; name:string; issuer:string|null; issueDate:string|null; expirationDate:string|null; credentialId:string|null;
  officialVerificationUrl:string|null; credlyBadgeUrl:string|null; scoreResult:string|null; candidateVerified:boolean;
  reminderDays:number[]; expirationStatus:'ACTIVE'|'EXPIRING_SOON'|'EXPIRED'|'NO_EXPIRY'; certificateFileName:string|null;
  certificateSizeBytes:number|null; malwareScanStatus:string; linksCandidateProvided:boolean;
};
type Form = Omit<Cert,'id'|'expirationStatus'|'certificateFileName'|'certificateSizeBytes'|'malwareScanStatus'|'linksCandidateProvided'>;
const empty:Form={name:'',issuer:'',issueDate:'',expirationDate:'',credentialId:'',officialVerificationUrl:'',credlyBadgeUrl:'',scoreResult:'',candidateVerified:false,reminderDays:[90,30,7]};
const statuses:Record<Cert['expirationStatus'],string>={ACTIVE:'Active',EXPIRING_SOON:'Expiring soon',EXPIRED:'Expired',NO_EXPIRY:'No expiry'};
const badgeClass:Record<Cert['expirationStatus'],string>={ACTIVE:'cert-badge active',EXPIRING_SOON:'cert-badge soon',EXPIRED:'cert-badge expired',NO_EXPIRY:'cert-badge none'};

export default function CertificationVault(){
  const [rows,setRows]=useState<Cert[]>([]); const [form,setForm]=useState<Form>(empty); const [editing,setEditing]=useState<string|null>(null);
  const [busy,setBusy]=useState(false); const [uploadId,setUploadId]=useState<string|null>(null); const [progress,setProgress]=useState(0);
  const [message,setMessage]=useState(''); const [search,setSearch]=useState(''); const [filter,setFilter]=useState('ALL');
  const load=async()=>{try{const {data}=await api.get('/certifications');setRows(data);}catch{setMessage('Could not load certification vault. Please retry.');}};
  useEffect(()=>{void load();},[]);
  const visible=useMemo(()=>rows.filter(c=>(filter==='ALL'||c.expirationStatus===filter)&&(`${c.name} ${c.issuer??''} ${c.credentialId??''}`).toLowerCase().includes(search.toLowerCase())),[rows,filter,search]);
  const set=(key:keyof Form,value:unknown)=>setForm(current=>({...current,[key]:value}));
  const reset=()=>{setForm(empty);setEditing(null);};
  const submit=async(e:React.FormEvent)=>{e.preventDefault();setBusy(true);setMessage('');try{const body={...form,issuer:form.issuer||null,issueDate:form.issueDate||null,expirationDate:form.expirationDate||null,credentialId:form.credentialId||null,officialVerificationUrl:form.officialVerificationUrl||null,credlyBadgeUrl:form.credlyBadgeUrl||null,scoreResult:form.scoreResult||null};if(editing)await api.put(`/certifications/${editing}`,body);else await api.post('/certifications',body);reset();await load();setMessage(editing?'Certification updated.':'Certification saved. Links remain candidate-provided until independently verified.');}catch(err:any){setMessage(err.response?.data?.message??'Could not save this certification.');}finally{setBusy(false);}};
  const edit=(c:Cert)=>{setEditing(c.id);setForm({name:c.name,issuer:c.issuer??'',issueDate:c.issueDate??'',expirationDate:c.expirationDate??'',credentialId:c.credentialId??'',officialVerificationUrl:c.officialVerificationUrl??'',credlyBadgeUrl:c.credlyBadgeUrl??'',scoreResult:c.scoreResult??'',candidateVerified:c.candidateVerified,reminderDays:c.reminderDays??[90,30,7]});window.scrollTo({top:0,behavior:'smooth'});};
  const archive=async(c:Cert)=>{if(!window.confirm(`Archive “${c.name}”? Its private PDF and metadata will be retained.`))return;try{await api.delete(`/certifications/${c.id}`);await load();setMessage('Certification archived. Its uploaded file is retained privately.');}catch{setMessage('Could not archive this certification.');}};
  const upload=async(id:string,file?:File)=>{if(!file)return;if(file.type!=='application/pdf'&&!file.name.toLowerCase().endsWith('.pdf')){setMessage('Choose a PDF certificate.');return;}const body=new FormData();body.append('file',file);setUploadId(id);setProgress(0);setMessage('');try{const {data}=await api.post(`/certifications/${id}/file`,body,{onUploadProgress:e=>setProgress(e.total?Math.round(e.loaded*100/e.total):0)});setRows(current=>current.map(c=>c.id===id?data:c));setMessage(`PDF attached. Malware scan status: ${data.malwareScanStatus}.`);}catch(err:any){setMessage(err.response?.data?.message??'Certificate upload failed.');}finally{setUploadId(null);setProgress(0);}};
  const download=async(c:Cert)=>{try{const {data}=await api.get(`/certifications/${c.id}/file`,{responseType:'blob'});const url=URL.createObjectURL(data);const a=document.createElement('a');a.href=url;a.download=c.certificateFileName??'certificate.pdf';a.click();URL.revokeObjectURL(url);}catch{setMessage('Certificate download failed.');}};
  return <div className="cert-vault">
    <header className="cert-head"><div><div className="eyebrow">CANDIDATE CREDENTIALS</div><h2>Certification Vault</h2><p>Keep credential details and private certificate PDFs together. Verification links are candidate-provided.</p></div><Award size={25} aria-hidden="true"/></header>
    <section className="cert-panel" aria-labelledby="cert-form-heading"><div className="eyebrow">{editing?'UPDATE CREDENTIAL':'ADD CREDENTIAL'}</div><h3 id="cert-form-heading">{editing?'Edit certification':'New certification'}</h3>
      <form onSubmit={submit}><div className="cert-form-grid">
        <label>Certification name<input required maxLength={200} value={form.name} onChange={e=>set('name',e.target.value)}/></label>
        <label>Issuing organization<input maxLength={200} value={form.issuer??''} onChange={e=>set('issuer',e.target.value)}/></label>
        <label>Issue date<input type="date" value={form.issueDate??''} onChange={e=>set('issueDate',e.target.value)}/></label>
        <label>Expiration date <span className="cert-muted">(leave blank for no expiry)</span><input type="date" value={form.expirationDate??''} onChange={e=>set('expirationDate',e.target.value)}/></label>
        <label>Credential ID<input maxLength={255} value={form.credentialId??''} onChange={e=>set('credentialId',e.target.value)}/></label>
        <label>Score / result<input maxLength={200} value={form.scoreResult??''} onChange={e=>set('scoreResult',e.target.value)}/></label>
        <label>Official issuer verification URL<input type="url" placeholder="https://" value={form.officialVerificationUrl??''} onChange={e=>set('officialVerificationUrl',e.target.value)}/><small>Saved as a candidate-provided link; ApplyPilot does not verify it.</small></label>
        <label>Credly badge URL<input type="url" placeholder="https://www.credly.com/" value={form.credlyBadgeUrl??''} onChange={e=>set('credlyBadgeUrl',e.target.value)}/><small>Credly is not scraped or queried by ApplyPilot.</small></label>
        <label>Reminder days before expiry<input inputMode="numeric" value={(form.reminderDays??[]).join(', ')} onChange={e=>set('reminderDays',e.target.value.split(',').map(x=>Number(x.trim())).filter(Number.isFinite))}/><small>Defaults: 90, 30, 7. Use up to five comma-separated days.</small></label>
      </div><label className="cert-check"><input type="checkbox" checked={form.candidateVerified} onChange={e=>set('candidateVerified',e.target.checked)}/> I reviewed these details (candidate self-attestation only)</label>
      <div className="cert-actions"><button className="button dark" disabled={busy}>{busy?<LoaderCircle size={15} className="spin"/>:<Check size={15}/>} {editing?'Save changes':'Save certification'}</button>{editing&&<button type="button" className="button outline" onClick={reset}>Cancel</button>}</div></form>
    </section>
    <section className="cert-panel"><div className="cert-toolbar"><div><div className="eyebrow">YOUR RECORDS</div><h3>Saved certifications ({rows.length})</h3></div><label className="cert-search"><Search size={15}/><span className="sr-only">Search certifications</span><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search name, issuer, ID"/></label><label className="sr-only" htmlFor="cert-filter">Filter by expiration</label><select id="cert-filter" value={filter} onChange={e=>setFilter(e.target.value)}><option value="ALL">All statuses</option><option value="ACTIVE">Active</option><option value="EXPIRING_SOON">Expiring soon</option><option value="EXPIRED">Expired</option><option value="NO_EXPIRY">No expiry</option></select></div>
      {visible.length===0?<div className="cert-empty"><Award size={22}/><b>{rows.length?'No certifications match these filters.':'Your vault is empty.'}</b><span>Add a credential above. You can enter existing certifications manually and attach each PDF later.</span></div>:<div className="cert-list">{visible.map(c=><article className="cert-card" key={c.id}><div className="cert-card-title"><div className="cert-icon"><Award size={18}/></div><div className="cert-title-text"><h4>{c.name}</h4><p>{c.issuer||'Issuing organization not provided'}{c.issueDate?` · Issued ${c.issueDate}`:''}</p></div><span className={badgeClass[c.expirationStatus]}>{statuses[c.expirationStatus]}</span></div>
        <div className="cert-meta">{c.credentialId&&<span>Credential ID: {c.credentialId}</span>}{c.expirationDate&&<span><CalendarClock size={13}/> Expires {c.expirationDate}</span>}{c.scoreResult&&<span>Result: {c.scoreResult}</span>}<span>{c.candidateVerified?<><ShieldCheck size={13}/> Candidate reviewed</>:'Candidate review not recorded'}</span></div>
        <div className="cert-links">{c.officialVerificationUrl&&<a href={c.officialVerificationUrl} target="_blank" rel="noopener noreferrer"><ExternalLink size={14}/> Official verification <small>(candidate-provided)</small></a>}{c.credlyBadgeUrl&&<a href={c.credlyBadgeUrl} target="_blank" rel="noopener noreferrer"><ExternalLink size={14}/> Credly badge <small>(candidate-provided)</small></a>}</div>
        <div className="cert-file-row">{c.certificateFileName?<><button className="button outline" type="button" onClick={()=>void download(c)}><Download size={14}/> Download {c.certificateFileName}</button><span className="cert-muted">Private PDF · {c.certificateSizeBytes} bytes · scanner {c.malwareScanStatus}</span></>:<label className="button outline cert-upload"><FileUp size={14}/>{uploadId===c.id?<><LoaderCircle size={14} className="spin"/> Uploading {progress}%</>:'Attach certificate PDF'}<input type="file" accept="application/pdf,.pdf" disabled={uploadId!==null} onChange={e=>{void upload(c.id,e.target.files?.[0]);e.currentTarget.value='';}}/></label>}</div>
        <div className="cert-actions"><button className="button outline" type="button" onClick={()=>edit(c)}>Edit</button><button className="button danger-outline" type="button" onClick={()=>void archive(c)}><Trash2 size={14}/> Archive</button></div>
      </article>)}</div>}
    </section>
    {message&&<div className="cert-message" role="status" aria-live="polite">{message}</div>}
  </div>;
}
