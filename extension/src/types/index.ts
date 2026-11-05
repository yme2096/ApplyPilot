export type AtsType = 'greenhouse' | 'lever' | 'workday' | 'ashby' | 'generic';

export interface EducationEntry {
  institution?: string;
  degree?: string;
  fieldOfStudy?: string;
  graduationYear?: number;
}

export interface ExperienceEntry {
  company?: string;
  title?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  isCurrent?: boolean;
}

export interface ProjectEntry {
  name?: string;
  description?: string;
  technologies?: string[] | string;
}

export interface CandidateProfile {
  fullName: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  phone: string;
  location: string;
  linkedinUrl?: string;
  githubUrl?: string;
  portfolioUrl?: string;
  graduationYear?: number | null;
  jobType?: string;
  salaryPreference?: string;
  education?: EducationEntry[];
  experience?: ExperienceEntry[];
  projects?: ProjectEntry[];
  skills?: string[];
  legallyAuthorized?: boolean;
  requiresSponsorship?: boolean;
  noticePeriod?: string;
}

export interface ApplicationBundleData {
  id: string;
  jobId: string;
  jobTitle: string;
  company: string;
  resumeId?: string;
  resumeDownloadUrl?: string;
  resumeVersionNumber?: number;
  status: 'DRAFT' | 'READY' | 'IN_REVIEW' | 'SUBMITTED_BY_CANDIDATE';
  coverLetterText?: string;
  notes?: string;
  applicationAnswers: Array<{ question: string; answer: string; category?: string }>;
  requiredDocuments?: Array<{ docType: string; filename: string }>;
}

export interface DetectedFieldInfo {
  element: HTMLElement;
  tag: string;
  type: string;
  resolvedLabel: string;
  name: string;
  id: string;
  placeholder: string;
  ariaLabel: string;
  options?: Array<{ value: string; text: string }>;
}

export interface DetectedForm {
  ats: AtsType;
  formElement: HTMLFormElement | null;
  detectedFields: Record<string, DetectedFieldInfo>;
  isApplicationPage: boolean;
  hasResumeUpload: boolean;
  resumeInputElement?: HTMLInputElement | null;
  jobTitle?: string;
  companyName?: string;
}

export interface MappedFieldAssignment {
  element: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
  value: string | boolean;
  fieldLabel: string;
  confidence: 'HIGH' | 'UNCERTAIN';
  matchReason: string;
  requiresCandidateConfirmation?: boolean;
}

export interface AutofillResult {
  filledCount: number;
  uncertainCount: number;
  labels: string[];
  resumeFieldDetected: boolean;
  hasAmbiguousFields: boolean;
  unsupportedCount: number;
}

export interface DetectionResponse {
  detected: boolean;
  ats: AtsType;
  url: string;
  matchedFieldsCount: number;
  hasResumeUpload: boolean;
  jobTitle?: string;
  company?: string;
}
