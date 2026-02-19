# PRD: Integrated Document Viewer Platform (MVP)

Date: 2026-02-19
Owner: Product Manager
Sources: `docs/architecture.md`, `docs/trd-integrated-document-viewer-platform.md`, `docs/diagrams/*`

## 1. Problem

- Document conversion and preview are currently fragmented, so users wait on one-off flows and do not get a single predictable entrypoint.
- Synchronous processing patterns create avoidable request latency and operational risk.
- Security controls for risky formats (for example, HWP/HWPX) are present but need a formalized exception flow.

## 2. Goals

- Deliver one integrated flow: upload -> async processing -> status -> preview (`/viewer/{docId}`).
- Keep submission and status APIs non-blocking for request threads.
- Standardize conversion states, errors, tracing, and audit events for predictable operations.
- Build a production-hardened MVP baseline within current architecture.

## 3. MVP Scope

- Backend-first implementation that supports the end-to-end concept and contract.
- Asynchronous conversion orchestration with queue/worker pattern and explicit state transitions.
- Unified viewer bootstrap contract at `/viewer/{docId}` with safe state handling.
- Mobile-ready viewer shell behavior for phone/tablet fallback states.
- Security and data-cleanliness defaults for initial release.

## 4. In-Scope (Must) / Out-of-Scope (Out)

### In-Scope

- `POST /api/v1/convert/jobs` submission endpoint.
- `GET /api/v1/convert/jobs/{jobId}` status endpoint.
- `GET /viewer/{docId}` and implemented aliases (`/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}`).
- Async worker path with states `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DEAD_LETTERED`.
- HWP/HWPX block-by-default with approver-traceable exception lane.
- Retry and DLQ design for failed background jobs.
- Standard API error schema (`errorCode`, `message`, `traceId`, `details`) and request tracing.
- Input sanitization rule for NUL bytes at persistence boundary (recursive for nested payload fields).

### Compatibility Contract (MVP must hold)

- `POST /api/v1/convert/jobs` is non-blocking and returns `202` with
  `jobId`, `status`, and `statusUrl`.
- `GET /api/v1/convert/jobs/{jobId}` remains stable and includes
  `attemptCount`, `maxAttempts`, `retryAt`, and `deadLettered` in status payload.
- `GET /viewer/{docId}` is canonical; aliases (`/api/v1/viewer/{docId}` and
  `/api/v1/convert/viewer/{docId}`) behave equivalently.
- Terminal state HTTP semantics are preserved as
  `409` for `SUBMITTED`/`PROCESSING`/`FAILED`/`DEAD_LETTERED` and `404` for missing.
- API compatibility retains both `errorCode` and compatibility `code` where already present.

### Viewer/Session Integration Baseline

- S2S preview session bootstrap is planned and documented as a post-success shell
  orchestration extension (viewer-path decision point), but does not change current
  contract of status/viewer state response.

### Out-of-Scope

- OCR, annotations, inline collaboration, and full enterprise permission matrix.
- Real-time push updates (SSE/WebSocket) in MVP.
- Large-scale UI rewrite or replatforming of downstream viewers.
- External DB DDL/schema changes outside project-owned schema.

### Security Lanes (MVP baseline)

- Normal lane: accepted format files that are not blocked pass async pipeline and can enter `PROCESSING`.
- Blocked lane: `hwp`/`hwpx` (and configured blocked extensions) return `400` with structured reason immediately.
- Exception lane: blocked-format upload can proceed only with explicit approver metadata and policy token retained in audit trail.
- Recovery lane: failed jobs route to retry/DLQ path and can be reprocessed only through operator action.

## 5. User Stories

- As a user, I can submit a supported document and receive `jobId` quickly to continue without waiting.
- As a user, I can query `GET /api/v1/convert/jobs/{jobId}` and understand exact status.
- As a user, I can open `/viewer/{docId}` and get immediate preview when ready or a clear next action when pending/failing.
- As a security operator, I can enforce blocked-format policy and log each exception request with approver context.
- As an SRE, I can observe queue pressure, retry patterns, and DLQ spikes to prevent service degradation.

