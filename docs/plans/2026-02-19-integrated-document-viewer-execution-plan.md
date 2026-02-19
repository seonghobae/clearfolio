# Integrated Document Viewer Execution Plan (PM Draft)

> I'm using the writing-plans skill to create the implementation plan.

## 1) Starting point and assumptions

- This plan is built from the existing scaffold in this repository (`src/main/java` and `docs/architecture.md`) and the previously prepared Pattern B execution notes.
- Source assumptions: API submits are non-blocking (`202 Accepted + jobId`), conversion is async, HWP/HWPX is blocked by default, and PDF output is the final viewer format.
- Scope assumed: end-to-end "integrated document viewer" includes backend conversion pipeline, object artifact handling, job status API, and web viewer integration (PDF.js).
- PRD exists as `docs/prd-integrated-document-viewer-platform.md`; this execution plan now references it as primary product guidance.
- Work is planned in weeks and aligns with a 10-week program baseline.

## 2) Phase plan and dependency map

| Phase | Objective | Key outputs | Depends on | Owner (A/R) |
|---|---|---|---|---|
| P1: Foundation | Lock requirements, API contract, and compliance baseline | AC list, API contract, security constraints, license baseline, runbook structure | User goals + AC set | PM (A), Product Lead (R) |
| P2: Core backend foundation | In-memory MVP stabilized into persistent, resilient queue-based conversion workflow | DB repository adapter, queue abstraction, worker orchestration, retry/DLQ policy | P1, existing code |
| P3: Conversion engine integration | Replace simulated worker with real conversion path and artifact persistence | Worker contract, container strategy, AV policy, hash dedupe service | P2 |
| P4: Viewer experience | Add secure document list, download endpoints, PDF.js UI, and status UX | Completed conversion artifact output and access control model | P2, P3 |
| P5: Operations and release readiness | Observability, smoke automation, runbooks, validation hardening | Integration done for major flows | P1-P4 |

## 3) WBS (work breakdown structure)

1.0 Program Management & Governance
- 1.1 Confirm PRD assumptions with Product Manager
- 1.2 Confirm success metrics and launch criteria
- 1.3 Register milestone and gating checklist issues/tickets

2.0 Product and API Baseline
- 2.1 Finalize API contracts (`submit`, `status`, `download`, `download token`, `health`)
- 2.2 Lock security policy (validation, rate limit, max file size, MIME checks)
- 2.3 Publish API/flow sequence diagrams and error model
- 2.4 Define DB naming convention: at least two-word snake_case for new tables/columns/indexes

3.0 Service Reliability and Data Layer
- 3.1 Implement persistent job store (`conversion_jobs`, `conversion_artifacts`, `conversion_events`) with short transactions
- 3.2 Replace in-memory repository with adapter-backed repository and transactional boundaries
- 3.3 Add read/write endpoint audit logs (`audit_events`) with append-only policy
- 3.4 Implement NUL sanitization policy at persistence boundaries and regression test
- 3.5 Add pooler/readonly detection (`PgBouncer`/`PgCat`) with safe fallback (`unknown`)
- 3.6 Add dedupe cache by content hash and reference counting

4.0 Queueing and Worker Orchestration
- 4.1 Select durable queue (RabbitMQ/Kafka/SQS) and implement producer/consumer adapter
- 4.2 Implement retry policy and DLQ handling (`max-attempts`, backoff, alertable dead-letter)
- 4.3 Enforce non-blocking submit path and bounded in-process backpressure
- 4.4 Add worker isolation for risky formats and policy-based routing
- 4.5 Add HWP/HWPX exception path design (manual/isolated worker, explicit approval)

5.0 Conversion, Security and Viewer Pipeline
- 5.1 Integrate converter runtime (JODConverter + LibreOffice container or equivalent approved path)
- 5.2 Implement artifact storage (immutable object paths), checksum validation, lifecycle retention
- 5.3 Implement conversion output metadata (`status`, timing, failure reason, output path)
- 5.4 Add AV/pre-scan and worker re-check gates before finalization
- 5.5 Add licensing scan and dependency blocking (GPL/AGPL/LGPL hard block)
- 5.6 Build PDF.js integrated viewer shell with secured signed download/tokenized access

6.0 Frontend, UX, and usability
- 6.1 Implement upload workflow page and status polling/streaming UI
- 6.2 Implement file type hints, block message UX, and queue feedback states
- 6.3 Apply UI/UX ver 3.0 checks: responsive breakpoints, fixed header, 50% modal overlay behavior, no horizontal scroll
- 6.4 Accessibility pass and action list for deviations

7.0 QA, validation, and release
- 7.1 Add unit/integration/contract tests around conversion, dedupe, and API errors
- 7.2 Add smoke tests in dockerized environment (delegated to QA/System-admin)
- 7.3 Add DB validation against real env if available; record result artifacts
- 7.4 Add canary, rollback, and operations runbooks
- 7.5 Complete release audit: security, license, performance, and gate approvals

## 4) Dependencies (critical path)

- `2.1 API contract` -> `4.1 Queue adapter` -> `5.1 Converter integration` -> `5.6 Viewer` -> `7.5 Release gate`
- `3.2 Repository persistence` -> `3.4 NUL policy` -> `7.3 DB validation`
- `4.2 Retry/DLQ` and `3.5 DB routing` are gating dependencies for operations hardening
- `5.5 License scan` must pass before `7.5` and before production cutover
- `6.3 UX checks` must be completed before UAT gate

## 5) Milestones and week-by-week plan

