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

The production topology is prepared but not deployed. There is still no configured cloud secret manager, monitoring/alerting, automated encrypted off-host backup, or full paired resume-volume restore drill. The backend Trivy scan retains two documented Spring CVE exceptions; worker `pip-audit` and frontend/extension `npm audit` now report zero findings after the worker dependency updates. On 2026-11-06, the isolated browser suite passed 7/7, recovery checks 7/7, and local synthetic Ollama answer generation passed. Live Chrome popup/auth/ATS/attachment remain NOT TESTED. No production load or penetration test was performed. ApplyPilot is not production-ready.

## Production deployment profile (prepared; NOT DEPLOYED)

`docker-compose.production.yml` is a separate deploy profile. It publishes only Caddy ports 80/443, keeps PostgreSQL/Redis/API/worker unpublished, separates internal service and edge networks, mounts persistent volumes, applies resource/pid limits and bounded log rotation, and requires image references pinned by digest. Caddy obtains and renews public TLS certificates when the domain's DNS and firewall are correctly configured. The frontend's `/healthz` endpoint and API's anonymous `/api/live` liveness endpoint are distinct from `/api/health` readiness.

The release workflow runs backend/frontend/extension/worker tests, isolated browser E2E/performance/recovery, Maven Trivy policy, Python `pip-audit`, and frontend/extension `npm audit`. Image publication depends on the security job and the other release gates and runs only for a `v*` tag; the workflow uploads immutable API/web/worker image digests. The workflow has been YAML-validated locally but has not been run by GitHub Actions. It does not deploy to a server. Deployment still requires an approved host/provider, domain/DNS, firewall, pinned Caddy/PostgreSQL/Redis image digests, a private secrets directory, SMTP relay, encrypted off-host backup target, and an authorized migration window.

On the deployment host, create `.env.production` from `PRODUCTION.env.example` and set exact immutable image digests and domain values. Create the secret files listed under `secrets/`: `postgres_password`, `jwt_secret` (at least 32 random bytes), `redis_password`, `redis_acl` (Redis ACL containing the same password), `smtp_username`, and `smtp_password`; restrict host permissions to the deployment operator. Compose mounts Spring property secrets through `SPRING_CONFIG_IMPORT=configtree:`. Docker Compose file-backed secrets are read-only host file mounts, not a cloud secret manager; protect the host and disk encryption accordingly. Validate before deployment with `docker compose -f docker-compose.production.yml --env-file .env.production config --quiet`. Never point this profile at the existing local development volumes.
