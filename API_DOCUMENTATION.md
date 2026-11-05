# REST API

Base path: `/api`. JSON is UTF-8. Authenticated endpoints require `Authorization: Bearer <access token>`. API errors use a consistent `{ "code": "...", "message": "..." }` body.

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Anonymous readiness check for PostgreSQL, Redis, and document worker; returns only `{ "status": "UP" }` or `{ "status": "DOWN" }` with HTTP 200 or 503. |
| POST | `/auth/register` | Create account and issue token pair |
| POST | `/auth/login` | Authenticate and issue token pair |
| POST | `/auth/refresh` | Rotate refresh token |
| POST | `/auth/logout` | Revoke refresh token |
| POST | `/auth/password-reset/request` | Send a short-lived reset link. Returns `202` with a generic response when SMTP is configured; returns controlled `503 SERVICE_UNAVAILABLE` when SMTP is not configured. |
| POST | `/auth/password-reset/confirm` | Consume a single-use reset token, change password, and revoke refresh tokens |
| GET/PUT | `/profile` | Read/update candidate profile |
| GET/POST | `/profile/records/{education\|skills\|projects\|experience\|certifications\|achievements}` | List/create candidate-owned records |
| PUT/DELETE | `/profile/records/{kind}/{id}` | Update/delete a candidate-owned record |
| GET/POST | `/resumes` | List/upload original resume |
| GET | `/resumes/{id}/versions` | List immutable versions |
| POST | `/resumes/{id}/versions/{version}/review` | Save candidate corrections as a new version |
| POST | `/resumes/{id}/versions/{version}/tailor` | Tailor resume for a target job role and description |
| POST | `/resumes/{id}/versions/{version}/export?format=pdf\|docx` | Generate and persist a document export version |
| GET | `/resumes/{id}/versions/{version}/download` | Download the exact stored version document |
| GET | `/jobs` | List user's saved opportunities (supports optional `?status=...` query) |
| POST | `/jobs` | Save opportunity with automatic match score calculation |
| GET | `/jobs/{id}` | Read single saved job details |
| GET | `/jobs/{id}/match` | Retrieve detailed explainable match breakdown (matching/missing reqs, eligibility, relevant projects) |
| POST | `/jobs/analyze` | On-the-fly match score analysis for a role before saving |
| GET | `/jobs/{id}/questions` | Return Ollama-generated role questions when configured; otherwise return deterministic prompts from saved role, skills, and eligibility terms |
| PATCH | `/jobs/{id}` | Update job details, notes, or status (`saved`, `applied`, `interviewing`, `offered`, `rejected`, `withdrawn`) |
| DELETE | `/jobs/{id}` | Remove saved job opportunity |
| POST | `/jobs/import/greenhouse` | Ingest jobs from public Greenhouse board token or posting URL |
| POST | `/jobs/import/lever` | Ingest postings from public Lever site or posting URL |
| POST | `/jobs/import/rss` | Ingest from a public RSS / Atom feed; XML is DTD-disabled, capped at 2 MB / 500 entries, and common RSS/Atom publication dates are retained |
| POST | `/jobs/import/url` | Ingest job details via structured JSON-LD or OpenGraph web data |
| POST | `/jobs/company-research` | Retrieve only Organization JSON-LD or OpenGraph company facts from a supplied public company page; each returned fact includes its source URL and retrieval time, and absent facts remain unavailable |
| GET | `/jobs/import/logs` | Retrieve candidate ingestion history and error audit log |
| GET | `/bundles` | List candidate application bundles (optional `?jobId=...` or `?status=...`) |
| POST | `/bundles` | Prepare job-specific application bundle (links job, tailored resume, screening Q&A) |
| POST | `/bundles/bulk` | Atomically create 1–25 separate DRAFT bundles for distinct saved jobs; every item must name an exact owned resume version and jobs with an existing bundle are rejected with 409 |
| GET | `/bundles/{id}` | Retrieve application bundle details with resume download link |
| PATCH | `/bundles/{id}` | Update bundle status (`DRAFT`, `READY`, `IN_REVIEW`, `SUBMITTED_BY_CANDIDATE`), notes, or answers |
| POST | `/bundles/{id}/answers` | Add or update custom screening Q&A |
| DELETE | `/bundles/{id}` | Remove application bundle |
| GET/POST | `/applications` | List or create one unique candidate-owned application tracker per saved job; optional bundle creation copies exact resume ID/version and application answer snapshot |
| PATCH | `/applications/{id}` | Update tracked status/notes; APPLIED requires `candidateConfirmedApplied: true` |
| GET | `/applications/dashboard` | Return record-derived status counts, weekly/monthly creation activity, interview/offer/rejection counts and rates |
| GET/POST | `/applications/{id}/followups` | List or create per-application follow-up reminders with due date, reminder toggle and note |
| GET | `/followups` | List overdue, upcoming and completed follow-ups |
| PATCH | `/followups/{id}` | Edit an owned follow-up or mark it complete/reopen it |
| GET/PATCH | `/applications/{id}/interview-prep` | Get deterministic job-specific interview question prompts or save candidate-edited answers |
| POST | `/applications/{id}/interview-prep/draft` | Request an optional Ollama evidence-grounded answer draft; returns controlled unavailable/unsupported errors, with evidence references; draft is not saved automatically |
| GET | `/jobs/{jobId}/skill-gaps` | Compare saved role requirements with verified candidate skills and retrieve learning progress |
| PUT | `/jobs/{jobId}/skill-gaps/{skill}` | Update candidate learning progress (NOT_STARTED, IN_PROGRESS, PRACTICED, ADDRESSED) |
| GET | `/notifications` | List due and unscheduled in-app notifications for the signed-in candidate |
| PATCH | `/notifications/{id}/read` | Mark an owned in-app notification as read |
| GET/PUT | `/notification-preferences` | Read or update global in-app reminder preferences (follow-up, interview, preparation, status) |