## 6. Success Metrics

- Submit API latency (P95, excluding raw upload I/O): **<= 250ms**.
- Submit -> status-visible latency (P95): **<= 8s**.
- Submit -> first preview frame for success path (P50): **<= 3s**.
- Supported-format success rate: **>= 95%**.
- State consistency/transition error rate (duplicate or missing transitions): **<= 2% P95**.
- Viewer bootstrap success for ready jobs (30s window): **>= 99%**.
- DLQ/time to alert threshold breach: no sustained breach >10 minutes without alarm.

## 7. Functional Requirements

- FR-01: Validate submission input and file metadata; reject invalid payloads with standard error format.
- FR-02: Return `202 Accepted`, `jobId`, `status`, and `statusUrl` on valid submission.
- FR-03: Ensure submission and status endpoints never run conversion logic inline.
- FR-04: Persist immutable job metadata and transition states for every lifecycle event.
- FR-05: Provide idempotent behavior for duplicate uploads through deterministic hash or equivalent dedupe signal.
- FR-06: Return HTTP-appropriate states for viewer access: success with bootstrap info, conflict for in-progress/failed/dead-lettered, and 404 for missing.
- FR-07: Record structured events for exception-driven formats, retries, and dead-lettered jobs.
- FR-08: Provide clear retry policy config (attempts, interval, backoff, max age).
- FR-09: Route read traffic to read-only DB endpoint when provided; otherwise safe default to primary.
- FR-10: Blocklist enforcement and approved-override path must be auditable and explicit.
- FR-11: Provide mobile shell behavior for `/viewer/{docId}` with no horizontal scroll, touch-first controls, and explicit loading/error states.

## 8. Non-Functional Requirements

- NFR-01: Non-blocking API behavior for request handling; long jobs must execute in worker/executor contexts.
- NFR-02: Short DB transactions, bounded lock windows, and retry/backoff on contention/deadlock.
- NFR-03: Best-effort detection for PgBouncer/PgCat (`version`/health checks); unknown states treated as safe fallback.
- NFR-04: New DB object names must use at least two-word snake_case.
- NFR-05: Security baseline includes authn/authz, traceability, rate limits, and audit logs for sensitive operations.
- NFR-06: API, smoke, and data validation evidence must be run in dockerized environment for pre-release.
- NFR-07: Mobile shell standards: no horizontal overflow on Phone (<768px), responsive layout at 768/1280 breakpoints, and visible loading/empty/error states.
- NFR-08: `/viewer/{docId}` interaction ordering for buttons/commands follows the standard sequence: 조회 → 입력 → 저장 → 수정 → 출력 → 화면이동 where applicable.
- NFR-09: Build-time warning count is 0 (compiler/test runtime warning budget is fail-fast in CI evidence).
- NFR-10: Deprecated API usage is 0 in production code and test gates.

## 9. Acceptance Criteria

- AC-01: `POST /api/v1/convert/jobs` responds with 202 within the target P95 and no inline conversion execution.
- AC-02: Status endpoint reliably returns current state and supports polling with stable schema.
- AC-03: Viewer route is state-gated and does not leak intermediate artifacts.
- AC-04: HWP/HWPX blocked in default path; exception path requires explicit approval metadata.
- AC-05: NUL handling is applied once at persistence edge and tested for plain and nested string values.
- AC-06: Retry, DLQ, and audit logs are observable with at least one complete failure simulation cycle.
- AC-07: Release gate evidence includes smoke test, API-E2E, security checklist, and data validation summary.
- AC-08: Viewer shell exposes loading/error/empty states and mobile interaction rules consistently at viewer entry.
- AC-09: Test coverage is 100% for tracked production classes (line/branch) with reproducible JaCoCo evidence.
- AC-10: Docstring coverage is 100% for public production API surfaces (class/interface/record/enum and public methods/constructors).
- AC-11: Request path remains non-blocking and heavy work is delegated to asynchronous worker queue.
- AC-12: Lightweight event queue is bounded, retry-capable, and dead-letter aware under failure conditions.
- AC-13: Warning count is 0 in verification command outputs.
- AC-14: Deprecated API usage count is 0 in verification command outputs.

