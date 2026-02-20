# Gate Risks and Mitigations

Top risks (minimum 5) and one-line mitigations:

1) LibreOffice conversion instability (OOM, crashes) — Mitigation: Run LibreOffice in a pre-warmed, containerized pool with strict memory limits, health checks and automatic restart/circuit-breaker; include stress tests in Gate7.

2) Unsupported/unsafe HWP/HWPX formats causing failures or security risk — Mitigation: Block HWP/HWPX by default; support only via an isolated, audited worker (separate queue/container, manual review, strict RBAC).

3) License contamination (GPL/AGPL/LGPL transitive dependency) — Mitigation: Enforce automated license scanning in CI, fail merges with banned licenses and require remediation before Gate7.

4) Blocking I/O in web server causing request starvation — Mitigation: Enforce non-blocking submit endpoints (enqueue pattern), ensure conversions run in background workers and validate with concurrency tests.

5) Queue overload / lost messages during peaks — Mitigation: Use durable queue with retry + DLQ, autoscale consumers, implement backpressure/rate-limiting and alerting on DLQ growth.

6) Data leakage from uploaded documents (sensitive data exposure) — Mitigation: Input scanning (secrets detection), encrypt-at-rest/transit, ephemeral storage with timely purge policy and access controls.

7) Storage bloat from duplicate artifacts — Mitigation: Content-hash dedupe on ingestion, reference counting and lifecycle rules to garbage-collect unreferenced artifacts.

8) Audit log tampering or loss — Mitigation: Append-only audit store with cryptographic signing/hashes, offsite backups and periodic integrity checks.

Notes:
- Each mitigation must be implemented with smoke/evidence attached to the corresponding Gate issue (logs, config, docs).
- Where DB behavior matters (PgBouncer/read-only routing), include detection and test verification steps in Gate checklists.
