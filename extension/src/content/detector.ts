import { AtsType, DetectedForm, DetectedFieldInfo } from '../types';

export function detectAtsForm(rootDoc: Document = document): DetectedForm {
  const url = (typeof window !== 'undefined' ? window.location.href : '').toLowerCase();

  // 1. Greenhouse Detection
  if (url.includes('greenhouse.io') || rootDoc.querySelector('form#application_form, #apply_app, [id*="job_application"]')) {
    const form = rootDoc.querySelector<HTMLFormElement>('form#application_form, form[action*="greenhouse"], form');
    const catalog = catalogFields(form || rootDoc.body);
    const resumeEl = rootDoc.querySelector<HTMLInputElement>('#resume_file, input[type="file"][name*="resume" i], input[type="file"]');

    return {
      ats: 'greenhouse',
      formElement: form,
      detectedFields: catalog,
      isApplicationPage: Boolean(form || rootDoc.querySelector('#first_name, #last_name, #email')),
      hasResumeUpload: Boolean(resumeEl),
      resumeInputElement: resumeEl,
      jobTitle: rootDoc.querySelector('.app-title, h1.job-title, h1')?.textContent?.trim(),
      companyName: rootDoc.querySelector('.company-name, .logo-container img')?.textContent?.trim()
    };
  }

  // 2. Lever Detection
  if (url.includes('lever.co') || rootDoc.querySelector('form#application-form, .posting-page, .application-form')) {
    const form = rootDoc.querySelector<HTMLFormElement>('form#application-form, form[action*="lever.co"], form');
    const catalog = catalogFields(form || rootDoc.body);
    const resumeEl = rootDoc.querySelector<HTMLInputElement>('input[type="file"][name="resume"], input[type="file"]');

    return {
      ats: 'lever',
      formElement: form,
      detectedFields: catalog,
      isApplicationPage: Boolean(form || rootDoc.querySelector('input[name="name"], input[name="email"]')),
      hasResumeUpload: Boolean(resumeEl),
      resumeInputElement: resumeEl,
      jobTitle: rootDoc.querySelector('.posting-headline h2, h2')?.textContent?.trim(),
      companyName: rootDoc.querySelector('.main-header-logo img')?.getAttribute('alt') || 'Employer'
    };
  }

  // 3. Ashby Detection
  if (url.includes('ashbyhq.com') || rootDoc.querySelector('[data-test="application-form"], .ashby-application-form')) {
    const form = rootDoc.querySelector<HTMLFormElement>('form[data-test="application-form"], form');
    const catalog = catalogFields(form || rootDoc.body);
    const resumeEl = rootDoc.querySelector<HTMLInputElement>('input[type="file"]');

    return {
      ats: 'ashby',
      formElement: form,
      detectedFields: catalog,
      isApplicationPage: true,
      hasResumeUpload: Boolean(resumeEl),
      resumeInputElement: resumeEl,
      jobTitle: rootDoc.querySelector('h1')?.textContent?.trim()
    };
  }

  // 4. Workday Detection
  if (url.includes('myworkdayjobs.com') || rootDoc.querySelector('[data-automation-id="applyFlow"]')) {
    const form = rootDoc.querySelector<HTMLFormElement>('form, [data-automation-id="applyFlow"]');
    const catalog = catalogFields(form || rootDoc.body);
    const resumeEl = rootDoc.querySelector<HTMLInputElement>('input[type="file"]');

    return {
      ats: 'workday',
      formElement: form as HTMLFormElement,
      detectedFields: catalog,
      isApplicationPage: true,
      hasResumeUpload: Boolean(resumeEl),
      resumeInputElement: resumeEl,
      jobTitle: rootDoc.querySelector('[data-automation-id="jobPostingHeader"]')?.textContent?.trim()
    };
  }

  // 5. Generic Form Heuristic
  const genericForm = rootDoc.querySelector<HTMLFormElement>('form');
  const catalog = catalogFields(genericForm || rootDoc.body);
  const resumeEl = rootDoc.querySelector<HTMLInputElement>('input[type="file"]');
  const hasJobInputs = Boolean(
    rootDoc.querySelector('input[type="email"], input[name*="resume" i], input[type="file"]')
  );

  return {
    ats: 'generic',
    formElement: genericForm,
    detectedFields: catalog,
    isApplicationPage: hasJobInputs,
    hasResumeUpload: Boolean(resumeEl),
    resumeInputElement: resumeEl,
    jobTitle: rootDoc.querySelector('h1, h2')?.textContent?.trim()
  };
}