## Abuse protection and runtime security

Public `POST /auth/register`, `/auth/login`, and `/auth/password-reset/request` use a Redis fixed-window counter. Public job import/analyze/company-research POST routes use a separate ingestion limit; `POST /answer-library/draft` and `/applications/{id}/interview-prep/draft` use an AI limit. Defaults are 20 auth, 30 ingestion, and 10 AI requests per 60 seconds per direct peer address. Configure with `APP_RATE_LIMIT_AUTH_MAX`, `APP_RATE_LIMIT_IMPORT_MAX`, `APP_RATE_LIMIT_AI_MAX`, and `APP_RATE_LIMIT_WINDOW_SECONDS`. Exceeding a limit returns HTTP `429`, JSON code `RATE_LIMITED`, and `Retry-After`. Forwarded IP headers are ignored. Redis connect and command timeouts default to 2 seconds (`REDIS_CONNECT_TIMEOUT`, `REDIS_COMMAND_TIMEOUT`); missing/unavailable Redis fails closed with HTTP `503`, code `RATE_LIMITER_UNAVAILABLE`, and `Retry-After: 5`.

The local Nginx frontend supplies a Content Security Policy and standard browser security headers. The web client uses same-origin `/api` proxying. `GET /api/health` checks PostgreSQL, Redis, and worker readiness and returns no service details. Checks run concurrently with a bounded 4-second deadline; the database pool connection timeout defaults to 3 seconds (`DB_CONNECTION_TIMEOUT_MS`). The resume parser uses a 3-second worker connect timeout and 45-second request timeout, configurable as `APP_WORKER_CONNECT_TIMEOUT_SECONDS` and `APP_WORKER_REQUEST_TIMEOUT_SECONDS` in Compose.
| GET/POST | `/answer-library` | List or save candidate-authored reusable answers; save requires an exact excerpt from the candidate's own resume version or profile record |
| POST | `/answer-library/draft` | Ask the configured local Ollama model for an evidence-grounded draft; does not save it, and returns a controlled 503 if local AI is disabled/unavailable |
| PUT/DELETE | `/answer-library/{id}` | Update or delete a candidate-owned answer |

Answer-library writes require one or more evidence references. Resume evidence must cite an owned immutable resume version and an excerpt found in its extracted text; profile evidence must cite an owned record of a supported kind and an excerpt found in that record. The API rejects excerpts that cannot be verified. AI drafts return source type, source ID, version or profile record kind, and the exact supporting excerpt. A conservative lexical overlap check rejects drafts whose factual vocabulary diverges from the supplied evidence; this is a screening guard, not a semantic fact verifier, so candidates must review every claim. Drafts are never persisted automatically. Ollama-generated job prompts fall back to deterministic templates if local AI is disabled or unavailable.

Job match responses include `eligibilityStatus` (`ELIGIBLE`, `NOT_ELIGIBLE`, or `UNKNOWN`). The analyzer only returns a positive or negative result for an explicit graduation-year range that can be compared to the candidate's saved graduation year. Enrollment, work authorization, degree equivalence, experience-duration, and unstated requirements stay `UNKNOWN` until candidate review.

Further contracts are documented alongside domain implementation.

Phase 4 tracking schema is introduced by forward-only V10/V11 (**APPLIED / PASS**, Flyway version 11). Application records are unique per user and saved job. Creation from a preparation bundle takes an immutable copy of its exact resume ID/version and answer JSON; later bundle edits do not rewrite the snapshot. `APPLIED` requires explicit candidate confirmation. Dashboard metrics are calculated from stored records/statuses. Interview prompts are deterministic; optional answer drafts use the existing local Ollama abstraction, cite supplied resume evidence, and remain editable and unsaved until the candidate saves them. Reminder preferences and interview scheduling are configurable; notifications remain local. Live Ollama generation is **BLOCKED / NOT TESTED** while the local provider/model is unavailable.

