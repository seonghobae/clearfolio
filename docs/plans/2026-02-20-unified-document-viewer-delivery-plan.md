# Unified Document Viewer Delivery Plan

> I'm using the writing-plans skill to create the implementation plan.

## 1) Requirements decomposition (non-breaking first)

Goal: integrate a unified document-viewer capability while preserving existing API contracts (`/api/v1/convert/jobs`, `/api/v1/convert/jobs/{jobId}`, `/viewer/{docId}`, and alias routes).

### A. Core behavioral requirements

- `F1` Maintain asynchronous submit/status workflow with immediate `202 Accepted`.
- `F2` Keep viewer entrypoint alias compatibility (`/viewer/{docId}`, `/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}`) and only add fields, not remove fields.
- `F3` Add production-grade queue worker hardening without changing request-level behavior (durable queue + retries + DLQ under adapter layer).
- `F4` Implement unified artifact lifecycle: content-hash dedupe -> conversion -> immutable artifact reference -> viewer bootstrap metadata.
- `F5` Build blocked-format security lane for `hwp`/`hwpx` with explicit audit-exception path.
- `F6` Route operational read operations through read-only-aware adapter when possible (best-effort); fallback to primary when unknown.
- `F7` Add data-cleanliness and resilience safeguards for persistence boundaries (NUL sanitization + bounded transactions + retry-on-contention policy).

### B. Backlog decomposition for execution

- `B1` Durable repository + schema migration strategy (job + events + artifact metadata).
- `B2` Durable queue + worker pool + idempotent dequeue contract.
- `B3` Artifact store and viewer bootstrap flow integration (`token` / `expiresAt` fields as additive response data).
- `B4` HWP/HWPX exception queue and policy enforcement with approver metadata.
- `B5` Release hardening: metrics, audits, license scan, and smoke evidence in dockerized environment.

## 2) Milestone & AC mapping

| Milestone | Scope | Required ACs (from `docs/prd-integrated-document-viewer-platform.md`) | Exit condition |
|---|---|---|---|
| M1: Contract lock + compatibility guard | Finalize non-breaking contract and update schema docs | AC-01, AC-02, AC-03, AC-08 | Existing endpoint behavior unchanged; compatibility tests added for aliases |
| M2: Platform foundation hardening | Replace in-memory repo with adapter-backed persistence + bounded queue/worker semantics | AC-06, AC-09, NFR-02 | Queue adapter + retry/DLQ policy configured and covered by unit/integration tests |
| M3: Conversion path integration | Integrate real converter + artifact persistence + signed preview bootstrap payload (additive) | AC-03, AC-04, AC-05 | Preview bootstrap returns prepared metadata on SUCCEEDED without altering 409/404 behavior |
| M4: Security and risk lanes | Implement blocked-list + exception lane audit + input governance | AC-04, AC-10, NFR-03 | Exception path requires explicit policy token; denied path remains immediate and deterministic |
| M5: Runtime readiness & release | Smoke, API contract, and operational evidence (release-grade) | AC-07, AC-08, AC-09 | CI + docker smoke + security + data validation evidence attached; all gates completed |

### Non-breaking mapping rule

- Contract changes are additive only: `statusMessage`, `traceId`, `viewerToken`, `artifactChecksum` and similar fields are added but existing clients should continue to parse current fields.

## 3) Required PRD / TRD / Architecture / UML updates

- `docs/prd-integrated-document-viewer-platform.md`
  - Add a dedicated section for **Compatibility Contract**: endpoint compatibility matrix, alias-preservation policy, and additive-response rule.
  - Add backlog items `B1..B5` and explicitly map each to phases and ACs.
  - Clarify non-breaking guardrails for query params, new headers, and error schema extension.

- `docs/trd-integrated-document-viewer-platform.md`
  - Update component boundary section to include:
    - `ConversionJobRepository` adapter for persistent store and `QueueAdapter` for queue operations.
    - `PreviewOrchestrator` path from conversion completion -> artifact bootstrap payload.
  - Update implementation status matrix to show `PLANNED` → `IMPLEMENTED` progression for worker queue, DLQ, audit trail, and exception lane.
  - Add explicit mapping notes for AC-06..AC-09 execution dependencies.

- `docs/architecture.md`
  - Replace in-memory-only wording with adapter-based runtime architecture.
  - Add a “Compatibility Layer” section describing how existing routes remain stable while enabling future internal replacement.
  - Add a small deployment runtime list: local dev in-memory, staging with durable queue/db, production with both read/write routing policies.

- UML updates in `docs/diagrams`
  - Update `docs/diagrams/submit-flow.md` with explicit `RepositoryAdapter` + `QueueAdapter` boxes.
  - Update `docs/diagrams/status-flow.md` with immutable event transition notation (SUBMITTED→PROCESSING→SUCCEEDED/FAILED/DLQ).
  - Update `docs/diagrams/preview-flow.md` with actual bootstrap path (tokenized session/manifest payload) while preserving `409`/`404` branch behavior.
  - Add a small combined end-to-end UML (`docs/diagrams/unified-viewer-flow.md`) for release reviewers.

## 4) CI and release evidence plan

- CI baseline
  - Add/maintain workflow to run `mvn test`, checkstyle, and build on every PR.
  - Add targeted tests for:
    - endpoint compatibility (`/viewer/{docId}` aliases, `202` contract, and existing error codes),
    - retry/DLQ branch, and
    - NUL sanitization for nested string fields.

- Dockerized smoke evidence
  - Authoritatively execute smoke commands in a dockerized compose profile and attach artifacts under `evidence/smoke/<run-id>/`.
  - Verify: startup, submit/status flow, alias route parity, blocked extension rejection, duplicate dedupe, failure path, and `viewer` state gating.

- Release artifacts required before gate
  - Test evidence bundle: `mvn test`, smoke report JSON, and smoke log.
  - Security evidence: blocked-format tests + policy token audit events + dependency/license scan.
  - Data/DB evidence: if real DB available, run migration + transaction/route checks, capture query plan or validation summary.
  - PR evidence package list in `docs/plans/*` should reference all evidence paths.

- Non-breaking release guard
  - No endpoint removal or response schema removal.
  - Any new endpoint is optional/adjunct and can be deployed behind the same ingress/controller path without client impact.
