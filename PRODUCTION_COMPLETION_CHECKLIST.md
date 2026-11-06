# ApplyPilot Production Completion Checklist

This is the launch-gate matrix for the production-completion mission. **PASS** means the named acceptance criteria have direct verification evidence; source code or unit tests alone do not establish a live/deployed pass. Statuses are current to 2026-11-06. No primary database or Docker volume was changed during this continuation.

## Product acceptance

| Area | Acceptance criteria | Current status | Evidence / remaining gate |
|---|---|---|---|
| Authentication and recovery | Registration/login; refresh rotation/revocation; ownership on protected routes; reset token single-use, expiry and non-enumerating responses | IMPLEMENTED / PASS (automated) | Backend integration/unit coverage in `PHASE_TEST_REPORT.md`; live SMTP delivery remains BLOCKED on provider configuration. |
| Candidate profile and education | Authenticated CRUD; validation; data isolated by owner; accessible UI | IMPLEMENTED / PASS (API + tests) | Prior local API and automated ownership evidence; browser visual/accessibility acceptance not complete. |
| Resume lifecycle | PDF/DOCX upload, content checks, parsing, review/correction, immutable versions, download/export, ownership | IMPLEMENTED / PASS (automated + synthetic local smoke) | Worker parsing and synthetic resume round-trip passed. Full document-volume restore and production storage behavior remain NOT TESTED. |
| Job discovery/import | Permitted public imports, safe redirects/host controls, RSS/Atom, duplicate handling, provenance, manual fallback | IMPLEMENTED / PASS (tests + live local imports) | Live Greenhouse and RSS evidence in `PHASE_TEST_REPORT.md`; no CAPTCHA/auth/anti-bot bypass. |
| Matching and eligibility | Explainable matched/missing requirements; verified skills only; ambiguous eligibility remains UNKNOWN | IMPLEMENTED / PASS (tests + synthetic API) | Synthetic match returned matched Java/PostgreSQL, missing Python, UNKNOWN eligibility. |
| Company facts | Public permitted facts have direct source and retrieval time; absent facts not invented | IMPLEMENTED / PASS (parser/API evidence) | Page metadata only; broader external research-provider integration NOT IMPLEMENTED. |
| Answer drafting | Optional Ollama; source evidence references; unsupported claims rejected; disabled mode safe | IMPLEMENTED / PASS (local synthetic) | Actual `qwen2.5:3b` generation through the ApplyPilot provider returned HTTP 200 and an exact resume/version evidence citation. Missing evidence returned 400; an unsupported-claim request returned controlled 422. No model was downloaded; AI remains disabled by default. |
| Individual/bulk preparation | Exact job and resume-version association; answer snapshots; editable review; duplicate prevention; manual employer submission | IMPLEMENTED / PASS (automated + prior API) | Bulk API verified; extension does not click Submit. Automatic resume tailoring is not claimed. |
| Chrome extension | Session auth, expiry/recovery, supported ATS detection/mapping, preserve fields, exact version upload, legal-answer confirmation | IMPLEMENTED / PARTIAL | Automated extension tests/build pass; real Chrome install, auth and ATS attachment remain NOT TESTED. |
| Application tracking/dashboard | Owned unique records; exact resume/answer snapshots; candidate confirmation for APPLIED; database-derived metrics | IMPLEMENTED / PASS (tests + prior API) | Prior API integration recorded. Synthetic cleanup limits and outstanding app record are documented in test report. |
| Follow-ups/interviews/skills/notifications | Owner-scoped persistence, editable evidence-grounded answers, configurable reminders, progress and read state | IMPLEMENTED / PASS (tests + isolated E2E) | 2026-11-06 browser run passed application, follow-up completion, interview edit, reminder preference, and notification read state. Live Ollama answer drafting also passed; screen-reader review remains NOT TESTED. |
| Certification Vault | Existing model extended; owner-scoped PDF; expiry reminders; application/resume evidence snapshots | IMPLEMENTED / PASS (isolated V12 API + E2E) | Current API validated V12 in the separate E2E PostgreSQL DB. Live create/list/dedupe, cross-owner 404, PDF validation and SHA-256 download passed. Primary DB remains V11 and was not migrated; scanner is `NOT_CONFIGURED`. |

## Security and reliability launch gates

