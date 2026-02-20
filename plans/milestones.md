# 10-Week Milestones & Gates Implementation Plan

Goal: Build Pattern B document conversion pipeline (Java backend + PDF.js + JODConverter + LibreOffice + final PDF render) with non-blocking queue/worker architecture, content-hash caching, audit logs, HWP/HWPX blocked by default (exceptions via isolated worker), and strict license policy (NO GPL/AGPL/LGPL).

Architecture: Java (Spring Boot) backend exposing non-blocking submit endpoints; durable queue (RabbitMQ/Kafka) for conversion jobs; containerized conversion workers (JODConverter + LibreOffice) with a pre-warmed pool; object storage for immutable artifacts; PDF.js for final rendering in the browser; audit log store (append-only); content-hash cache (Redis or DB) to dedupe uploads.

Tech stack (pattern B): Java (Spring), JODConverter, LibreOffice (containerized), PDF.js, Queue (RabbitMQ/Kafka), Object storage (S3-compatible), PostgreSQL (job metadata), Redis (optional cache).

Constraints (must):
- HWP/HWPX blocked by default; exceptions require an isolated worker (separate queue/container, explicit approval).
- Non-blocking web API + background workers; retry + DLQ semantics for jobs.
- Content-hash based dedupe, audit logs, and tamper-evidence.
- No GPL/AGPL/LGPL dependencies allowed in code or transitive deps.
- If Docker/Compose files exist, dockerized smoke tests are delegated to QA/System-admin (see Gate checklists).

Week-by-week deliverables (Week1..Week10)

- Week 1 — Kickoff & Architecture (Owner: Tech Lead / TPM)
  - Confirm Pattern B and commit architecture diagram (/docs/architecture.md).
  - Define non-blocking API contract (202 + jobId) and job-status API.
  - Define queue model, retry/DLQ policy, audit log schema.
  - Create license policy doc and configure CI license-scan (fail on banned licenses).
  - Create risk register and assign owners.

- Week 2 — POC conversion & queue setup (Owner: Backend Lead)
  - POC: containerized JODConverter + LibreOffice converting DOCX->PDF.
  - Queue prototype with sample producer/consumer.
  - Define content-hash algorithm and sample dedupe.
  - Draft PDF.js integration plan.

- Week 3 — Core worker + API (Owner: Backend Lead)
  - Implement non-blocking submit endpoint and job-status API.
  - Implement conversion worker with retry + DLQ + audit logging.
  - Add content-hash dedupe in submission path.
  - Integrate basic PDF.js preview.
  - Prepare dockerized smoke-test playbook and create QA ticket.
  - Gate3 checkpoint at end of Week3.

- Week 4 — Storage & DB hardening (Owner: Infra Lead)
  - Object storage with immutable artifacts + reference counting.
  - PostgreSQL schema for job metadata; short transactions and SKIP LOCKED usage.
  - PgBouncer/pooler detection plan & read-only routing doc.

- Week 5 — HWP isolation / Licensing & Security (Owner: Security Lead)
  - Implement HWP/HWPX block-by-default behavior; isolate exception worker prototype.
  - Remediate any banned-licence dependencies discovered in CI.
  - Secrets management & audit log integrity (signing/hashing).

- Week 6 — CI/QA integration & smoke test readiness (Owner: CI/QA Lead)
  - Wire CI to run unit + integration tests.
  - Build docker-compose for local smoke test (if present) and handoff to QA/System-admin.
  - Document smoke test playbook and measurement points.

- Week 7 — E2E integration & scaling tests (Owner: QA Lead / Infra Lead)
  - Run full E2E tests (DOCX/ODT/PDF) and document failures.
  - Scale test workers; validate backpressure & DLQ handling.
  - Confirm audit logs, content-hash cache correctness, and storage dedupe.
  - Gate7 checkpoint at end of Week7.

- Week 8 — Performance tuning & hardening (Owner: Backend Lead)
  - Tune JODConverter/LibreOffice pool size and memory limits.
  - Optimize job scheduling and worker autoscaling rules.
  - Improve observability (metrics, dashboards, alerts).

- Week 9 — Release prep & runbooks (Owner: Release Manager)
  - Finalize release notes, migration/rollback runbooks, and ops runbook.
  - Prepare release candidate build and tag.
  - Prepare support contacts and incident playbook.
  - Flag branding/name decisions: 브랜딩 전문가 검토 필요

