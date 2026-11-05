import {useEffect,useState} from 'react';
import {api} from '../api';
import {Check,LoaderCircle,Save,ShieldCheck,Download,Sparkles,Eye,Edit3,Wand2} from 'lucide-react';

type Resume={id:string;filename:string};
type Version={
  version:number;
  sha256:string;
  extractedText:string|null;
  structuredData:Record<string,unknown>;
  evidence:unknown[];
};
type CertOption={id:string;name:string;issuer:string|null;expirationStatus:string};

export default function ResumeVersions({resumes}:{resumes:Resume[]}){
  const [id,setId]=useState('');
  const [versions,setVersions]=useState<Version[]>([]);
  const [selected,setSelected]=useState<Version|null>(null);
  const [text,setText]=useState('');
  const [data,setData]=useState('{}');
  const [evidence,setEvidence]=useState('[]');
  const [busy,setBusy]=useState(false);
  const [message,setMessage]=useState('');

  // Tailoring state
  const [targetRole,setTargetRole]=useState('');
  const [jobDescription,setJobDescription]=useState('');
  const [viewMode,setViewMode]=useState<'edit'|'preview'>('edit');
  const [showTailor,setShowTailor]=useState(false);
  const [certifications,setCertifications]=useState<CertOption[]>([]);
  const [selectedCertifications,setSelectedCertifications]=useState<string[]>([]);

  useEffect(()=>{if(!id&&resumes.length)setId(resumes[0].id);},[resumes]);
  useEffect(()=>{void api.get('/certifications').then(res=>setCertifications(res.data)).catch(()=>setCertifications([]));},[]);

  const load=async(resumeId:string)=>{
    if(!resumeId)return;
    try{
      const {data:vList}=await api.get(`/resumes/${resumeId}/versions`);
      setVersions(vList);
      if(vList.length){
        setSelected(vList[0]);
        setText(vList[0].extractedText??'');
        setData(JSON.stringify(vList[0].structuredData??{},null,2));
        setEvidence(JSON.stringify(vList[0].evidence??[],null,2));
      }else{
        setSelected(null);
      }
    }catch{
      setMessage('Could not load resume versions.');
    }
  };

  useEffect(()=>{void load(id);},[id]);

  const choose=(v:Version)=>{
    setSelected(v);
    setText(v.extractedText??'');
    setData(JSON.stringify(v.structuredData??{},null,2));
    setEvidence(JSON.stringify(v.evidence??[],null,2));
  };

  const save=async()=>{
    if(!selected)return;
    setBusy(true);
    setMessage('');
    try{
      const structuredData=JSON.parse(data);
      const evidenceData=JSON.parse(evidence);
      await api.post(`/resumes/${id}/versions/${selected.version}/review`,{
        extractedText:text,
        structuredData,
        evidence:evidenceData
      });
      await load(id);
      setMessage('Review saved as a new version. Your original file is unchanged.');
    }catch(err:any){
      setMessage(err instanceof SyntaxError?'Structured data and evidence must be valid JSON.':err.response?.data?.message??'Could not save this review.');
    }finally{
      setBusy(false);
    }
  };

  const exportFile=async(format:'pdf'|'docx')=>{
    if(!selected)return;
    setBusy(true);
    setMessage('');
    try{
      const params=new URLSearchParams({format});selectedCertifications.forEach(certId=>params.append('certificationIds',certId));
      const {data:version}=await api.post(`/resumes/${id}/versions/${selected.version}/export?${params.toString()}`);
      const file=await api.get(`/resumes/${id}/versions/${version.version}/download`,{responseType:'blob'});
      const url=URL.createObjectURL(file.data);
      const a=document.createElement('a');
      a.href=url;
      a.download=`resume-v${version.version}.${format}`;
      a.click();
      URL.revokeObjectURL(url);
      await load(id);
      setMessage(`Version ${version.version} exported as ${format.toUpperCase()}.`);
    }catch(err:any){
      setMessage(err.response?.data?.message??'Could not export this resume.');
    }finally{
      setBusy(false);
    }
  };

  const tailorResume=async(e:React.FormEvent)=>{
    e.preventDefault();
    if(!selected||!targetRole.trim()||!jobDescription.trim())return;
    setBusy(true);
    setMessage('');
    try{
      const {data:newVersion}=await api.post(`/resumes/${id}/versions/${selected.version}/tailor`,{
        targetRole:targetRole.trim(),
        jobDescription:jobDescription.trim()
      });
      await load(id);
      setMessage(`Created tailored Version ${newVersion.version} for "${targetRole}". Review highlighted changes below.`);
      setShowTailor(false);
    }catch(err:any){
      setMessage(err.response?.data?.message??'Resume tailoring could not be completed.');
    }finally{
      setBusy(false);
    }
  };

  if(!resumes.length)return <div className="records-empty">Upload a resume to inspect its extracted text, tailored versions, and evidence.</div>;

  const isTailored = Boolean(selected?.structuredData?.tailored);
  const matchedKeywords = Array.isArray(selected?.structuredData?.matchedKeywords)
    ? (selected?.structuredData?.matchedKeywords as string[])
    : [];

  return (
    <div className="resume-review">
      <div className="review-heading">
        <div>
          <h3>Extracted resume studio & tailoring</h3>
          <p>Verify details, customize versions for target roles, and export clean PDF/DOCX documents.</p>
        </div>
        <ShieldCheck size={18}/>
      </div>

      <div style={{display:'flex',gap:'12px',flexWrap:'wrap',alignItems:'flex-end',marginBottom:'12px'}}>
        <label className="review-label" style={{flex:1,minWidth:'180px',margin:0}}>
          Resume
          <select value={id} onChange={e=>setId(e.target.value)}>
            {resumes.map(r=><option value={r.id} key={r.id}>{r.filename}</option>)}
          </select>
        </label>
        {versions.length>1&& (
          <label className="review-label" style={{flex:1,minWidth:'180px',margin:0}}>
            Version History
            <select value={selected?.version??''} onChange={e=>{
              const v=versions.find(x=>x.version===Number(e.target.value));
              if(v)choose(v);
            }}>
              {versions.map(v=>(
                <option value={v.version} key={v.version}>
                  Version {v.version} {v.structuredData?.tailored ? `(Tailored: ${String(v.structuredData.targetRole??'')})` : ''}
                </option>
              ))}
            </select>
          </label>
        )}
        <button
          type="button"
          className="button outline"
          style={{marginBottom:'2px'}}
          onClick={()=>setShowTailor(!showTailor)}
        >
          <Sparkles size={15}/> {showTailor ? 'Close Tailor' : 'Tailor for Job'}
        </button>
      </div>

      {showTailor && (
        <form onSubmit={tailorResume} style={{background:'#f0f4ff',border:'1px solid #d5e0fc',borderRadius:'8px',padding:'14px',marginBottom:'16px'}}>
          <div style={{display:'flex',alignItems:'center',gap:'6px',color:'#3a5dce',fontWeight:700,fontSize:'12px',marginBottom:'8px'}}>
            <Wand2 size={16}/> AI Resume Tailoring (Candidate Controlled)
          </div>
          <p style={{fontSize:'10px',color:'#5a6475',marginBottom:'12px'}}>
            Align your verified experience to target job requirements. Relevant skills and sections will be emphasized without fabricating new claims.
          </p>
          <label className="review-label" style={{margin:'6px 0'}}>
            Target Role Title
            <input
              type="text"
              placeholder="e.g. Full Stack Software Engineer"
              value={targetRole}
              onChange={e=>setTargetRole(e.target.value)}
              required
              style={{height:'34px',borderRadius:'6px',border:'1px solid #c9d5f5',padding:'0 10px',fontSize:'11px'}}
            />
          </label>
          <label className="review-label" style={{margin:'6px 0'}}>
            Job Description & Key Requirements
            <textarea
              rows={5}
              placeholder="Paste the job posting description here to extract keywords..."
              value={jobDescription}
              onChange={e=>setJobDescription(e.target.value)}
              required
              style={{borderRadius:'6px',border:'1px solid #c9d5f5',padding:'8px',fontSize:'11px'}}
            />
          </label>
          <div style={{display:'flex',gap:'8px',marginTop:'10px'}}>
            <button className="button dark" type="submit" disabled={busy || !targetRole.trim() || !jobDescription.trim()}>
              {busy ? <LoaderCircle size={15} className="spin"/> : <Sparkles size={15}/>} Generate Tailored Version
            </button>
            <button type="button" className="button outline" onClick={()=>setShowTailor(false)}>Cancel</button>
          </div>
        </form>
      )}

      {selected ? (
        <>
          {isTailored && (
            <div style={{background:'#eef9f2',border:'1px solid #bfe3cd',borderRadius:'8px',padding:'10px 14px',margin:'10px 0',fontSize:'11px',color:'#22573a'}}>
              <div style={{fontWeight:700,display:'flex',alignItems:'center',gap:'6px'}}>
                <Check size={14}/> Tailored for: {String(selected.structuredData.targetRole ?? 'Target Role')}
              </div>
              {matchedKeywords.length>0 && (
                <div style={{marginTop:'6px',display:'flex',flexWrap:'wrap',gap:'4px'}}>
                  <span style={{fontSize:'10px',color:'#356d4b'}}>Matched keywords:</span>
                  {matchedKeywords.map((kw,idx)=>(
                    <span key={idx} style={{background:'#d5f0df',padding:'2px 7px',borderRadius:'10px',fontSize:'10px',fontWeight:600}}>
                      {kw}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginTop:'10px'}}>
            <div style={{display:'flex',gap:'6px'}}>
              <button
                type="button"
                className={`button ${viewMode==='edit'?'dark':'outline'}`}
                style={{minHeight:'28px',padding:'0 9px',fontSize:'10px'}}
                onClick={()=>setViewMode('edit')}
              >
                <Edit3 size={13}/> Edit Content
              </button>
              <button
                type="button"
                className={`button ${viewMode==='preview'?'dark':'outline'}`}
                style={{minHeight:'28px',padding:'0 9px',fontSize:'10px'}}
                onClick={()=>setViewMode('preview')}
              >
                <Eye size={13}/> Clean Preview
              </button>
            </div>
            <small style={{color:'#8c93a0',fontSize:'10px'}}>Version {selected.version} · SHA-256: {selected.sha256.substring(0,12)}...</small>
          </div>

          {viewMode==='edit' ? (
            <>
              <label className="review-label">
                Resume Text (Candidate editable)
                <textarea className="review-area text-area" value={text} onChange={e=>setText(e.target.value)} rows={10}/>
              </label>
              <label className="review-label">
                Structured fields (JSON)
                <textarea className="review-area" value={data} onChange={e=>setData(e.target.value)} rows={6}/>
              </label>
              <label className="review-label">
                Evidence mapping (JSON)
                <textarea className="review-area" value={evidence} onChange={e=>setEvidence(e.target.value)} rows={4}/>
              </label>
            </>
          ) : (
            <div style={{background:'#fff',border:'1px solid #e1e4e9',borderRadius:'8px',padding:'16px',margin:'12px 0',whiteSpace:'pre-wrap',fontFamily:'Georgia, serif',lineHeight:1.6,fontSize:'12px',maxHeight:'450px',overflowY:'auto',color:'#1e2430'}}>
              {text || 'No text extracted for this version.'}
            </div>
          )}

          {certifications.length>0&&<fieldset className="bundle-cert-select"><legend>Certifications for this exported resume</legend><p>Only the metadata you select is added to this new version. Original files and earlier versions remain unchanged.</p>{certifications.map(cert=><label key={cert.id}><input type="checkbox" checked={selectedCertifications.includes(cert.id)} onChange={e=>setSelectedCertifications(current=>e.target.checked?[...current,cert.id]:current.filter(id=>id!==cert.id))}/><span>{cert.name}{cert.issuer?` · ${cert.issuer}`:''}</span></label>)}</fieldset>}

          <div className="review-footer" style={{marginTop:'12px'}}>
            <small>Immutable original stored securely.</small>
            <div className="review-buttons">
              <button className="button outline" onClick={()=>void exportFile('docx')} disabled={busy}>
                <Download size={15}/> Export DOCX
              </button>
              <button className="button outline" onClick={()=>void exportFile('pdf')} disabled={busy}>
                <Download size={15}/> Export PDF
              </button>
              <button className="button dark" onClick={()=>void save()} disabled={busy}>
                {busy?<LoaderCircle size={15} className="spin"/>:<Save size={15}/>} Save as New Version
              </button>
            </div>
          </div>
        </>
      ) : (
        <div className="records-empty">No parsed version is available. Check the worker status and try uploading the file again.</div>
      )}

      {message&&<div className="notice" style={{marginTop:'14px'}}>{message}<button onClick={()=>setMessage('')}>×</button></div>}
    </div>
  );
}
