import { detectAtsForm } from './detector';
import { mapCandidateFieldsToForm } from './field-mapper';
import { CandidateProfile, ApplicationBundleData, AutofillResult, MappedFieldAssignment } from '../types';

export function executeAutofill(
  profile: CandidateProfile,
  bundle?: ApplicationBundleData,
  rootDoc: Document = document,
  options: { confirmLegalAnswers?: boolean } = {}
): AutofillResult {
  const detected = detectAtsForm(rootDoc);
  if (!detected.isApplicationPage) {
    return {
      filledCount: 0,
      uncertainCount: 0,
      labels: [],
      resumeFieldDetected: false,
      hasAmbiguousFields: false,
      unsupportedCount: 0
    };
  }

  const assignments = mapCandidateFieldsToForm(detected.detectedFields, profile, bundle, options);
  const filledLabels: string[] = [];
  let uncertainCount = 0;

  for (const item of assignments) {
    if (hasUserValue(item.element)) continue;
    const success = applyValueToElement(item);
    if (success) {
      filledLabels.push(item.fieldLabel);
      if (item.confidence === 'UNCERTAIN') {
        uncertainCount++;
      }
    }
  }

  const detectedCount = Object.keys(detected.detectedFields).length;
  const unsupportedCount = Math.max(0, detectedCount - filledLabels.length);

  // Record that the candidate entered review. Filling never marks a job as submitted/applied.
  if (bundle?.id && typeof chrome !== 'undefined' && chrome.runtime?.sendMessage) {
    try {
      chrome.runtime.sendMessage({
        type: 'SYNC_BUNDLE_STATUS',
        bundleId: bundle.id,
        status: 'IN_REVIEW'
      });
    } catch {
      // Runtime might be disconnected in fixture tests
    }
  }

  return {
    filledCount: filledLabels.length,
    uncertainCount,
    labels: filledLabels,
    resumeFieldDetected: detected.hasResumeUpload,
    hasAmbiguousFields: uncertainCount > 0,
    unsupportedCount
  };
}

function hasUserValue(element: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement): boolean {
  if (element instanceof HTMLInputElement && ['checkbox', 'radio', 'file'].includes(element.type)) {
    return element.checked || (element.type === 'file' && element.files != null && element.files.length > 0);
  }
  if (element instanceof HTMLSelectElement) {
    const selected = element.selectedOptions[0];
    return Boolean(element.value && selected && !/^(choose|select|--)/i.test(selected.text.trim()));
  }
  return Boolean(element.value.trim());
}

export function applyValueToElement(item: MappedFieldAssignment): boolean {
  const el = item.element;
  if (hasUserValue(el)) return false;
  const isHigh = item.confidence === 'HIGH';

  // 1. Text Inputs and Textareas
  if (el instanceof HTMLInputElement && ['text', 'email', 'tel', 'url', 'number', 'date'].includes(el.type)) {
    setValueWithReactSupport(el, String(item.value));
    applyVisualFeedback(el, isHigh, item.fieldLabel);
    return true;
  }

  if (el instanceof HTMLTextAreaElement) {
    setValueWithReactSupport(el, String(item.value));
    applyVisualFeedback(el, isHigh, item.fieldLabel);
    return true;
  }

  // 2. Select Dropdowns
  if (el instanceof HTMLSelectElement) {
    const targetVal = String(item.value).toLowerCase().trim();
    let matchedIndex = -1;

    for (let i = 0; i < el.options.length; i++) {
      const opt = el.options[i];
      const optVal = opt.value.toLowerCase().trim();
      const optText = opt.text.toLowerCase().trim();

      // Exact match
      if (optVal === targetVal || optText === targetVal) {
        matchedIndex = i;
        break;
      }

      // Boolean synonyms (Yes/No, True/False)
      if (typeof item.value === 'boolean') {
        if (item.value && (optVal === 'yes' || optVal === 'true' || optVal === '1' || optText === 'yes' || optText.includes('authorized'))) {
          matchedIndex = i;
          break;
        }
        if (!item.value && (optVal === 'no' || optVal === 'false' || optVal === '0' || optText === 'no' || optText.includes('sponsorship'))) {
          matchedIndex = i;
          break;
        }
      }

      // Substring match
      if (optText.includes(targetVal) || (targetVal.length > 4 && targetVal.includes(optText))) {
        matchedIndex = i;
      }
    }

    if (matchedIndex !== -1) {
      el.selectedIndex = matchedIndex;
      el.dispatchEvent(new Event('change', { bubbles: true }));
      applyVisualFeedback(el, isHigh, item.fieldLabel);
      return true;
    }
  }

  // 3. Radio Buttons
  if (el instanceof HTMLInputElement && el.type === 'radio') {
    const isBool = typeof item.value === 'boolean';
    const isYes = item.value === true || String(item.value).toLowerCase() === 'yes';

    // Find sibling radio buttons in same group
    const form = el.form || el.ownerDocument;
    const groupRadios = form.querySelectorAll<HTMLInputElement>(`input[type="radio"][name="${CSS.escape(el.name)}"]`);

    for (const radio of groupRadios) {
      const radioVal = radio.value.toLowerCase();
      const radioLabel = (radio.getAttribute('aria-label') || radio.closest('label')?.textContent || '').toLowerCase();

      if (isBool) {
        if (isYes && (radioVal === 'yes' || radioVal === 'true' || radioVal === '1' || radioLabel.includes('yes'))) {
          radio.checked = true;
          radio.dispatchEvent(new Event('change', { bubbles: true }));
          radio.dispatchEvent(new Event('click', { bubbles: true }));
          applyVisualFeedback(radio, isHigh, item.fieldLabel);
          return true;
        }
        if (!isYes && (radioVal === 'no' || radioVal === 'false' || radioVal === '0' || radioLabel.includes('no'))) {
          radio.checked = true;
          radio.dispatchEvent(new Event('change', { bubbles: true }));
          radio.dispatchEvent(new Event('click', { bubbles: true }));
          applyVisualFeedback(radio, isHigh, item.fieldLabel);
          return true;
        }
      }
    }
  }

  // 4. Checkboxes
  if (el instanceof HTMLInputElement && el.type === 'checkbox') {
    const shouldCheck = typeof item.value === 'boolean' ? item.value : String(item.value).toLowerCase() === 'true';
    el.checked = shouldCheck;
    el.dispatchEvent(new Event('change', { bubbles: true }));
    applyVisualFeedback(el, isHigh, item.fieldLabel);
    return true;
  }

  return false;
}

