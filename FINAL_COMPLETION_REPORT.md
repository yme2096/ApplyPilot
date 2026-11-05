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