| Week | Milestone | Exit criteria |
|---:|---|---|
| W1 | P1 complete + Gate0 approval | AC list, constraints, owner matrix, risk register signed |
| W2 | API and data model v1 | API contract, migration draft, in-memory repo wrapped by repository adapter |
| W3 | Core async worker + queue scaffold | Submit/status flow async, at least one durable queue adapter in place |
| W4 | Conversion execution MVP | Real conversion path POC with one supported format + bounded retry |
| W5 | Resilience hardening | DLQ, AV/scan hook, hash cache, NUL test coverage |
| W6 | Viewer integration | PDF.js display path and secure artifact download |
| W7 | UI and non-functional quality | UX checklists, a11y baseline, telemetry hooks |
| W8 | Operational hardening | Audit logs, license enforcement, canary config, runbook v1 |
| W9 | Smoke and DB validation | Dockerized smoke, DB route/read policy validation evidence |
| W10 | Release readiness | Gates passed (security, license, smoke, rollback), UAT/go-live decision |

## 6) Deliverables

- Backend: conversion API set, queue adapters, worker service, DB schema, migration scripts, audit and event logs.
- Storage/infra: object artifacts repository, retention policy, signed access workflow.
- Frontend: upload/status/preview screens, download endpoints, error guidance, responsive behavior.
- Quality: test suites, smoke scripts, NUL-sanitize cases, EXPLAIN evidence set, DB validation report.
- Governance: risk log updates, decisions ledger, release checklists, rollback/incident runbook.

## 7) QA gates

### Gate 0: Baseline Readiness
Pass if: AC is signed, API contract is immutable for sprint, license policy exists, branch protections and issue template compliance are set, and dependencies (queue/DB/viewer) owners are assigned.

### Gate 3: Functional Integration
Pass if: async submit works under duplicate and concurrent load, conversion success path stores immutable output, retry and DLQ operate on error, and API response shapes are stable.

### Gate 7: Scale + Reliability
Pass if: failure scenarios (`DLQ`, `retry`, worker restart, converter timeout) are tested, hash dedupe returns expected hit behavior, AV and sanitization checks have logs.

### Gate 10: Release
Pass if: all high-priority ACs complete, smoke evidence captured, license/security scan green, rollback and runbooks approved, and PM/PMO sign-off obtained.

## 8) Resource assumptions

- Team setup (minimum): 1 Product Manager, 1 Project Manager, 2 Backend Engineers, 1 Frontend Engineer, 1 QA Engineer, 0.5 Security Lead (part-time), 0.5 DevOps/Platform.
- External dependencies: converter runtime image, optional managed queue service, object storage account, test PostgreSQL instance.
- Environment assumptions: no production DB writes in this plan unless explicitly approved; local/staging DB for verification.
- Calendar assumption: two developers per week can sustain 20-30 focused points; planning assumes average 2-day unplanned contingency over 10 weeks.

## 9) Decision points

- D1 (W2): Final converter runtime choice (JODConverter + LibreOffice container vs managed SaaS).
- D2 (W3): Queue stack (RabbitMQ vs Kafka vs managed queue).
- D3 (W4): Strict HWP/HWPX hard block vs controlled exception lane.
- D4 (W6): Viewer stack (PDF.js integrated SPA vs server-rendered embed).
- D5 (W8): Whether read-only DB routing is always-on or opt-in by deployment profile.
- D6 (W9): Go-live path (full cutover vs staged canary) based on smoke, SLO, and incident readiness.

## 10) Risk log (top priorities)

| ID | Risk | Impact | Probability | Mitigation | Owner |
|---|---|---:|---:|---|
| R1 | Converter crashes under complex documents | Sev0/Sev1 | Medium | Container isolation, health checks, circuit breaker, backpressure tests | Backend Lead |
| R2 | Queue growth causing request drops | High | Medium | DLQ + retries + capacity alerts + scaling policy | Platform |
| R3 | HWP/HWPX exception path expands scope | Medium | Medium | Formal exception approval, isolated worker, explicit SLA | Security |
| R4 | License contamination in transitive dependencies | High | Low | Automated license scan in CI with blocklist | Security |
| R5 | Data corruption from NUL sanitation | Medium | Low | Preserve originals outside DB, sanitize only text fields, explicit audit |
| R6 | PG pooling incompatibility (`PgBouncer` transaction pooling) | High | Medium | Auto detection, short transactions, SKIP LOCKED strategy |
| R7 | Missing UI/UX compliance causing rework late in cycle | Medium | Medium | Mid-sprint UX reviews; separate `ux-ui` validation stream |
| R8 | No reliable dockerized smoke test environment | Medium | Medium | QA/system-admin delegated smoke path plus local fallback checks |

## 11) Weekly execution operating mode (team cadence)

- Week 1 kick-off and Gate0 issue setup (PM-owned).
- Weekly design review with Product + Security on Friday.
- Mid-week dependency call for queue/DB/converter decisions.
- End-of-week gate artifact review: tests, logs, risk updates, burndown.
- Any Gate failure blocks merge of work for next sprint dependency chain until remediated.

## 12) Suggested artifact outputs

- `docs/plans/2026-02-19-integrated-document-viewer-execution-plan.md`
- `plans/milestones.md` (program calendar sync and KPI mapping)
- `plans/gate_risks.md` (risk evidence index)
- `docs/qa/smoke_test_plan.md` (already present, update with plan-specific pass/fail matrix)
- `docs/architecture.md` (must be updated with final queue, storage, and conversion path)
- `docs/trd-integrated-document-viewer-platform.md` (technical baseline for implemented scope and implementation gaps)