Dashboard clients request applications, summary metrics, saved jobs, follow-ups, notifications, and notification preferences independently. A failure in one section should be shown as unavailable with retry instead of being displayed as an empty result or zero metric. Interview answer updates are candidate-authored `PATCH /applications/{id}/interview-prep` writes; generated drafts from `POST /applications/{id}/interview-prep/draft` are not persisted until the candidate explicitly saves the edited answer. Follow-up and interview notifications honor the signed-in user's preferences.

Security review notes: API authentication remains bearer-token based and CORS uses the configured exact origin. Web tokens are tab-scoped in `sessionStorage`; reset tokens use a URL fragment. Public/expensive route limits fail closed when Redis is unavailable. `/api/health` checks DB, Redis, and worker. Authenticated browser journeys and failure-recovery checks passed in the isolated synthetic stack; see `PHASE_TEST_REPORT.md`. These checks do not establish production readiness.

The MV3 browser extension authenticates with `/auth/login`, reads the signed-in user's `/profile` and `/profile/records/{kind}`, lists only that user's `/bundles`, and updates bundle review/submission status through the owner-checked `PATCH /bundles/{id}` route. Tokens are held in `chrome.storage.session` by the extension and cleared on sign-out/session expiry; profile records are fetched on demand and not cached in extension storage. The extension can record `SUBMITTED_BY_CANDIDATE` only after the candidate explicitly confirms a manual submission. The API does not independently verify employer submission, and autofill is never treated as submission.

Bulk preparation accepts `{ "items": [{ "jobId": "…", "resumeId": "…", "resumeVersionNumber": 1, "applicationAnswers": [] }] }`. It creates one DRAFT bundle per job under a shared `preparationBatchId`; the whole request rolls back if any job is duplicated, already has a bundle, is not owned by the caller, or lacks an exact owned resume version. PATCH each job-specific draft with its corrected notes and answers and `status: READY` plus `resumeVersionApproved: true` only after candidate approval of that specific version. The API rejects a batch bundle's READY transition without that acknowledgement. Repeated submission of the same batch returns 409 instead of duplicating preparations. Bulk groups persist across reloads. Employer forms remain opened and autofilled one at a time using the extension; the batch API does not submit applications.

For attachment, the extension re-reads `/bundles` and accepts only a `READY` bundle with a `resumeId`, integer `resumeVersionNumber`, and exactly matching `/api/resumes/{id}/versions/{version}/download` path. It then uses the authenticated, owner-checked version download endpoint. The extension restricts files to PDF/DOCX and a 15 MB limit; it does not use the unversioned resume download route. Chrome file-input behavior remains portal-dependent and live verification is pending.

Password-reset requests do not disclose whether an email is registered. When SMTP is configured, unknown addresses receive the same accepted response as known addresses; a transient SMTP delivery failure is swallowed without persisting a token. When SMTP configuration is absent, the API returns the same controlled `503` before looking up the address.

The reset email places the one-time token in the URL fragment (`/#resetToken=...`), so the fragment is not sent in the initial HTTP request. After a successful password update, the frontend removes the fragment from browser history. Reset-link email delivery and address-bar behavior were not independently exercised in the Phase 5 Playwright run.
# Certification Vault (V12)

All routes below require an authenticated candidate session and scope records to that candidate. V12 must be applied before these routes are available.

- `GET /api/certifications?q=cloud&status=ACTIVE` lists/searches records; statuses are `ACTIVE`, `EXPIRING_SOON`, `EXPIRED`, `NO_EXPIRY`.
- `GET /api/certifications/{id}` returns one owned record.
- `POST /api/certifications` and `PUT /api/certifications/{id}` accept JSON fields `name`, `issuer`, `issueDate`, `expirationDate` (nullable), `credentialId`, `officialVerificationUrl`, `credlyBadgeUrl`, `scoreResult`, `candidateVerified`, and `reminderDays` (default `[90,30,7]`). URLs must be HTTPS. Dates are ISO `YYYY-MM-DD`; expiry cannot precede issue date.
- `DELETE /api/certifications/{id}` archives the record. Certificate files and metadata are retained privately.
- `POST /api/certifications/{id}/file` accepts multipart field `file`, PDF only, up to 10 MiB. The service checks PDF signature and EOF marker, SHA-256, and an injectable malware scanner hook. The default scanner is `NOT_CONFIGURED`; this is not a malware-clean verdict.
- `GET /api/certifications/{id}/file` returns the private PDF with an integrity header. No public storage URL is exposed.

Verification links are candidate-provided; ApplyPilot does not verify issuers or query/scrape Credly. Certification data selected for resume exports or preparation bundles is copied into an evidence-referenced snapshot and requires explicit candidate approval before a bundle advances to ready/submission-prepared status. Certificate PDFs are never attached to an employer submission automatically. Expiration reminder days are configurable per record, deduplicated by certification and interval, and can be disabled through the existing `PUT /api/notifications/preferences` `certificationReminders` setting.
