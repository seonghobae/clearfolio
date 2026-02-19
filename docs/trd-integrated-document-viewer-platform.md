# Technical Requirements Document: Integrated Document Viewer Platform

- Feature: Integrated Document Viewer Platform
- Version: `0.1.0-mvp`
- Owner: Product Manager + Platform Team
- Last updated: `2026-02-20`
- Sources:
  - `docs/architecture.md`
  - `docs/prd-integrated-document-viewer-platform.md`
  - `docs/plans/2026-02-19-integrated-document-viewer-execution-plan.md`
  - `docs/diagrams/submit-flow.md`
  - `docs/diagrams/status-flow.md`
  - `docs/diagrams/preview-flow.md`
  - `plans/milestones.md`
  - `plans/gate_risks.md`

## 1) Purpose and scope

This TRD captures the technical scope that is implemented in this repository today and the required gap work before Pattern B production hardening.

- **Implemented now (MVP scope):** non-blocking submit/status pipeline, SHA-256 based dedupe, bounded async worker simulation, health endpoint, NUL removal at persistence boundary, blocked extension guard for `hwp` and `hwpx`, and configuration-driven tuning.
- **Explicitly deferred:** persistent DB migrations, durable queue substitution, real converter/container runtime, artifact store, signed download links, admin APIs, observability/metrics stack, and full RBAC/security gate integrations.

## 2) Baseline architecture and components

- `ConversionController` (`/api/v1/convert`): HTTP ingress for submit/status.
- `DocumentConversionService` (`DefaultDocumentConversionService`): submit flow with validation, hash calculation, dedupe, repository save, enqueue.
- `DefaultDocumentValidationService`: extension validation from filename + configurable blocked list.
- `InMemoryConversionJobRepository`: canonical dedupe map (`contentHash -> jobId`) plus in-memory job map.
- `DefaultConversionWorker`: async task dispatch into Spring `TaskExecutor`; simulates conversion delay and writes path/message.
- `HealthController`: operational health probe endpoint.
- `ConversionJob` + `ConversionJobStatus`: in-memory domain state and status transitions.
- `Preview orchestration` for `/viewer/{docId}`: viewer endpoint contract is implemented at API layer, while S2S token bootstrap is still planned in `docs/diagrams/preview-flow.md`.

## 3) API contract (implemented)

| API | Method | Contract |
| --- | ------ | -------- |
| `POST /api/v1/convert/jobs` | `POST` `multipart/form-data` | Accepts `file`, validates input, returns 202 immediately with `SubmitConversionResponse { jobId, status, statusUrl }` |
| `GET /api/v1/convert/jobs/{jobId}` | `GET` | Returns `ConversionJobStatusResponse { jobId, fileName, status, message, convertedResourcePath, createdAt, startedAt, completedAt, attemptCount, maxAttempts, retryAt, deadLettered }` if found; 404 otherwise |
| `GET /healthz` | `GET` | Returns `{ "status": "ok" }` |
| `GET /viewer/{docId}` (alias: `/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}`) | `GET` | **Implemented (MVP):** returns `ViewerBootstrapResponse` when job is `SUCCEEDED`; returns `409 CONFLICT` for `SUBMITTED`/`PROCESSING`/`FAILED`/`DEAD_LETTERED`; and `404 NOT_FOUND` when missing. S2S session bootstrap is planned as a post-success extension at the viewer entry path.

## 4) Technical requirements and implementation mapping

