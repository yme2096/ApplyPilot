# ApplyPilot AI

Candidate-controlled job discovery and application preparation platform.

Phase 2 currently supports permitted Greenhouse/Lever sources, RSS/Atom feeds, public structured job metadata, source-linked company metadata retrieval, explainable criteria matching, and evidence-cited answer drafts through optional local Ollama. Public page import validates hosts and redirects, limits response size, and falls back to manual entry when metadata is unavailable. Company facts are only shown when present in page metadata; eligibility remains `UNKNOWN` when candidate/profile facts do not establish the requirement. Ollama inference has not been live-verified on this computer because Ollama is not installed.

## Local development

Prerequisites: Windows PowerShell 7, Docker Desktop with the Linux engine, Java 21, Maven 3.9+, and Node.js 20+. Keep the existing `.env`; for a new checkout, copy `.env.example` and set local secrets. Use the guarded scripts and full setup instructions in [LOCAL_DEVELOPMENT_GUIDE.md](LOCAL_DEVELOPMENT_GUIDE.md):

```powershell
pwsh -NoProfile -File .\scripts\Start-ApplyPilot.ps1
pwsh -NoProfile -File .\scripts\Get-ApplyPilotStatus.ps1
pwsh -NoProfile -File .\scripts\Stop-ApplyPilot.ps1
```

The web UI is served on port 5174 and the API on port 8081; PostgreSQL uses host port 5433. Published Compose ports bind only to `127.0.0.1`. The startup script checks existing named volumes and a verified database backup, confirms Flyway V11 before API startup, creates a paired database/resume backup, and waits for service readiness. Routine shutdown retains all volumes. This Compose stack is for local development, not production.

The backend uses Flyway migrations and does not rely on Hibernate schema update. The web app calls the backend API. See [ARCHITECTURE.md](ARCHITECTURE.md), [DATABASE.md](DATABASE.md), [API_DOCUMENTATION.md](API_DOCUMENTATION.md), [SECURITY.md](SECURITY.md), and [DEPLOYMENT.md](DEPLOYMENT.md).

## Optional local AI

Ollama is the first supported AI provider. It is disabled by default, uses no API key, makes no paid calls, and never downloads a model automatically. See [AI_SETUP.md](AI_SETUP.md) for local installation, model selection, environment settings, and verification steps.

## Chrome extension

Application Bundles support selecting multiple saved jobs, choosing an exact resume version per job, creating separate DRAFT preparations, and reviewing/correcting each answer in a consolidated screen before individually approving bundles as READY. Bulk state is persisted so review can continue after reload; employer forms are still prepared one at a time. The Manifest V3 extension can prepare supported fields from a READY bundle and attempts candidate-approved attachment of its exact resume version. It preserves existing form entries and never submits an employer form. Chrome installation and live ATS/file-input behavior remain NOT TESTED. See [extension/README.md](extension/README.md) for setup, automated coverage, safety checks, and browser-test limits.

## Current delivery status

Phase 4 tracking, dashboard metrics, follow-ups, interview preparation, verified-skill gaps, local notifications and configurable reminders are implemented through Flyway V10/V11. Prior live database/API integration checks passed. Dashboard component coverage is included in the 20-test frontend suite. Phase 4 remains in progress pending authenticated frontend/device accessibility verification and live Ollama generation. Phase 3 remains in progress; Chrome popup/auth, live ATS and actual attachment checks remain NOT TESTED. Phase 5 is IN PROGRESS: local runtime recovery, Redis-backed endpoint limits, CSP/security headers, loopback-only Compose ports, and frontend dependency remediation passed current automated/runtime checks. See [PHASE_TEST_REPORT.md](PHASE_TEST_REPORT.md), [SECURITY.md](SECURITY.md), [DEPLOYMENT.md](DEPLOYMENT.md), and [PHASE5_READINESS_AUDIT.md](PHASE5_READINESS_AUDIT.md). ApplyPilot is not production-ready.

The latest backend dependency audit uses Trivy over the 122-component runtime/test SBOM and records two narrowly scoped Spring exceptions. The services were stopped at the latest runtime inspection; see [PROJECT_STATUS.md](PROJECT_STATUS.md) for current status. Phase 2 live Ollama generation and Phase 3 live Chrome checks remain NOT TESTED.

## Isolated Phase 5 verification

Playwright end-to-end, performance, and recovery scripts target only the disposable `applypilot-e2e` Compose stack with separate `applypilot-e2e-*` volumes and synthetic accounts. From PowerShell, run `frontend/e2e/start-isolated.ps1`; it validates the database target and volume attachments, then starts and rechecks the isolated services. From `frontend`, run `npm run test:e2e`, `npm run test:perf`, or `npm run test:recovery`. The backup restore drill uses a separate `applypilot-restore-drill-data` volume. Never point these scripts at the primary Compose file or use `down -v`.
# Certification Vault

ApplyPilot's Certification Vault extends the existing certification records with expiry tracking, candidate-provided issuer/Credly links, private PDF attachments, and configurable in-app reminders. See the [API documentation](API_DOCUMENTATION.md) for routes and [database notes](DATABASE.md) for V12 activation status. The feature is implemented in source; the primary database requires the additive Flyway V12 migration before runtime use. Certificate links are not independently verified and the default malware scanner is not configured.
