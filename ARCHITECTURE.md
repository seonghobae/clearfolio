# Architecture Map

Last updated: 2026-02-21

## System Purpose

Clearfolio Viewer is an MVP backend that accepts document uploads, processes conversion asynchronously, exposes conversion status, and serves viewer bootstrap metadata when conversion succeeds.

Runtime stance: Spring WebFlux is adopted as the current non-blocking web runtime (Servlet/MVC path is not the selected implementation for this repo).

S2S chain (target integration path): `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`.
Current state: viewer/state API is implemented in this repository; downstream S2S orchestration remains planned and documented.

## Runtime Components

- `ConversionController` (`src/main/java/com/clearfolio/viewer/controller/ConversionController.java`)
  - `POST /api/v1/convert/jobs`: async submit contract.
  - `GET /api/v1/convert/jobs/{jobId}`: status polling.
  - `GET /viewer/{docId}` (+ aliases): viewer bootstrap/state-gated responses.
- `DefaultDocumentConversionService` (`src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`)
  - Validation, content hash generation, dedupe lookup, repository persistence, worker enqueue.
- `DefaultDocumentValidationService` (`src/main/java/com/clearfolio/viewer/service/DefaultDocumentValidationService.java`)
  - Enforces extension blocklist and size limits.
- `DefaultConversionWorker` (`src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`)
  - Runs conversion on a bounded executor with retry scheduling and dead-letter fallback.
- `InMemoryConversionJobRepository` (`src/main/java/com/clearfolio/viewer/repository/InMemoryConversionJobRepository.java`)
  - In-memory job store and content-hash dedupe index.
- `ConversionJob` (`src/main/java/com/clearfolio/viewer/model/ConversionJob.java`)
  - Domain lifecycle and retry metadata (`attemptCount`, `maxAttempts`, `retryAt`, `deadLettered`).

## State Model

- Status values: `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
- Retry-exhausted terminal state remains `FAILED` and is identified by `deadLettered=true`.

## Operational Gates

- Build and test gates are defined in `AGENTS.md` and include:
  - `mvn -DskipTests compile`
  - `mvn test`
  - JaCoCo line/branch 100% for `com.clearfolio.viewer.*`
  - JavaDoc gate: `mvn -q -DskipTests javadoc:javadoc`
  - Markdown lint for changed docs

Mandatory AC list (exact):

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

Optional tracks:

- client DB pooler
- PostgreSQL 17

## Detailed Design Docs

- `docs/architecture.md`
- `docs/prd-integrated-document-viewer-platform.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/engineering/acceptance-criteria.md`
- `docs/workflow/one-day-delivery-plan.md`
