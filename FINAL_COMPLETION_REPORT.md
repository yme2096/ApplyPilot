# ApplyPilot Certification Vault — Implementation Report (2026-11-05)

## Implemented

- Extended the existing certification model and table with expiry, credential ID, candidate-provided issuer/Credly links, score/result, candidate self-attestation, reminder intervals, private PDF metadata, scan state and soft archive support.
- Added authenticated, owner-scoped CRUD/archive and PDF upload/download endpoints. Upload limit is 10 MiB; validation checks PDF signature and EOF marker and calculates SHA-256. A modular malware scanner hook is present, but the default status is `NOT_CONFIGURED`.
- Added expiration statuses, search/filter, default 90/30/7-day deduplicated in-app reminders, a global preference, resume export evidence, and candidate-approved immutable bundle/application metadata snapshots.
- Added responsive vault interface and certification-related backend/frontend tests.

## Verification results

- **PASS:** Backend main-source Maven compile.
- **PASS:** Backend full test suite: 76 tests, 0 failures, 0 errors, 0 skipped.
- **PASS:** Frontend suite: 5 files and 24 tests.
- **PASS:** Production build after correcting an accidental leading `+` in the mobile CSS media rule; final build result is recorded in this report after the build process completes.
- **BLOCKED:** Flyway V12 was not applied to the primary PostgreSQL database. No primary DB restart, migration, record modification or volume change was performed. Live routes, PostgreSQL persistence, reminder behavior and migrated compatibility remain unverified.
- **NOT TESTED:** Malware scanning, issuer or Credly verification, real candidate certificate uploads, live owner isolation, actual database reminder delivery, and browser visual/screen-reader review.

## Add the candidate's four existing certifications

Once V12 is safely activated through the normal Flyway/API startup path:

1. Open **Certification Vault** and add one record for each credential.
2. Enter the credential's exact name, issuing organization and issue date. Leave expiration blank only if the credential has no expiry.
3. Add credential ID and score/result if shown on the certificate. Add issuer verification or Credly links only if available; these remain candidate-provided links.
4. Attach the corresponding PDF to that specific record and confirm the displayed filename and expiration badge.
5. Mark the candidate review checkbox only after comparing the saved details with the original credential.
6. Select relevant records when exporting a resume or creating a preparation bundle, review the saved snapshot, and explicitly approve it. ApplyPilot does not submit certificate PDFs to employers automatically.

No certificate records or candidate files were created, altered, or uploaded in this implementation pass.
- **PASS:** Final `npm run build` completed successfully after removing the stray CSS diff marker; Vite transformed 1,705 modules and emitted production JS/CSS assets without warnings.

## Full production-completion continuation — 2026-11-06

### Delivered and verified

- Added a separate production Compose profile with Caddy TLS ingress, private PostgreSQL/Redis/API/worker networks, persistent private storage, required configtree secrets, resource/process limits, bounded log rotation, and immutable image-reference requirements. Synthetic-only Compose config validation passed. The profile is **NOT DEPLOYED**.
- Added an authenticated anonymous liveness endpoint `/api/live` distinct from readiness `/api/health`, a frontend `/healthz`, controlled document-worker errors for malformed PDFs, and four worker unit tests.
- Extended release CI so publishing is gated by backend/frontend/extension/worker tests, isolated E2E/performance/recovery, Maven Trivy policy, Python pip-audit, and frontend/extension npm audit. Workflow YAML parses locally. GitHub-hosted workflow execution and artifacts are **NOT TESTED**.
- Added a responsive browser E2E for Certification Vault. Isolated current-source API validation at Flyway V12 passed; create/list/deduplication, owner-denied metadata and file access, invalid dates, PDF validation, private upload/download, signature, and SHA-256 passed. Primary schema remains V11 and was not changed.
- Fixed the isolated recovery harness cold-start timeout and partial-result reporting. Its seven recovery checks passed after change.
- Updated worker dependencies after a completed `pip-audit` initially found 26 advisories in old `python-multipart`/Starlette versions. FastAPI 0.162.0, Starlette 1.10.0, and python-multipart 0.0.33 pass worker tests and current `pip-audit` with no known findings. The machine-readable report is under ignored `security-reports/`.
- Verified actual ApplyPilot answer generation through the existing local Ollama provider against a fictional resume in the isolated E2E database. `qwen2.5:3b` returned an answer citing the exact version and evidence. Missing evidence returned 400; an unsupported-claim prompt returned 422. The model was already present; no model was downloaded. Normal AI behavior remains disabled by default.

### Current test evidence

| Check | Result |
|---|---|
| Backend `mvn -B test` | PASS — 77 tests, no failures/errors |
| Frontend Vitest | PASS — 24 tests; production build PASS |
| Extension Vitest/build | PASS — 20 tests; build PASS |
| Worker unit tests/current image | PASS — 4 tests; image built and healthy |
| Playwright isolated E2E | PASS — 7/7 |
| Isolated recovery | PASS — 7/7 against updated worker; Redis 503 in 2.076 s, PostgreSQL readiness 503 in 3.134 s |
| Synthetic 250-job performance | PASS — measured; see `PHASE_TEST_REPORT.md` for p50/p95 and throughput |
| Frontend/extension npm audit | PASS — zero findings each |
| Worker pip-audit | PASS after update — no known vulnerabilities |
| Maven Trivy | ACCEPTED RISKS — 2 Critical-classified Spring MVC findings remain; full raw report unchanged |
| Production Compose config / workflow YAML parse | PASS with synthetic config; no service deployment |

### Not complete / release blockers

- Production deployment requires an approved hosting provider/account, domain/DNS/firewall, immutable Caddy/PostgreSQL/Redis digests, private production secrets, SMTP relay, encrypted off-host backup destination, monitoring/alert contact, and authorized release/migration window.
- The primary PostgreSQL database remains V11. V12 was verified only in isolated E2E. Applying V12 to primary requires the guarded verified backup procedure and authorized maintenance window.
- Full ASVS 5.0 control-by-control assessment, independent penetration test, base-image scan, full paired database+resume restore, automated encrypted off-host backups, monitoring/alerting, malware scanning, production load testing, and hosted CI run remain NOT TESTED or NOT IMPLEMENTED.
- Phase 3 real Chrome popup/auth, ATS compatibility, and actual attachment remain NOT TESTED. Actual-device and screen-reader evaluation remains NOT TESTED. SMTP delivery remains unconfigured.

**Final assessment:** the local software and isolated test gates are materially more complete, but ApplyPilot is **NOT production-ready** and Phase 5 remains **IN PROGRESS**. The next safe milestone is an approved deployment target and operations/security plan, followed by a verified paired backup/restore and authorized V12 migration; do not deploy against the existing primary volumes.
