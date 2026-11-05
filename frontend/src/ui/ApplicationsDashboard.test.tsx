// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ApplicationsDashboard from './ApplicationsDashboard';
import { api } from '../api';

vi.mock('../api', () => ({ api: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), put: vi.fn() } }));

const app = { id:'app-1', job_id:'job-1', job_title:'Backend Engineer', company:'Northwind', status:'SAVED', notes:null, applied_at:null, resume_version_number:2, interview_scheduled_at:null };
const question = { id:'q-1', category:'PROJECT', question:'Describe a related project.', evidenceRequirement:'Use confirmed evidence.' };
const metrics = { total:1, interviews:0, offers:0, responseRate:0, interviewRate:0, byStatus:{SAVED:1}, weeklyActivity:[], monthlyActivity:[] };
const preferenceState = { followupReminders:true, interviewReminders:true, statusChanges:true, preparationReminders:true };

function response(path:string) {
  const values:Record<string,unknown> = {
    '/applications':[app], '/applications/dashboard':metrics,
    '/jobs':[{id:'job-1',title:'Backend Engineer',company:'Northwind',location:'Remote'},{id:'job-2',title:'QA Engineer',company:'Contoso',location:'Remote'}],
    '/followups':{overdue:[],upcoming:[],completed:[]}, '/notifications':[], '/notification-preferences':preferenceState,
    '/applications/app-1/interview-prep':{id:'prep-1',questions:[question],progress:{}},
  };
  return { data: values[path] ?? {} };
}

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const patch = vi.mocked(api.patch);
const put = vi.mocked(api.put);

