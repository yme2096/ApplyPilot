import { CandidateProfile, ApplicationBundleData, DetectedFieldInfo, MappedFieldAssignment } from '../types';

export function mapCandidateFieldsToForm(
  detectedFields: Record<string, DetectedFieldInfo>,
  profile: CandidateProfile,
  bundle?: ApplicationBundleData,
  options: { confirmLegalAnswers?: boolean } = {}
): MappedFieldAssignment[] {
  const assignments: MappedFieldAssignment[] = [];
  const assignedElements = new Set<HTMLElement>();

  const nameParts = (profile.fullName || '').trim().split(/\s+/);
  const firstName = profile.firstName || nameParts[0] || '';
  const lastName = profile.lastName || (nameParts.length > 1 ? nameParts.slice(1).join(' ') : '');

  // Retrieve primary education and experience if available
  const primaryEdu = profile.education && profile.education.length > 0 ? profile.education[0] : null;
  const primaryExp = profile.experience && profile.experience.length > 0 ? profile.experience[0] : null;

  // Rule definitions with strict disambiguation
  interface FieldRule {
    target: string;
    aliases: string[];
    negativeAliases?: string[]; // prevent false positive matches
    getValue: () => string | boolean | null;
  }

  const rules: FieldRule[] = [
    // 1. First Name
    {
      target: 'First Name',
      aliases: ['first name', 'given name', 'first_name', 'firstname', 'fname'],
      negativeAliases: ['last', 'sur', 'family', 'middle'],
      getValue: () => firstName
    },
    // 2. Last Name
    {
      target: 'Last Name',
      aliases: ['last name', 'surname', 'family name', 'last_name', 'lastname', 'lname'],
      negativeAliases: ['first', 'given', 'middle'],
      getValue: () => lastName
    },
    // 3. Full Name
    {
      target: 'Full Name',
      aliases: ['full name', 'complete name', 'candidate name', 'your name', 'fullname', 'name'],
      negativeAliases: ['first', 'last', 'sur', 'family', 'company', 'school', 'user'],
      getValue: () => profile.fullName
    },
    // 4. Email
    {
      target: 'Email',
      aliases: ['email', 'email address', 'e-mail', 'electronic mail'],
      negativeAliases: ['confirm', 'verify'],
      getValue: () => profile.email || ''
    },
    // 5. Phone
    {
      target: 'Phone',
      aliases: ['phone', 'mobile', 'telephone', 'phone number', 'cell', 'contact number'],
      negativeAliases: ['emergency'],
      getValue: () => profile.phone
    },
    // 6. Location / Address
    {
      target: 'Location',
      aliases: ['location', 'city', 'current location', 'address', 'residence', 'candidate location'],
      negativeAliases: ['school', 'company', 'previous'],
      getValue: () => profile.location
    },
    // 7. LinkedIn Profile
    {
      target: 'LinkedIn Profile',
      aliases: ['linkedin', 'linkedin profile', 'linkedin url', 'linked in'],
      getValue: () => profile.linkedinUrl || ''
    },
    // 8. GitHub Profile
    {
      target: 'GitHub Profile',
      aliases: ['github', 'github url', 'github profile', 'git repository'],
      getValue: () => profile.githubUrl || ''
    },
    // 9. Portfolio / Website
    {
      target: 'Portfolio URL',
      aliases: ['portfolio', 'website', 'personal website', 'blog', 'homepage', 'url'],
      negativeAliases: ['linkedin', 'github'],
      getValue: () => profile.portfolioUrl || ''
    },
    // 10. Education: School / Institution
    {
      target: 'School / Institution',
      aliases: ['school', 'university', 'college', 'institution', 'education institution'],
      getValue: () => primaryEdu?.institution || ''
    },
    // 11. Education: Degree
    {
      target: 'Degree',
      aliases: ['degree', 'education level', 'degree level'],
      getValue: () => primaryEdu?.degree || ''
    },
    // 12. Education: Major / Field of Study
    {
      target: 'Field of Study',
      aliases: ['major', 'field of study', 'discipline', 'subject', 'area of study'],
      getValue: () => primaryEdu?.fieldOfStudy || ''
    },
    // 13. Graduation Year
    {
      target: 'Graduation Year',
      aliases: ['graduation year', 'grad year', 'completion year', 'year of graduation', 'graduation date'],
      getValue: () => {
        const yr = primaryEdu?.graduationYear || profile.graduationYear;
        return yr ? String(yr) : null;
      }
    },
    // 14. Work Experience: Current / Most Recent Employer
    {
      target: 'Current / Recent Company',
      aliases: ['current company', 'current employer', 'most recent company', 'employer'],
      getValue: () => primaryExp?.company || ''
    },
    // 15. Work Experience: Job Title
    {
      target: 'Job Title / Role',
      aliases: ['current title', 'job title', 'current role', 'recent title'],
      getValue: () => primaryExp?.title || ''
    },
    // 16. Skills Summary
    {
      target: 'Key Skills',
      aliases: ['skills', 'key skills', 'technical skills', 'technologies', 'proficiencies'],
      getValue: () => (profile.skills && profile.skills.length > 0 ? profile.skills.join(', ') : '')
    },
    // 17. Work Authorization (Legally authorized to work)
    {
      target: 'Work Authorization',
      aliases: ['legally authorized', 'authorized to work', 'legal right to work', 'work authorization'],
      negativeAliases: ['sponsorship', 'sponsor', 'require sponsorship', 'future'],
      getValue: () => profile.legallyAuthorized ?? null
    },
    // 18. Sponsorship Required (Require sponsorship now or in future)
    {
      target: 'Visa Sponsorship',
      aliases: ['require sponsorship', 'future sponsorship', 'visa sponsorship', 'need sponsorship'],
      getValue: () => profile.requiresSponsorship ?? null
    },
    // 19. Notice Period / Start Date
    {
      target: 'Notice Period',
      aliases: ['notice period', 'how soon can you start', 'start date', 'availability', 'earliest start'],
      getValue: () => profile.noticePeriod || ''
    },
    // 20. Salary Expectations
    {
      target: 'Salary Expectations',
      aliases: ['salary expectations', 'desired salary', 'expected compensation', 'salary requirement'],
      getValue: () => profile.salaryPreference || ''
    }
  ];

  // Evaluate each detected field against rules
  for (const [, field] of Object.entries(detectedFields)) {
    if (assignedElements.has(field.element)) continue;

    const queryKey = [field.resolvedLabel, field.name, field.id, field.placeholder, field.ariaLabel]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    const legalOrWorkAuthorization = /\b(work authorization|authorized to work|legally authorized|sponsorship|sponsor|citizenship|visa|criminal history|background check|terms and conditions|attest|certify|declaration)\b/.test(queryKey);
    if (legalOrWorkAuthorization && !options.confirmLegalAnswers) continue;

    // Check specific rules first
    for (const rule of rules) {
      if (assignedElements.has(field.element)) break;

      const hasNegative = rule.negativeAliases?.some((neg) => queryKey.includes(neg));
      if (hasNegative) continue;

      const matches = rule.aliases.some((alias) => queryKey.includes(alias));
      if (matches) {
        const val = rule.getValue();
        if (val !== null && val !== undefined && val !== '') {
          assignments.push({
            element: field.element as any,
            value: val,
            fieldLabel: rule.target,
            confidence: legalOrWorkAuthorization ? 'UNCERTAIN' : (isHighConfidence(field, rule.aliases) ? 'HIGH' : 'UNCERTAIN'),
            requiresCandidateConfirmation: legalOrWorkAuthorization,
            matchReason: `Matched rule for ${rule.target}`
          });
          assignedElements.add(field.element);
          break;
        }
      }
    }
  }

  // Next, map bundle custom screening Q&A
  if (bundle?.applicationAnswers && bundle.applicationAnswers.length > 0) {
    for (const qa of bundle.applicationAnswers) {
      if (!qa.question || !qa.answer) continue;
      const sensitiveQuestion = /\b(work authorization|authorized to work|legally authorized|sponsorship|sponsor|citizenship|visa|criminal history|background check|terms and conditions|attest|certify|declaration)\b/i.test(qa.question);
      if (sensitiveQuestion && !options.confirmLegalAnswers) continue;
      const qTokens = qa.question.toLowerCase().split(/\s+/).filter((t) => t.length > 3);

      for (const [, field] of Object.entries(detectedFields)) {
        if (assignedElements.has(field.element)) continue;

        const queryKey = [field.resolvedLabel, field.name, field.id, field.placeholder]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();

        const matchCount = qTokens.filter((token) => queryKey.includes(token)).length;
        if (matchCount >= 2 || (qTokens.length === 1 && matchCount === 1)) {
          assignments.push({
            element: field.element as any,
            value: qa.answer,
            fieldLabel: `Screening: ${qa.question}`,
            confidence: sensitiveQuestion ? 'UNCERTAIN' : 'HIGH',
            requiresCandidateConfirmation: sensitiveQuestion,
            matchReason: `Matched screening question tokens`
          });
          assignedElements.add(field.element);
          break;
        }
      }
    }
  }

  // Cover letter mapping to textarea
  if (bundle?.coverLetterText) {
    for (const [, field] of Object.entries(detectedFields)) {
      if (assignedElements.has(field.element)) continue;
      if (field.tag !== 'textarea') continue;

      const queryKey = [field.resolvedLabel, field.name, field.id, field.placeholder]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();

      if (queryKey.includes('cover') || queryKey.includes('letter') || queryKey.includes('additional') || queryKey.includes('comments')) {
        assignments.push({
          element: field.element as any,
          value: bundle.coverLetterText,
          fieldLabel: 'Cover Letter',
          confidence: 'HIGH',
          matchReason: 'Target textarea for cover letter / additional comments'
        });
        assignedElements.add(field.element);
        break;
      }
    }
  }

  return assignments;
}

function isHighConfidence(field: DetectedFieldInfo, aliases: string[]): boolean {
  const directName = (field.name + ' ' + field.id).toLowerCase();
  const label = field.resolvedLabel.toLowerCase();

  return aliases.some((a) => directName.includes(a) || label === a || label.startsWith(a));
}