export function catalogFields(container: HTMLElement): Record<string, DetectedFieldInfo> {
  const fields: Record<string, DetectedFieldInfo> = {};
  const inputs = container.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(
    'input, textarea, select'
  );

  inputs.forEach((input) => {
    // Exclude hidden, password, submit, button, search, or disabled fields
    const type = (input.getAttribute('type') || input.tagName.toLowerCase()).toLowerCase();
    if (['hidden', 'password', 'submit', 'button', 'reset', 'search'].includes(type)) {
      return;
    }
    if (input.disabled || ('readOnly' in input && input.readOnly)) {
      return;
    }

    const name = (input.getAttribute('name') || '').trim();
    const id = (input.getAttribute('id') || '').trim();
    const placeholder = (input.getAttribute('placeholder') || '').trim();
    const ariaLabel = (input.getAttribute('aria-label') || '').trim();
    const resolvedLabel = resolveLabelText(input);

    const compoundKey = [resolvedLabel, name, id, placeholder, ariaLabel]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();

    if (!compoundKey) return;

    let optionsList: Array<{ value: string; text: string }> | undefined;
    if (input instanceof HTMLSelectElement) {
      optionsList = Array.from(input.options).map((opt) => ({
        value: opt.value,
        text: opt.text.trim()
      }));
    }

    // Key by unique identifier or position
    const fieldId = id || name || `field_${Object.keys(fields).length}`;
    fields[fieldId] = {
      element: input,
      tag: input.tagName.toLowerCase(),
      type,
      resolvedLabel,
      name,
      id,
      placeholder,
      ariaLabel,
      options: optionsList
    };
  });

  return fields;
}

export function resolveLabelText(element: HTMLElement): string {
  // 1. Label by 'for' attribute matching element id
  if (element.id && typeof document !== 'undefined') {
    const labelFor = Array.from(element.ownerDocument.querySelectorAll('label')).find((label) => label.htmlFor === element.id);
    if (labelFor && labelFor.textContent) {
      return cleanLabel(labelFor.textContent);
    }
  }

  // 2. Parent label tag enclosing element
  const parentLabel = element.closest('label');
  if (parentLabel && parentLabel.textContent) {
    return cleanLabel(parentLabel.textContent);
  }

  // 3. aria-labelledby
  const labelledBy = element.getAttribute('aria-labelledby');
  if (labelledBy && typeof document !== 'undefined') {
    const labelEl = element.ownerDocument.getElementById(labelledBy);
    if (labelEl && labelEl.textContent) {
      return cleanLabel(labelEl.textContent);
    }
  }

  // 4. aria-label
  const ariaLabel = element.getAttribute('aria-label');
  if (ariaLabel) {
    return cleanLabel(ariaLabel);
  }

  // 5. Preceding sibling or parent container label (e.g. .field-label, dt, legend)
  const container = element.closest('.field, .form-group, .input-group, div, fieldset');
  if (container) {
    const label = container.querySelector('label, .field-label, legend, dt');
    if (label && label.textContent) {
      return cleanLabel(label.textContent);
    }
  }

  // 6. Placeholder
  const placeholder = element.getAttribute('placeholder');
  if (placeholder) {
    return cleanLabel(placeholder);
  }

  return '';
}

function cleanLabel(text: string): string {
  return text
    .replace(/[*:\n\r\t]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}
