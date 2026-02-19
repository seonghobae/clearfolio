# Conversion Service Architecture

This repository currently ships a minimal Pattern B scaffold for non-blocking submission, status polling, and asynchronous worker simulation. The viewer shell entrypoint has started implementation for the state-gated bootstrap contract.

## Runtime Flow

- Submit flow: client calls `POST /api/v1/convert/jobs` with a multipart file.
  - `docs/diagrams/submit-flow.md` contains both component and sequence diagrams.
  - Current implementation: validation, dedupe by content hash, and async worker enqueue.
- Status flow: client calls `GET /api/v1/convert/jobs/{jobId}` for lifecycle state.
  - `docs/diagrams/status-flow.md` contains component + sequence diagrams and 404/failed-state branches.
- Preview flow: `GET /viewer/{docId}` (alias routes `/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}`) is implemented for bootstrap/error-by-state behavior with 404/409 handling at the API layer. S2S preview-session orchestration is still planned.
  - `docs/diagrams/preview-flow.md` documents the planned component and exception flow.
- Health and readiness check is available at `GET /healthz`.

## Compatibility contract guardrails

- API compatibility is preserved by keeping all existing route-entry contracts stable.
- `POST /api/v1/convert/jobs` remains asynchronous and returns `202` with `jobId`, `status`, and `statusUrl`.
- `GET /api/v1/convert/jobs/{jobId}` and `/viewer/{docId}` retain status semantics and do not change contract shape.
- `errorCode` is the canonical error key, with compatibility `code` retained for clients that consume it.

## Runtime quality gates

- Non-blocking request path is mandatory: submit/status/viewer handlers do not run conversion inline.
- Event queue must stay lightweight: bounded executor queue, retry scheduling, and dead-letter transition.
- Build hygiene is mandatory: warning count 0 and deprecated usage 0 on verification commands.

## Diagram index

- `docs/diagrams/submit-flow.md`: submit path, duplicate job handling, HWP/HWPX block, and exception branches.
- `docs/diagrams/status-flow.md`: status polling, success path, failure path, and job-missing branch.
- `docs/diagrams/preview-flow.md`: planned WAS -> preview service flow with exception handling and not-yet-ready states.

## Default Non-Functional Rules

- Non-blocking API behavior: submission returns quickly with `202 Accepted` and `jobId`.
- HWP/HWPX are blocked by default through configuration.
- Conversion queue/workers are intentionally pluggable via interfaces for future replacement with durable queues and containerized workers.
- Error handling for blocked formats is explicit, and mapped API errors now include `errorCode`, optional `details`, `message`, and `traceId` on supported endpoints.

## Component Boundaries

- `controller`: HTTP-facing endpoints and exception mapping.
- `service`: Validation, conversion orchestration, worker execution.
- `repository`: Job persistence abstraction.
- `model`: Job and status domain objects.
- `config`: Conversion properties and execution resources.

## Current implementation snapshot

This repository currently ships a MVP scaffold, not a production-grade Pattern B rollout yet.

- Asynchronous submit flow and status polling are active.
- Worker execution is an in-memory async simulation (`DefaultConversionWorker`) with bounded thread pool + queue.
- Job state and output paths are kept in-process (`InMemoryConversionJobRepository`).
- HWP/HWPX are blocked through configuration and file-extension validation.
- HWP/HWPX exception handling is documented in diagrams as a future manual-approval lane.
- NUL characters are sanitized when writing job metadata.
- Preview shell and `GET /viewer/{docId}` endpoint behavior is implemented for bootstrap-on-success (`200`) and conflict responses for `SUBMITTED`/`PROCESSING`/`FAILED`/`DEAD_LETTERED` (`409`), plus `404` when missing; full preview-session orchestration and token bootstrap are still planned.

## AC alignment (current)

| AC | Requirement | Status | Evidence |
|---|---|---|---|
| AC-01 | Non-blocking submit returns 202 with `jobId`/`statusUrl` | `IMPLEMENTED` | `docs/diagrams/submit-flow.md`, `docs/trd-integrated-document-viewer-platform.md` |
| AC-02 | Status supports `SUBMITTED`/`PROCESSING`/`SUCCEEDED`/`FAILED`/`DEAD_LETTERED` lifecycle | `IMPLEMENTED` | `docs/diagrams/status-flow.md`, `src/main/java/com/clearfolio/viewer/model/ConversionJobStatus.java` |
| AC-03 | HWP/HWPX blocked by default | `IMPLEMENTED` | `docs/diagrams/submit-flow.md`, `src/main/java/com/clearfolio/viewer/service/DefaultDocumentValidationService.java`, `src/main/resources/application.yml` |
| AC-04 | WAS preview prefetch via S2S | `PLANNED` | `docs/diagrams/preview-flow.md` |
| AC-05 | `/viewer/{docId}` state-gated responses (`200 SUCCEEDED`, `409` for `SUBMITTED`/`PROCESSING`/`FAILED`/`DEAD_LETTERED`, `404 not found`) | `IMPLEMENTED (MVP)` | `docs/diagrams/preview-flow.md`, `src/main/java/com/clearfolio/viewer/controller/ConversionController.java` |
| AC-06 | Standard error schema + trace in API responses | `IMPLEMENTED (submit/status/viewer)` | `ConversionController` exception mapping + `docs/diagrams/status-flow.md` + `docs/diagrams/preview-flow.md` |
| AC-08 | NUL sanitization at persistence boundary | `IMPLEMENTED` | `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`, `docs/diagrams/submit-flow.md` |

## Planned Evolution

1. Add persistent job store (PostgreSQL).
2. Replace in-memory repository with durable queue + worker pool.
3. Add retry policy, DLQ, and audit event emission.
4. Add content-hash based dedupe cache layer.
5. Complete preview orchestration path and S2S session handling for `/viewer/{docId}`.

## Runtime Configuration

- `conversion.blocked-extensions`: blocked upload extension list.
- `conversion.worker-threads`: worker pool size.
- `conversion.queue-capacity`: async queue depth.
