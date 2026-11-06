// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mocks=vi.hoisted(()=>({get:vi.fn(),post:vi.fn(),put:vi.fn(),delete:vi.fn()}));
vi.mock('../api',()=>({api:mocks}));
import CertificationVault from './CertificationVault';

describe('CertificationVault',()=>{
  afterEach(()=>cleanup());
  beforeEach(()=>{vi.clearAllMocks();mocks.get.mockResolvedValue({data:[]});mocks.post.mockResolvedValue({data:{}});mocks.put.mockResolvedValue({data:{}});mocks.delete.mockResolvedValue({data:{}});});

  it('shows a useful empty state and expiry filters',async()=>{
    render(<CertificationVault/>);
    expect(await screen.findByText('Your vault is empty.')).toBeTruthy();
    expect(screen.getByLabelText('Filter by expiration')).toBeTruthy();
  });

  it('labels saved links candidate-provided and distinguishes no expiry',async()=>{
    mocks.get.mockResolvedValue({data:[{id:'c1',name:'Example Cloud Credential',issuer:'Example Academy',issueDate:'2025-01-01',expirationDate:null,credentialId:'EX-123',officialVerificationUrl:'https://issuer.example/check',credlyBadgeUrl:'https://credly.example/badge',scoreResult:'Pass',candidateVerified:true,reminderDays:[90,30,7],expirationStatus:'NO_EXPIRY',certificateFileName:null,certificateSizeBytes:null,malwareScanStatus:'NOT_CONFIGURED',linksCandidateProvided:true}]});
    render(<CertificationVault/>);
    expect(await screen.findByText('Example Cloud Credential')).toBeTruthy();
    expect(screen.getAllByText('No expiry').some(element=>element.tagName==='SPAN')).toBe(true);
    expect(screen.getAllByText('(candidate-provided)').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('Credly is not scraped or queried by ApplyPilot.')).toBeTruthy();
  });

  it('creates certification details through the vault API without asserting issuer verification',async()=>{
    const user=userEvent.setup();
    render(<CertificationVault/>);
    fireEvent.change(screen.getByLabelText('Certification name'), { target: { value: 'Synthetic Security Credential' } });
    fireEvent.change(screen.getByLabelText('Issuing organization'), { target: { value: 'Fictional Issuer' } });
    fireEvent.change(screen.getByLabelText('Credential ID'), { target: { value: 'SYN-2026-1' } });
    await user.click(screen.getByRole('button',{name:'Save certification'}));
    await waitFor(()=>expect(mocks.post).toHaveBeenCalledWith('/certifications',expect.objectContaining({name:'Synthetic Security Credential',issuer:'Fictional Issuer',credentialId:'SYN-2026-1',candidateVerified:false,reminderDays:[90,30,7]})));
    expect(await screen.findByText('Certification saved. Links remain candidate-provided until independently verified.')).toBeTruthy();
  }, 10_000);

  it('shows API validation errors without clearing the candidate form',async()=>{
    mocks.post.mockRejectedValue({response:{data:{message:'Expiration date must be on or after the issue date.'}}});
    const user=userEvent.setup();render(<CertificationVault/>);
    fireEvent.change(screen.getByLabelText('Certification name'), { target: { value: 'Bad date fixture' } });
    await user.click(screen.getByRole('button',{name:'Save certification'}));
    expect(await screen.findByText('Expiration date must be on or after the issue date.')).toBeTruthy();
    expect((screen.getByLabelText('Certification name') as HTMLInputElement).value).toBe('Bad date fixture');
  }, 10_000);
});
