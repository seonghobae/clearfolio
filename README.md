# Clearfolio Viewer

This repository contains the MVP backend for an integrated document viewer platform.
The current implementation includes non-blocking submit, job status polling, and
an asynchronous conversion simulation for early pattern validation.

## Scope

- `POST /api/v1/convert/jobs`: upload document and receive async job id.
- `GET /api/v1/convert/jobs/{jobId}`: poll conversion status and lifecycle fields.
- `POST /api/v1/convert/jobs` response includes `jobId`, `status`, and `statusUrl`.
- `GET /viewer/{docId}`: canonical viewer bootstrap entrypoint.
- `GET /api/v1/viewer/{docId}` and `GET /api/v1/convert/viewer/{docId}`: alias routes for compatibility.
- Errors follow shared shape (`errorCode`, optional `code`, `message`, `traceId`, `details`) for 404/409/400/500 paths.
- `GET /healthz`: readiness probe.
- HWP/HWPX are blocked by configuration.

## Compatibility notes

- API contract has been kept backward-compatible with the existing jobs + viewer flow.
- `GET /viewer/{docId}` remains the canonical entry route.
- Alias endpoints remain stable in behavior and response shape expectations.
- `DEAD_LETTERED` jobs are returned as 409 (same surface as failed state) so
  client-side state handling can treat terminal retry-exhausted cases consistently.

## Acceptance gates (current)

- Mandatory: test coverage 100%, docstring 100%, non-blocking request path,
  lightweight event queue, warning count 0, deprecated usage 0.
- Optional: DB pooler client path (when DB is introduced), PostgreSQL 17 support track.

## Documentation references

- `docs/architecture.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`

## Transfer metadata

- Target owner repo: to be set during transfer.
- Tech stack: Java 21 / Spring Boot / Maven.
- Primary package: `com.clearfolio.viewer`.
- Current branch default assumption: `main`.