describe('Applications dashboard integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    get.mockImplementation(async (path:string) => response(path) as any);
    post.mockResolvedValue({data:{}} as any);
    patch.mockResolvedValue({data:{...app,status:'INTERVIEW'}} as any);
    put.mockResolvedValue({data:preferenceState} as any);
  });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it('loads database-backed sections and suppresses already tracked jobs', async () => {
    render(<ApplicationsDashboard/>);
    expect(screen.getByText(/Loading application data/)).toBeTruthy();
    expect(await screen.findByText('Backend Engineer')).toBeTruthy();
    expect(screen.getAllByText('1').length).toBeGreaterThan(0);
    expect(screen.getByRole('button',{name:/Track application/})).toBeTruthy();
    expect(screen.queryByRole('button',{name:/Track application/})?.closest('.phase4-row')?.textContent).toContain('QA Engineer');
  });

  it('creates tracking from a saved job and refreshes the dashboard', async () => {
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    await user.click(await screen.findByRole('button',{name:/Track application/}));
    expect(post).toHaveBeenCalledWith('/applications',{jobId:'job-2',status:'SAVED'});
    expect(await screen.findByText('Application tracking started.')).toBeTruthy();
  });

  it('requires confirmation before marking a candidate application applied', async () => {
    const confirm=vi.fn(()=>false); vi.stubGlobal('confirm',confirm);
    render(<ApplicationsDashboard/>);
    const status=await screen.findByRole('combobox',{name:'Status for Backend Engineer'});
    fireEvent.change(status,{target:{value:'APPLIED'}});
    expect(confirm).toHaveBeenCalledOnce(); expect(patch).not.toHaveBeenCalled();
  });

  it('schedules interview reminders only after the candidate saves the selected time', async () => {
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    const date=await screen.findByLabelText('Interview date for Backend Engineer');
    fireEvent.change(date,{target:{value:'2030-01-05T11:00'}});
    expect(patch).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button',{name:'Save interview date'}));
    expect(patch).toHaveBeenCalledWith('/applications/app-1',{interviewScheduledAt:new Date('2030-01-05T11:00').toISOString()});
  });

  it('allows editing a follow-up note through an accessible form', async () => {
    get.mockImplementation(async (path:string) => path==='/followups'
      ? {data:{overdue:[],upcoming:[{id:'f-1',application_id:'app-1',due_at:'2026-11-10T10:00:00Z',note:'Call recruiter',completed_at:null,reminder_enabled:true}],completed:[]}} as any
      : response(path) as any);
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    await user.click(await screen.findByRole('button',{name:'Edit'}));
    const dialog=screen.getByRole('dialog',{name:'Edit follow-up reminder'});
    const note=within(dialog).getByRole('textbox',{name:'Follow-up note'});
    await user.clear(note); await user.type(note,'Send a concise follow-up');
    fireEvent.change(within(dialog).getByLabelText('Follow-up date and time'),{target:{value:'2026-11-11T12:30'}});
    await user.click(within(dialog).getByRole('checkbox',{name:'Enable in-app reminder'}));
    await user.click(within(dialog).getByRole('button',{name:'Save follow-up'}));
    expect(patch).toHaveBeenCalledWith('/followups/f-1',{dueAt:new Date('2026-11-11T12:30').toISOString(),reminderEnabled:false,note:'Send a concise follow-up'});
  });

  it('keeps local AI evidence visible and answer editable until explicit save; Escape closes the dialog', async () => {
    post.mockResolvedValueOnce({data:{answer:'I built a small API.',evidenceUsed:[{sourceId:'resume-1',version:2,excerpt:'Built a small API'}]}} as any);
    patch.mockResolvedValueOnce({data:{id:'prep-1',questions:[question],progress:{'q-1':{answer:'Edited and verified by candidate.',editedByCandidate:true}}}} as any);
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    const opener=await screen.findByRole('button',{name:/Interview prep/}); await user.click(opener);
    const dialog=await screen.findByRole('dialog',{name:'Interview preparation · Backend Engineer'});
    const close=within(dialog).getByRole('button',{name:'Close'});
    expect(document.activeElement).toBe(close);
    await user.tab({shift:true}); expect(document.activeElement).toBe(within(dialog).getByRole('button',{name:'Save answer'}));
    await user.tab(); expect(document.activeElement).toBe(close);
    await user.click(within(dialog).getByRole('button',{name:'Draft with local AI'}));
    const answer=within(dialog).getByRole('textbox',{name:'Your answer'});
    await waitFor(()=>expect((answer as HTMLTextAreaElement).value).toBe('I built a small API.'));
    expect(within(dialog).getByLabelText('Evidence supporting this draft').textContent).toContain('Built a small API');
    expect(patch).not.toHaveBeenCalled();
    await user.clear(answer); await user.type(answer,'Edited and verified by candidate.');
    await user.click(within(dialog).getByRole('button',{name:'Save answer'}));
    expect(patch).toHaveBeenCalledWith('/applications/app-1/interview-prep',{questionId:'q-1',answer:'Edited and verified by candidate.'});
    await user.keyboard('{Escape}');
    await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());
    expect(document.activeElement).toBe(opener);
  });

  it('keeps answer editing available when local AI is disabled or unavailable', async () => {
    post.mockRejectedValueOnce({response:{status:503,data:{message:'Local AI is disabled or unavailable.'}}});
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    await user.click(await screen.findByRole('button',{name:/Interview prep/}));
    const dialog=await screen.findByRole('dialog',{name:'Interview preparation · Backend Engineer'});
    const answer=within(dialog).getByRole('textbox',{name:'Your answer'});
    await user.click(within(dialog).getByRole('button',{name:'Draft with local AI'}));
    expect(await screen.findByText('Local AI is disabled or unavailable.')).toBeTruthy();
    await user.type(answer,'Candidate-authored answer remains available.');
    expect((answer as HTMLTextAreaElement).value).toContain('Candidate-authored answer');
  });

  it('persists notification preferences and reports partial API failures without showing a false empty state', async () => {
    get.mockImplementation(async (path:string) => path==='/notifications'?Promise.reject({response:{data:{message:'Notifications are offline'}}}):response(path) as any);
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    expect((await screen.findByRole('alert')).textContent).toContain('notifications');
    expect(screen.getByText('Backend Engineer')).toBeTruthy();
    get.mockImplementation(async (path:string)=>response(path) as any);
    await user.click(screen.getByRole('checkbox',{name:'Interview reminders'}));
    await waitFor(()=>expect(put).toHaveBeenCalledWith('/notification-preferences',{...preferenceState,interviewReminders:false}));
  });

  it('retries failed dashboard requests when requested', async () => {
    get.mockImplementation(async (path:string)=>path==='/applications'?Promise.reject({response:{data:{message:'Offline'}}}):response(path) as any);
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    expect(await screen.findByText('Application records are unavailable. Use Retry loading above.')).toBeTruthy();
    get.mockImplementation(async (path:string)=>response(path) as any);
    await user.click(screen.getByRole('button',{name:'Retry loading'}));
    expect(await screen.findByText('Backend Engineer')).toBeTruthy();
    expect(screen.queryByText('Application records are unavailable. Use Retry loading above.')).toBeNull();
  });

  it('shows an empty state only after successful empty responses', async () => {
    get.mockImplementation(async (path:string) => response(path) as any);
    get.mockImplementation(async (path:string) => path==='/applications'?{data:[]}:path==='/jobs'?{data:[]} as any:response(path) as any);
    render(<ApplicationsDashboard/>);
    expect(await screen.findByText(/No applications tracked yet/)).toBeTruthy();
    expect(screen.getByText('All saved opportunities have a tracking record.')).toBeTruthy();
  });

  it('rolls back notification preferences when the save request fails', async () => {
    put.mockRejectedValueOnce({response:{data:{message:'Save failed'}}});
    const user=userEvent.setup(); render(<ApplicationsDashboard/>);
    const toggle=await screen.findByRole('checkbox',{name:'Interview reminders'}) as HTMLInputElement;
    expect(toggle.checked).toBe(true);
    await user.click(toggle);
    await waitFor(()=>expect(toggle.checked).toBe(true));
    expect(await screen.findByText('Save failed')).toBeTruthy();
  });
});