- Week 10 — Release & Cutover (Owner: Product Manager / Release Manager)
  - Execute canary/cutover plan; monitor SLOs; rollback if necessary.
  - Final Gate10 acceptance and sign-off.
  - Post-release smoke test run by QA/System-admin; collect artifacts & metrics.

Gates (detailed checklists)

Gate0 — Pre-kickoff readiness (Primary owner: Tech Lead / TPM; Approver: Eng Manager)
Checklist:
1. Architecture diagram (Pattern B) committed to /docs/architecture.md
2. License policy documented and CI license-scan configured (fails on GPL/AGPL/LGPL)
3. Repo skeleton + branch protection + CI baseline (build/test) in place
4. Issue/PR templates present in .github/
5. Owners assigned for backend, infra, QA, security, release
6. Risk register created and initial risks logged
7. Docker/Compose presence detected; if present, smoke-test delegation ticket created and assigned to QA/System-admin
8. Gate approval issue template created for subsequent gates
Acceptance: All items completed and documented in Gate0 issue; approver (Eng Manager) assigned

Gate3 — Early integration & safety (Primary owner: Backend Lead; Approver: Eng Manager)
Checklist:
1. JODConverter + LibreOffice POC converts DOCX->PDF in containerized environment
2. Non-blocking submit endpoint returns jobId and persists job metadata
3. Conversion worker implemented with retry policy + DLQ and documented backoff strategy
4. Content-hash dedupe implemented for incoming artifacts
5. Audit logging implemented for submission & conversion events (append-only)
6. Basic PDF.js render integration demonstrating final PDF preview
7. HWP/HWPX uploads rejected by default; exception path to isolated worker implemented (if allowed)
8. CI license check passes; banned licenses absent
9. Dockerized smoke-test playbook drafted and QA ticket created (Delegated to QA/System-admin)
Acceptance: Gate3 issue contains evidence artifacts (logs, CI passing, sample outputs); approver signs off on issue

Gate7 — E2E stability & scale (Primary owner: QA Lead / Infra Lead; Approver: Engineering Manager)
Checklist:
1. Full E2E runs for supported formats (DOCX/ODT/PDF) pass consistently
2. Isolated HWP worker validated in separate queue/cluster with strict isolation & audit
3. DLQ and retry metrics defined; monitoring alerts configured for DLQ growth
4. Content-hash cache and storage dedupe validated under load
5. Audit log integrity checks in place (signing/hashing) and retention policy documented
6. PgBouncer/pooler detection doc exists and read-only routing considerations documented
7. Performance/stress test results meet baseline SLA targets (documented)
8. Dockerized smoke tests executed by QA/System-admin with logs attached to Gate7 issue
9. Rollback and incident runbooks prepared and reviewed
Acceptance: Gate7 issue contains test evidence, metrics dashboards links, and QA smoke test logs; approver signs off

Gate10 — Release approval (Primary owner: Product Manager / Release Manager; Approver: Eng Manager + Product Manager)
Checklist:
1. All unit/integration/e2e tests green; CI status checks passing on main/RC branch
2. Release candidate (tag) created and artifacts available in registry
3. Container images scanned and registry vulnerability policy satisfied
4. Monitoring dashboards, SLOs, and alerting configured for production
5. Canary/deployment plan & rollback steps validated in staging
6. Final security & license sign-off obtained (legal)
7. QA smoke tests passed in dockerized environment (evidence attached)
8. Documentation updated: architecture, runbooks, support contacts
9. Stakeholders notified and cutover window scheduled
Acceptance: Gate10 issue approved and labeled gate:approved; release can proceed

Notes:
- HWP/HWPX: default rejection; any exception must be implemented as a separately isolated worker (separate queue, container, limited access) and explicitly approved through risk review
- License policy strict: any transitive GPL/AGPL/LGPL must be removed or replaced before Gate7
- Smoke test execution when Docker/Compose files exist is explicitly delegated to QA/System-admin (do not rely on dev environment)
- Any naming/branding choices in releases or UI: 브랜딩 전문가 검토 필요

Shortcuts / artifacts to attach to each Gate issue:
- architecture diagram (/docs/architecture.md)
- CI build link(s) and status
- sample conversion outputs (input → output)
- smoke-test logs (for dockerized runs, attached by QA/System-admin)
- license-scan report
- perf/stress test report (for Gate7)

End of plan