| PRD AC | Requirement | Mapping docs | Implementation status | Notes |
| --- | --- | --- | --- | --- |
| AC-01 | Submit is non-blocking and returns `202` quickly with `jobId` and status URL | `docs/diagrams/submit-flow.md` | `IMPLEMENTED` | Request path never invokes conversion directly and returns job id immediately. |
| AC-02 | Workflow supports status transitions for submit/polling (`SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DEAD_LETTERED`) | `docs/diagrams/status-flow.md` | `IMPLEMENTED` | `ConversionJob` transitions are reflected by `GET /api/v1/convert/jobs/{jobId}`. |
| AC-03 | HWP/HWPX blocked by default with exception policy | `docs/diagrams/submit-flow.md`, `docs/diagrams/preview-flow.md` | `IMPLEMENTED` for blocklist, `PLANNED` for exception lane | Blocked extensions return structured 400; exception path is documented as planned integration. |
| AC-04 | Viewer entrypoint provides stable state-gated bootstrap contract and routes to S2S session orchestration as planned extension | `docs/diagrams/preview-flow.md` | `PLANNED` | Viewer workflow includes stable API contract now; external session bootstrap and token flow remain planned at viewer-path orchestration layer. |
| AC-05 | `/viewer/{docId}` path supports state-gated responses | `docs/diagrams/preview-flow.md` | `IMPLEMENTED (MVP)` | `ConversionController#getViewer` returns bootstrap on `SUCCEEDED` and `409` for `SUBMITTED`/`PROCESSING`/`FAILED`/`DEAD_LETTERED` (aliases `/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}` also route). |
| AC-06 | Standardized error schema and trace ID across status + viewer | `docs/diagrams/status-flow.md`, `docs/diagrams/preview-flow.md`, `ConversionController#getViewer` | `IMPLEMENTED (submit/status/viewer)` | `errorCode`, `details`, `message`, `traceId`, and compatibility `code` are returned for 404/409 error paths on viewer endpoint in addition to status/submit. |
| AC-08 | NUL string sanitization on persistence boundary | `docs/diagrams/submit-flow.md` | `IMPLEMENTED` | `ConversionJob` sanitizes file name/content type/message/resource path at state write points. |
| AC-09 | Release evidence and smoke/compliance checks complete | `docs/qa/smoke_test_plan.md` | `PLANNED` | Existing smoke plan covers MVP; full AC-09 evidence still pending. |

## 5) Planned backlog from TRD to full platform

- Signed/temporary download endpoint + checksum response.
- Admin control plane for retry/DLQ/review workflows.
- Real file artifact persistence (immutable object path, lifecycle retention).
- Containerized converter integration (JODConverter/LibreOffice path).
- Queue durability and metrics (DLQ depth, worker lag, retry counts).
- Security surface hardening (authn/authz, request tracing, explicit error schema with `traceId`, audit chain).

## 6) Non-functional requirements status

- **Performance:** Current implementation is thread-pooled and non-blocking on submit path; concurrency bounded by in-memory queue capacity.
- **Resilience:** Simulated worker catches runtime errors and marks job failed; thread interruption handled.
- **Observability:** Minimal; only HTTP JSON payload + exception advice currently present.
- **Data integrity:** One in-memory dedupe index, canonical hash keying, and null-sanitization at boundary. No durable persistence or transactions yet.
- **Deployability:** No dedicated container profile in-repo yet; this is MVP Java backend only.

## 6-1) Mandatory acceptance gates (current release target)

- **Coverage gate:** 100% line/branch coverage for production classes (`jacoco.csv` evidence).
- **Docstring gate:** 100% JavaDoc coverage on public production symbols.
- **Non-blocking web gate:** submit/status/viewer request path does not perform conversion work inline.
- **Lightweight queue gate:** bounded async executor, retry scheduling, dead-letter terminal path.
- **Warning gate:** compiler/test verification outputs must be warning-free.
- **Deprecated gate:** deprecated API usage count must be zero in build verification.

## 6-2) Optional acceptance tracks

- **Client DB pooler:** optional PgBouncer/PgCat detection and read-only routing (only when DB is introduced).
- **PostgreSQL 17:** optional compatibility track with integration verification before enablement.

## 7) Risk acceptance and validation alignment

- `R1` conversion instability: acceptable only for simulation path; production-ready converter integration is a planned phase-2 dependency.
- `R3` license contamination: dependency and container image scanning remains a planned phase-2/3 gate item.
- `R4` request-thread blocking: current architecture avoids blocking submits by design; keep regression checks in CI to preserve this.
- `R5` queue overflow: queue capacity is configured and bounded by `ThreadPoolTaskExecutor`; monitor and replace with durable queue in platform hardening phase.

## 8) Current quality evidence and next checks

- Unit tests assert dedupe idempotency and concurrent duplicate handling.
- Controller tests assert 202 submit, unsupported format mapping, status/not-found behavior, and `/viewer/{docId}` state-gated behavior.
- Health endpoint test asserts operational readiness payload.
- Next required checks for TRD completion before Pattern B gate:
  - Add smoke test command and smoke compose artifacts when containerized runtime is introduced.
- Add API-level integration tests for malformed names and oversized payloads (service validation coverage is now added in unit tests).
- Add API test evidence for error-schema consistency once auth/error tracing is added.
