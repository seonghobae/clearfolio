# Clearfolio Viewer

This repository contains the MVP backend for an integrated document viewer platform.
The current implementation includes non-blocking submit, job status polling, and
an asynchronous conversion simulation for early pattern validation.

## Quick Start

1. Build and verify compilation:
   - `mvn -DskipTests compile`
2. Run tests:
   - `mvn test`
3. Start the app locally:
   - `mvn spring-boot:run`
4. Check readiness:
   - `curl -sS http://localhost:8080/healthz`

## Scope

- `POST /api/v1/convert/jobs`: upload document and receive async job id.
- `GET /api/v1/convert/jobs/{jobId}`: poll conversion status and lifecycle fields.
- `POST /api/v1/convert/jobs` response includes `jobId`, `status`, and `statusUrl`.
- `POST /api/v1/convert/jobs/{jobId}/retry`: operator-triggered retry for dead-lettered jobs.
- `GET /viewer/{docId}`: canonical viewer bootstrap entrypoint.
- `GET /api/v1/viewer/{docId}` and `GET /api/v1/convert/viewer/{docId}`: alias routes for compatibility.
- Errors follow shared shape (`errorCode`, optional `code`, `message`, `traceId`, `details`) for 404/409/400/500 paths.
- `GET /healthz`: readiness probe.
- HWP/HWPX are blocked by configuration.

## Compatibility notes

- API contract has been kept backward-compatible with the existing jobs + viewer flow.
- `GET /viewer/{docId}` remains the canonical entry route.
- Alias endpoints remain stable in behavior and response shape expectations.
- Dead-letter terminal cases keep `status=FAILED` in API payloads and set
  `deadLettered=true` when retries are exhausted.
- Dead-lettered jobs can be re-queued by an operator with
  `X-Clearfolio-Operator-Id` via `/api/v1/convert/jobs/{jobId}/retry`.

## Acceptance gates (current)

- Mandatory: test coverage 100%, docstring 100%, non-blocking request path,
  lightweight event queue, warning count 0, deprecated usage 0,
  and one-day delivery schedule with security verification evidence.
- Optional: DB pooler client path (when DB is introduced), PostgreSQL 17 support track.

Current release claim boundary:
- Mandatory gates are validated through committed evidence under `docs/qa/evidence/`.
- Optional DB pooler/PostgreSQL 17 tracks are documented only and not executed in this MVP release.

## Delivery schedule

- One-day customer delivery plan (including security verification):
  - `docs/plans/2026-02-20-24h-customer-delivery-plan.md`

## Documentation references

- `docs/architecture.md`
- `docs/prd-integrated-document-viewer-platform.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/diagrams/retry-deadletter-flow.md`

## Transfer metadata

- Target owner repo: to be set during transfer.
- Tech stack: Java 21 / Spring Boot / Maven.
- Primary package: `com.clearfolio.viewer`.
- Current branch default assumption: `main`.