### Acceptance Mapping (Current Implementation)

- AC-01 **IMPLEMENTED**: submit endpoint is async and returns `202` immediately.
  - Evidence: `src/main/java/com/clearfolio/viewer/controller/ConversionController.java`, `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`.
- AC-02 **IMPLEMENTED**: status lifecycle includes `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `DEAD_LETTERED` with polling contract.
  - Evidence: `src/main/java/com/clearfolio/viewer/model/ConversionJobStatus.java`, `docs/diagrams/status-flow.md`.
- AC-03 **IMPLEMENTED**: `/viewer/{docId}` returns bootstrap on success and `409` for in-progress/failed/dead-lettered.
  - Evidence: `src/main/java/com/clearfolio/viewer/controller/ConversionController.java`, `src/main/java/com/clearfolio/viewer/api/ViewerBootstrapResponse.java`.
  - Updated implementation-equivalent contract includes `FAILED` and `DEAD_LETTERED` as `409` terminal cases.
- AC-04 **PARTIAL**: blocklist for HWP/HWPX is implemented; explicit exception lane is documented but not productionized.
  - Evidence: `src/main/java/com/clearfolio/viewer/service/DefaultDocumentValidationService.java`, `docs/diagrams/preview-flow.md`.
- AC-05 **IMPLEMENTED**: NUL sanitization is applied at persistence edge for string fields.
  - Evidence: `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`.
- AC-06 **PLANNED**: retry/DLQ and operational audit trail are partially designed, not fully implemented in production-grade form.
  - Evidence: `docs/trd-integrated-document-viewer-platform.md`, `docs/diagrams/submit-flow.md`.
- AC-07 **PLANNED**: dockerized smoke/test/security/data validation evidence is required before release but not fully completed in this repo state.
  - Evidence: `docs/qa/smoke_test_plan.md`.
- AC-08 **PLANNED**: mobile viewer shell states are defined for roadmap and require responsive UI validation before sign-off.
- AC-09 **IMPLEMENTED**: JaCoCo report is generated with line/branch 100% evidence for current production classes.
  - Evidence: `target/site/jacoco/jacoco.csv`.
- AC-10 **IMPLEMENTED**: public production API surfaces are documented with JavaDoc.
  - Evidence: `src/main/java/**/*.java`.
- AC-11 **IMPLEMENTED (MVP)**: request thread returns quickly and conversion work is offloaded to worker queue.
  - Evidence: `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`, `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`.
- AC-12 **IMPLEMENTED (MVP)**: bounded async executor + retry/dead-letter flags are present in current queue path.
  - Evidence: `src/main/java/com/clearfolio/viewer/config/ConversionExecutorConfig.java`, `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`.
- AC-13 **IMPLEMENTED**: compiler and test execution are configured to fail on warnings.
  - Evidence: `pom.xml` (`maven-compiler-plugin`, `maven-surefire-plugin`).
- AC-14 **IMPLEMENTED**: build is configured to fail on deprecated usage warnings.
  - Evidence: `pom.xml` (`-Xlint:all`, `-Werror`).

## 10. Release Plan

- **Week 1-2:** Contract freeze, schema/queue adapter design, and API validation hardening.
- **Week 3-5:** Queue/worker hardening, retries, DLQ, and status/state contracts complete.
- **Week 6-8:** Viewer bootstrap integration, security/audit instrumentation, exception flow implementation, and mobile shell baseline.
- **Week 9-10:** Dockerized smoke run, NFR compliance checks, docs/sign-off, and go-live criteria.

## Decisions / Assumptions / Risks

- Decision: `/viewer/{docId}` is the canonical user entry and must remain backward-compatible with current alias routes.
- Assumption: Production conversion runtime can be integrated behind the existing worker contract without API contract breakage.
- Assumption: `read-only` DB endpoints may be available and should be used for read-biased flows when safe.
- Risk: converter engine selection and rollout strategy may shift timeline by 1-2 weeks.
- Risk: strict HWP/HWPX exception workflow can expand scope if policy ownership is unclear.