function setValueWithReactSupport(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  element.focus();

  // Try prototype setter to notify React's synthetic event tracker
  const prototype = element instanceof HTMLInputElement ? window.HTMLInputElement.prototype : window.HTMLTextAreaElement.prototype;
  const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
  if (descriptor && descriptor.set) {
    descriptor.set.call(element, value);
  } else {
    element.value = value;
  }

  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
  element.blur();
}

function applyVisualFeedback(element: HTMLElement, isHighConfidence: boolean, fieldLabel: string) {
  if (isHighConfidence) {
    element.style.outline = '2px solid #22c55e';
    element.style.backgroundColor = '#f0fdf4';
  } else {
    element.style.outline = '2px solid #eab308';
    element.style.backgroundColor = '#fefce8';
  }
  element.style.transition = 'outline 0.3s ease, background-color 0.3s ease';
  element.setAttribute('data-applypilot-filled', isHighConfidence ? 'matched-review' : 'uncertain-review');
  element.setAttribute('title', `ApplyPilot: ${fieldLabel} (${isHighConfidence ? 'Mapped from saved profile — review' : 'Needs candidate review'})`);
}

// Chrome content script listener
if (typeof chrome !== 'undefined' && chrome.runtime?.onMessage) {
  chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
    if (msg.type === 'ATTACH_APPROVED_RESUME') {
      try {
        const result = attachApprovedResume(msg);
        sendResponse(result);
      } catch {
        sendResponse({ success: false, error: 'The employer form rejected the resume attachment. Attach the approved file manually.' });
      }
      return true;
    }
    if (msg.type === 'DETECT_PAGE') {
      const d = detectAtsForm();
      sendResponse({
        detected: d.isApplicationPage,
        ats: d.ats,
        jobTitle: d.jobTitle,
        company: d.companyName,
        fieldsCount: Object.keys(d.detectedFields).length,
        hasResumeUpload: d.hasResumeUpload
      });
      return true;
    }

    if (msg.type === 'TRIGGER_AUTOFILL') {
      const res = executeAutofill(msg.profile, msg.bundle, document, { confirmLegalAnswers: msg.confirmLegalAnswers === true });
      sendResponse({
        success: true,
        filledCount: res.filledCount,
        uncertainCount: res.uncertainCount,
        fields: res.labels,
        hasResumeUpload: res.resumeFieldDetected,
        unsupportedCount: res.unsupportedCount
      });
      return true;
    }
  });
}

export function attachApprovedResume(msg: { bytes?: ArrayBuffer; filename?: string; mimeType?: string; version?: number }): { success: boolean; filename?: string; version?: number; error?: string } {
  if (!(msg.bytes instanceof ArrayBuffer) || !msg.bytes.byteLength || msg.bytes.byteLength > 15 * 1024 * 1024 || !Number.isInteger(msg.version)) {
    return { success: false, error: 'The selected resume file could not be verified. Attach it manually.' };
  }
  if (!['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'].includes(msg.mimeType || '')) {
    return { success: false, error: 'Only verified PDF and DOCX resume versions can be attached.' };
  }
  const inputs = Array.from(document.querySelectorAll<HTMLInputElement>('input[type="file"]'));
  const resumeInputs = inputs.filter((input) => /resume|cv/i.test(`${input.name} ${input.id} ${input.getAttribute('aria-label') || ''} ${input.getAttribute('accept') || ''}`));
  const input = resumeInputs.length === 1 ? resumeInputs[0] : resumeInputs.length === 0 && inputs.length === 1 ? inputs[0] : undefined;
  if (!input) return { success: false, error: inputs.length ? 'Resume upload control is ambiguous or unsupported. Attach the file manually.' : 'No supported resume upload control was found on this page.' };
  if (input.files?.length) return { success: false, error: 'A file is already selected. ApplyPilot preserved it; review or replace it manually.' };
  const filename = String(msg.filename || `resume-v${msg.version}.${msg.mimeType === 'application/pdf' ? 'pdf' : 'docx'}`).replace(/[\\/\r\n"]/g, '_').slice(0, 120);
  try {
    const file = new File([msg.bytes], filename, { type: msg.mimeType });
    const transfer = new DataTransfer();
    transfer.items.add(file);
    input.files = transfer.files;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    const attached = input.files?.length === 1 && input.files[0].name === filename && input.files[0].size === msg.bytes.byteLength;
    return attached ? { success: true, filename, version: msg.version } : { success: false, error: 'The portal did not confirm the selected resume. Attach it manually.' };
  } catch {
    return { success: false, error: 'This portal does not permit programmatic file attachment. Attach the approved version manually.' };
  }
}