| Control | Acceptance criteria | Current status | Remaining evidence/gap |
|---|---|---|---|
| ASVS 5.0 security review | Traceable control-by-control assessment for applicable Level 1/2 controls, with evidence and exceptions | NOT COMPLETE | Existing security audit is prioritized but not an ASVS control matrix; independent penetration test not done. |
| Authorization/isolation | Cross-user access denied for API, resume and certificate metadata/files | PASS (selected automated/API tests) | V12 certificate endpoints require isolated integration tests. Extension live auth not tested. |
| JWT/session lifecycle | Rotation, logout, expiry, storage, revocation impact documented and tested | PARTIAL | Web tokens use sessionStorage; XSS can access same-tab state; access JWT remains valid until expiry after logout. Extension uses session storage. |
| Passwords/recovery | Strong adaptive hash, generic reset response, high-entropy hashed single-use reset tokens, delivery and rate limits | PASS / PARTIAL | SMTP delivery configuration and live provider flow pending. |
| SQL injection/XSS/CSRF/CORS | Parameterized DB operations; output encoding; explicit origin/method/header policy; appropriate CSRF model | PARTIAL | Static/manual and selected tests only; full ASVS validation and penetration test pending. |
| SSRF/import | DNS/IP/redirect validation, response/time limits, safe parsers and no private network access | PASS (tests + live permitted imports) | Revalidate during security review; test DNS rebinding and IPv6 edge cases. |
| Upload/storage | Actual content validation, bounded sizes, private owner-scoped paths, integrity checks and malware scanning | PARTIAL | Resume parser checks signatures and parser behavior; certificate checks signature/EOF/hash. Malware scanner not configured; no production object store. |
| Abuse controls | Distributed rate limits for sensitive endpoints; bounded timeouts; fail-safe dependency behavior | PASS (local Redis tests) | Production Redis authentication/TLS/HA and operational alerting not configured. |
| Secrets | No credentials in Git; external secret injection/rotation; least privilege | PASS for repository hygiene / NOT DEPLOYED | Local `.env` excluded; production secret manager and rotation process not configured. |
| TLS/security headers | Public HTTPS, secure cookies/session policy where applicable, HSTS and tested CSP | NOT DEPLOYED | Current Nginx headers and loopback Compose are local only; no domain/TLS ingress. |
| Dependencies | Complete backend/frontend/worker/extension scans, policy and remediation | PARTIAL | Maven Trivy still has two documented Spring findings. Frontend/extension npm audit and worker pip-audit report zero after worker updates; full container/base-image scan and release-commit rescan remain required. |
| Audit/logging | Structured logs, correlation IDs, redaction tests, retention/alerting | PARTIAL | No centralized log pipeline or alerting; sensitive-data logging review incomplete. |
| Health/monitoring | Separate liveness/readiness, metrics, dashboards and actionable alerts | PARTIAL | `/api/health` is readiness for PostgreSQL/Redis/worker; no metrics/alerting or separate liveness endpoint. |
| Backups/recovery | Automated encrypted off-host DB and document backup; paired restore verifies metadata and files | PARTIAL | PostgreSQL-only isolated restore passed; paired document-volume restore and scheduled off-host encrypted backups not proven. |
| Worker recovery | Idempotent processing, durable retry/dead-letter behavior and interruption tests | PARTIAL | Upload metadata survives worker failure, but no durable queue/retry service is configured. |
| Production containers | Non-root, pinned/scanned images, health checks, resource limits, network isolation, graceful stop | PARTIAL | App/worker non-root; local Compose lacks production resource limits, TLS ingress and complete image digest pinning. |
| Database migration | Clean-install plus upgrade migration tests; safe release procedure and rollback strategy | BLOCKED / PARTIAL | V1–V11 applied historically; V12 unactivated. No clean-install migration CI test. Rollback is forward-fix/backup restore. |

## Deployment completion

| Gate | Acceptance criteria | Status |
|---|---|---|
| Production topology/configuration | Separate deploy profile with private DB/Redis/worker, HTTPS edge, persistent private documents, resource limits, external secret injection | IMPLEMENTED / CONFIG VALIDATION PASS; NOT DEPLOYED |
| CI/CD | Build, all tests, migration validation, complete security scans, deploy approval and rollback process | IMPLEMENTED IN WORKFLOW; NOT YET EXECUTED ON GITHUB — local YAML parse passed; release publishing depends on tests, Maven Trivy, Python pip-audit, npm audit, and isolated E2E. GitHub-hosted execution/artifacts remain NOT TESTED. |
| Hosted services | Frontend/API/database/Redis/worker deployed to controlled infrastructure | BLOCKED — no provider/account/credentials/domain authorized or configured |
| Domain and TLS | Custom DNS, certificate issuance/renewal and public endpoint verification | BLOCKED — requires domain/DNS and deployment target |
| Operational ownership | Monitoring, backups, restore drills, incident/rotation procedures and on-call recipient | NOT IMPLEMENTED |
| Release acceptance | All critical end-to-end workflows on deployed application; no unresolved critical/high findings | NOT MET |

## Completion rule

Do not label ApplyPilot production-ready or complete while any deployment, migration, security, restore, monitoring, or live-workflow gate above is BLOCKED, PARTIAL, or NOT TESTED. Deployment depends on an approved hosting provider/account, billing arrangement if required, DNS/domain control, production secrets, and an authorized V12 release window with verified paired backup.
