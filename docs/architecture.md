# Conversion Service Architecture

Last updated: 2026-02-22

This repository currently ships an MVP backend for integrated document conversion/viewer entry with a non-blocking web stack.

## Current implementation stance

- Web runtime stance: this implementation adopts Spring WebFlux (`spring-boot-starter-webflux`) over Servlet/MVC for request handling.
- Non-blocking contract stance: request handlers return quickly and conversion work is delegated to the worker queue.
- Scope boundary: S2S preview-session orchestration is documented but still planned, not completed.

## Runtime flow

- Submit flow (`POST /api/v1/convert/jobs`): validation -> blocked-format policy evaluation (default block, optional auditable override headers) -> content hash dedupe -> enqueue async conversion -> return `202`.
- Status flow (`GET /api/v1/convert/jobs/{jobId}`): return lifecycle snapshot (`SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`) with retry metadata.
- Preview flow (`GET /viewer/{docId}` and aliases): return bootstrap on `SUCCEEDED` with deterministic `sourceExtension`/`rendererAdapter`; return `409` for not-ready/failed states; return `404` when missing.
- Health flow (`GET /healthz`): readiness probe.

## S2S delivery chain (documented target)

- `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`
- Current state: viewer endpoint contract is implemented in this repo; downstream S2S session orchestration remains planned and is documented in `docs/diagrams/preview-flow.md`.

## Mandatory AC list (exact)

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

Reference policy: `docs/engineering/acceptance-criteria.md`.

## Optional tracks

- client DB pooler
- PostgreSQL 17

## Component boundaries

- `controller`: HTTP endpoints and exception mapping.
- `service`: validation, policy-override exception lane handling, conversion orchestration, worker execution.
- `repository`: job persistence abstraction.
- `model`: lifecycle state and retry/dead-letter metadata.
- `config`: conversion properties and executor resources.

## Non-blocking, queue, and DB operation rules

- Request path non-blocking by default: heavy conversion runs in `DefaultConversionWorker`, not in API handlers.
- Queue flow in request path does not wait for completion; clients poll status endpoint.
- Queue policy baseline: bounded executor, retry scheduling with backoff, dead-letter fallback.
- DB/transaction policy (for future persistent DB phase): keep transactions short, avoid external calls inside transactions, use timeout/retry and `SKIP LOCKED` where applicable.
- Read-only routing policy (future DB phase): use provided read-only endpoint/DSN for read-biased traffic; strong consistency/DDL/lock-sensitive paths stay on primary.
- Pooler detection policy (best effort, future DB phase): in management DB `pgbouncer`/`pgcat`, try `SHOW VERSION;`; if detection fails, treat as `unknown` and keep safe fallback.
- Distributed Postgres compatibility policy: for Citus/Cosmos DB for PostgreSQL (Hyperscale)-style deployments, automatic read split is disabled by default (opt-in only).

## OSS references (implementation and concept)

| OSS repo | License | Usage status | Trade-off note |
| --- | --- | --- | --- |
| `spring-projects/spring-framework` | Apache-2.0 | Implemented (WebFlux runtime) | Strong reactive stack, but requires careful blocking-code isolation. |
| `reactor/reactor-core` | Apache-2.0 | Implemented (reactive primitives) | Good async composition, but debugging stack traces can be harder than imperative flow. |
| `apache/tika` | Apache-2.0 | Implemented (document metadata/parsing support) | Broad format support, but parser footprint can increase dependency surface. |
| `jodconverter/jodconverter` | Concept-only (license/legal review pending in this repo) | Planned concept, not integrated | Useful LibreOffice bridge, but package/image/license governance must be cleared before adoption. |

## Evidence pointers (file-level)

| Evidence target | File pointer |
| --- | --- |
| WebFlux dependency | `pom.xml` |
| Submit non-blocking controller path | `src/main/java/com/clearfolio/viewer/controller/ConversionController.java` |
| Blocked-format override lane + audit signal | `src/main/java/com/clearfolio/viewer/service/DefaultDocumentValidationService.java` |
| Override header contract | `src/main/java/com/clearfolio/viewer/service/PolicyOverrideRequest.java` |
| Conversion enqueue orchestration | `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java` |
| Worker retry/dead-letter behavior | `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java` |
| Bounded queue configuration | `src/main/java/com/clearfolio/viewer/config/ConversionExecutorConfig.java` |
| NUL sanitization at persistence boundary | `src/main/java/com/clearfolio/viewer/model/ConversionJob.java` |
| Viewer adapter selection metadata | `src/main/java/com/clearfolio/viewer/api/ViewerBootstrapResponse.java` |
| Mandatory gate evidence index | `docs/qa/evidence/LATEST.md` |
| Latest gate summary | `docs/qa/evidence/2026-02-21-ac-gates/SUMMARY.md` |

## Related design docs

- `ARCHITECTURE.md` (root architecture map, updated in this change)
- `docs/prd-integrated-document-viewer-platform.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/diagrams/submit-policy-adapter-flow.md`
