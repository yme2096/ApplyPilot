# ApplyPilot Local Development Guide

This guide is for the existing Windows/Compose development setup. ApplyPilot is not production-ready. The commands preserve the named PostgreSQL, Redis, and resume volumes; routine shutdown uses `stop`, never volume removal.

## Prerequisites and first setup

- Windows 10/11, Docker Desktop with the Linux engine, PowerShell, Java 21, Maven 3.9+, Node.js 20+, and Git.
- Open Docker Desktop and wait for **Engine running**. The scripts detect an unavailable engine and stop without starting services.
- Keep the existing `.env`. If setting up a fresh checkout, copy `.env.example` to `.env`, then set unique local `POSTGRES_PASSWORD` and a random `JWT_SECRET` of at least 32 bytes. Never share `.env` or paste its contents into logs.
- The startup script refuses to run if any existing primary volume is missing. It also validates the latest database backup and refuses to start the API unless the database reports Flyway V11.

## Daily commands

Run these from the repository root in PowerShell 7 (`pwsh`):

```powershell
pwsh -NoProfile -File .\scripts\Start-ApplyPilot.ps1
pwsh -NoProfile -File .\scripts\Get-ApplyPilotStatus.ps1
pwsh -NoProfile -File .\scripts\Stop-ApplyPilot.ps1
```

The startup script validates Compose, checks the existing named volumes, validates the latest `.dump` archive with `pg_restore`, starts PostgreSQL/Redis/document worker, waits for readiness, reads Flyway version and aggregate record counts, creates a fresh database plus resume-volume backup, and then builds/starts only the API and web services. It waits for `/api/health` and the web root to return HTTP 200. It does not run `down`, remove volumes, or run API startup unless the database is already at V11.

Services and URLs:

| Service | Local address |
|---|---|
| Web | [http://localhost:5174](http://localhost:5174) |
| API readiness | [http://localhost:8081/api/health](http://localhost:8081/api/health) |
| PostgreSQL | `localhost:5433` |
| Redis | `localhost:6379` |
| Document worker | Internal Compose network, port 8000 |

For safe shutdown, `Stop-ApplyPilot.ps1` uses `docker compose stop`. It leaves containers and all named volumes in place. Never use `docker compose down -v`, `docker volume prune`, or `docker system prune` for routine work.

## Status and tests

The status script reports Docker Engine availability, Compose service state, API/web HTTP status, PostgreSQL/Redis/worker readiness, primary volume presence, Flyway version, and aggregate user/job/resume/application counts. It is read-only.

Run the suites and builds:

```powershell
mvn -B -f .\backend\pom.xml verify
Push-Location .\frontend; npm test; npm run build; Pop-Location
Push-Location .\extension; npm test; npm run build; Pop-Location
```

The isolated Playwright environment is separate from the primary Compose database. Run its preflight from the repo root with `pwsh -NoProfile -File .\frontend\e2e\start-isolated.ps1`, then from `frontend` run `npm run test:e2e`. Review `docker-compose.e2e.yml` and the preflight guard before running it; do not point tests at the primary stack. Existing automated suites do not verify live Chrome authentication/ATS behavior or real Ollama generation.

## Backups

Create a new timestamped PostgreSQL custom archive and a read-only archive of the existing resume volume:

```powershell
pwsh -NoProfile -File .\scripts\Backup-ApplyPilot.ps1
```

Files and a SHA-256 manifest are written under `%LOCALAPPDATA%\ApplyPilot\backups`. The script never overwrites an existing file, verifies the PostgreSQL archive with `pg_restore --list`, validates the resume tar archive, and hashes both. These backups contain candidate data; keep the folder private and encrypted according to your local policy. Run backups while the application is idle to reduce the chance of a file changing during the separate database/file snapshots.

## Isolated restore guide

Never restore into `applypilot_postgres_data`, `applypilot_resume_data`, the primary Compose project, or the existing `applypilot-restore-drill-data` volume. The documented earlier drill restored PostgreSQL into a separate volume but did not restore resume binaries. A current paired restore drill remains pending until Docker startup and a fresh paired backup succeed.

For a new restore drill, use a new timestamped volume name that does not already exist, create isolated PostgreSQL and resume containers with no published port or network access, validate the archive hashes first, restore PostgreSQL into the blank isolated database, and extract the resume archive into the blank isolated resume volume. Compare Flyway, aggregate row counts, resume-version references, file counts, and every file hash against the backup manifest. Do not use `--clean` against an existing volume. Retain or remove only the uniquely named drill volumes after confirming they contain no needed data; never prune Docker volumes globally. See `DATABASE.md` and `DEPLOYMENT.md` for the database-only drill evidence and limitations.

## Troubleshooting

- **Docker Engine unavailable:** Open Docker Desktop and wait until Engine is running; rerun the status script. The startup script does not launch Docker Desktop or reset containers.
- **Primary volume missing:** Stop. Do not let Compose create an empty replacement. Inspect Docker Desktop's volume list and recover from a verified backup only into a new isolated target.
- **Backup invalid or missing:** Startup stops before starting the API. Preserve the archive and inspect it; create a fresh backup from the running original database before any migration.
- **Database version is not V11:** Startup starts only the existing PostgreSQL/Redis/worker services and stops before API startup. Verify schema history and create a current verified backup before planning any migration.
- **API or web health timeout:** Run the status script. Inspect logs only after redacting email addresses, tokens, resume contents, answer text, and credentials. Do not include `.env` in diagnostic output.
- **Port conflict:** Check only the documented loopback ports. Do not change database ports or volume names without reviewing the Compose configuration and current clients.

## Optional integrations

Ollama remains optional and disabled by default. Follow `AI_SETUP.md` only if you choose to install it and download a model; ApplyPilot does not install software or download models automatically. Chrome extension installation and live ATS/attachment behavior remain unverified; see `extension/README.md`.
# Certification Vault local setup

The Certification Vault source requires Flyway V12 before its API or UI can run against PostgreSQL. Before applying the migration, follow the existing backup procedure and run the normal API startup path; do not alter Flyway history or reset volumes. The PDF files use the existing private uploads volume. Uploads are limited to 10 MiB and `.pdf`; malware scanning reports `NOT_CONFIGURED` unless an operator wires a scanner implementation. Issuer/Credly URLs are candidate-provided and are not independently checked.

After V12 is activated, open **Certification Vault**, create one record per certification, enter the issuer/name and issue date, leave expiration blank only for a non-expiring credential, optionally add the credential ID, result, and HTTPS issuer/Credly links, and attach the matching PDF. Confirm each status badge and candidate review state. When using certifications in a resume export or application bundle, select only the relevant credentials and review/approve the captured metadata. ApplyPilot does not submit certificate files to employers automatically.
