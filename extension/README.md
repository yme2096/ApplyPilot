# ApplyPilot Chrome Extension

The Manifest V3 extension connects to a local ApplyPilot API and helps prepare application fields. It never clicks the employer's submit button, bypasses CAPTCHA, or bypasses sign-in and access controls.

## Install locally

Prerequisites: Node.js 20 or later, a running ApplyPilot API at `http://localhost:8081`, and Chrome.

```powershell
cd C:\Users\hp\Downloads\ApplyPilot\extension
npm ci
npm test
npm run build
```

In Chrome, open `chrome://extensions`, enable Developer mode, choose **Load unpacked**, and select the `extension` folder. Reload the extension after rebuilding. On an otherwise unsupported public application page, click the extension action to grant Chrome's temporary `activeTab` access; it then injects the conservative generic-form detector for that tab only.

## Sign in and prepare one application

1. Sign in from the popup using your local ApplyPilot account. The access/refresh token pair is held only in `chrome.storage.session`; profile data is fetched when the popup opens and is not cached in extension storage. The password field is cleared after sign-in.
2. Prepare a job-specific bundle and selected resume version in the ApplyPilot app, then mark it `READY` after reviewing it.
3. Open the public application form, open the extension, select its READY bundle, and confirm that you reviewed the selected resume version. Legal-answer confirmation is a separate optional checkbox.
4. Choose **Prepare fields for final review**. Existing non-empty values are preserved; only exact supported profile mappings and bundle answers are filled. Profile skills are limited to records marked `verified`. Legal/work-authorization answers need explicit confirmation and are highlighted for review. Unknown questions remain for manual entry.
5. Review every filled value. To attach the approved version, use **Attach approved resume version**. The extension re-reads the READY bundle, verifies the exact resume ID/version, retrieves only its versioned PDF/DOCX endpoint, and attempts attachment to a single unfilled, unambiguous resume input. Confirm the filename and version in the employer page. If the portal rejects script-provided files or the upload control is ambiguous, attach that exact version manually. This behavior is automated-tested but not verified in live Chrome yet.
6. Manually submit on the employer website. Then check the popup confirmation and record the submission. Autofill alone only changes the bundle to `IN_REVIEW`; it never records submission.

## Prepare several jobs

In ApplyPilot's Application Bundles page, select multiple saved jobs that do not already have a bundle. Choose a resume document and an exact existing version for every job, then create the separate DRAFT bundles. In the consolidated review, edit each job's own answer list, cover note, and notes. Check the approval box separately for each selected resume version and save the reviewed jobs as `READY`. The batch ID persists, so unapproved drafts reappear after reload. A failed save can be retried without creating a second bundle. Cancel removes only the unapproved draft bundles; saved jobs and resume versions remain. Open each employer application separately and use the extension on that page. The bulk workflow does not tailor resumes automatically, generate answers automatically, open portals, or submit forms.

The mapper's green highlight means a field label matched a saved candidate record. It is not proof that the underlying profile statement was independently verified. Yellow means the mapping or sensitive answer needs extra review.

## Detection scope

Implemented DOM selectors cover common patterns associated with Greenhouse, Lever, Ashby, Workday and generic forms. Twenty automated tests cover ATS selector recognition, mapping, unknown/missing values, sensitive questions, preservation, duplicate suppression, exact bundle/resume/version matching, simulated successful attachment, portal refusal, preserving an already selected file, unsupported upload controls, and non-submission behavior. These are DOM-level simulations and do not certify Chrome or live portal compatibility. Dynamic flows, live portal behavior, and installation in Chrome remain **NOT TESTED**. Portal interfaces change, so always inspect the final form yourself.

## Boundaries and known gaps

- No employer form submit, CAPTCHA interaction, employer-site login automation, access-control bypass, or anti-bot evasion.
- Resume attachment is a candidate-triggered attempt. It validates the versioned API URL, PDF/DOCX type, file size, and a single unfilled resume field. Real Chrome and portal acceptance are **NOT TESTED**; manual attachment is the fallback.
- Bulk job preparation and consolidated review are available in the ApplyPilot web application's Application Bundles page. The extension operates on one selected READY bundle and one active employer form at a time.
- Duplicate guard: READY bundles are hidden when their job already has a candidate-confirmed `SUBMITTED_BY_CANDIDATE` bundle, and duplicate READY bundles for the same job are collapsed. This cannot detect submissions made outside ApplyPilot.
- Submission state changes only after the candidate confirms they manually submitted; there is no reliable employer-side submission confirmation integration.
- Browser session token storage is session-scoped; expiration requires signing in again. No secrets or candidate fields are logged.

## Manual Chrome verification checklist

Chrome popup, sign-in, extension-to-API connection, session expiry, Greenhouse detection, and actual browser file attachment are **NOT TESTED** by the development run. Use this checklist when you verify them manually:

1. Confirm ApplyPilot web is available at `http://localhost:5174` and API at `http://localhost:8081`; in Chrome load the unpacked `extension` folder from `chrome://extensions`.
2. Open the extension popup. Sign in to the local ApplyPilot account and confirm the signed-in account, saved bundles, and active-page status load. The popup password field should clear after sign-in.
3. On a permitted test application form, confirm supported form detection, known field mapping, preservation of prefilled values, and that only verified skills are included. Unknown fields must remain untouched. Legal/work-authorization answers should require the separate confirmation.
4. Select a READY bundle, approve the exact resume version, and use the attachment action only on a controlled test form or sandbox. Confirm the selected filename/version in that form. If a file is already selected, verify ApplyPilot leaves it unchanged; with ambiguous/unsupported upload inputs, confirm it shows the manual-attachment fallback.
5. For session-expiry recovery, leave the popup session unused past the configured access-token lifetime (`JWT_ACCESS_MINUTES`, default 15), reopen/reload the popup, confirm it asks for sign-in after the API returns 401, then sign in again and verify profile/bundles reload.
6. Verify autofill changes the bundle to `IN_REVIEW`; confirm the extension has no employer Submit action. Do not submit an employer form as part of this check.

Prefer a local/sandbox form and synthetic data. Do not enter or upload candidate data to a real employer portal just to test the extension. Record each manual result separately from the automated Vitest/jsdom results.

## Test and build

```powershell
npm test
npm run build
```
