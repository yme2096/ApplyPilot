# Local operations and deployment readiness

## Current state

ApplyPilot's Compose stack is for local development only. It is not a production deployment. The current local services are published on loopback (127.0.0.1); PostgreSQL, Redis, the API and document worker must not be exposed directly to the public internet. Use TLS termination and private service networking in any separately designed production environment.

## Local startup and health checks

Use [LOCAL_DEVELOPMENT_GUIDE.md](LOCAL_DEVELOPMENT_GUIDE.md) and its guarded PowerShell scripts. The startup script verifies Docker Engine, `.env`, Compose configuration, the existing primary named volumes, and the latest database archive. It starts PostgreSQL/Redis/worker first, waits for health, confirms Flyway V11 and reads aggregate counts, creates a new paired database/resume backup, then builds and starts API/web. It stops before API startup if any prerequisite fails. The shutdown script uses `docker compose stop`; it retains containers and volumes.

Do not substitute `docker compose down -v`, volume removal, or database reset commands. The anonymous API readiness route is `http://localhost:8081/api/health`; the local web app is `http://localhost:5174`. Ollama is optional and disabled by default.

The API exposes anonymous `GET /api/health`, which checks PostgreSQL, Redis, and the document worker and returns 200/503 with a bounded response. Compose uses this endpoint for the API container healthcheck. The separate E2E Compose stack has its own healthcheck and isolated volumes.

## PostgreSQL backup and recovery

Historical verified database backup: `C:\Users\hp\AppData\Local\ApplyPilot\backups\applypilot-pre-phase5-20261103-182146.dump`. Its custom archive was independently accepted by `pg_restore --list`; the earlier isolated restore matched Flyway V11 and the recorded counts. Use `pwsh -NoProfile -File .\scripts\Backup-ApplyPilot.ps1` to create new timestamped database and resume-volume archives plus SHA-256 metadata. The script refuses to overwrite prior backups.

The earlier restore drill restored PostgreSQL only. The new backup helper creates a read-only archive of the resume volume and validates its tar structure and SHA-256, but a complete paired restore has not yet been executed. Store backups outside database volumes with restricted access and encryption appropriate to the data. Do not test restores against working volumes.

## Change and incident procedure

- Before any schema migration, verify database health, current Flyway version, available storage, and a validated backup archive. Apply migrations through normal Flyway/API startup only; never edit Flyway history manually.
- For a failed API or worker, inspect container state and redacted logs first. Restart only the affected existing service. Do not remove/recreate data volumes.
- Preserve logs without access tokens, passwords, resume text, or application answers. Record timestamps, service names, status codes and non-sensitive correlation IDs.
- After a change, run backend/frontend/extension suites and production builds, validate Compose configuration, and perform protected-route and web-header smoke checks.

## Production gaps

There is no production deployment manifest, TLS ingress, secret-manager integration, monitoring/alerting, or automated encrypted off-host backup. The backend Trivy SBOM scan completed and retains two documented Spring CVE exceptions; see `BACKEND_DEPENDENCY_AUDIT.md`. PostgreSQL-only restore passed; full paired resume-volume restore remains unverified. Browser E2E passed in the isolated test stack; live Chrome popup/auth/ATS/attachment and live Ollama generation remain NOT TESTED. No production load or penetration test was performed. ApplyPilot is not production-ready.
