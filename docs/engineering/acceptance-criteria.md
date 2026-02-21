# Engineering Acceptance Criteria

Last updated: 2026-02-21

This document is the canonical acceptance policy for the current Clearfolio Viewer delivery baseline.

## Mandatory AC list (exact)

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

## Runtime stance

- Non-blocking web runtime is implemented with WebFlux (`spring-boot-starter-webflux`) in current code.
- Servlet/MVC runtime is not the selected implementation for this repository baseline.

## Delivery context chain

- `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`
- Current implementation in this repo covers the Clearfolio Viewer side of the contract and state gating.

## Mandatory AC evidence mapping

| AC | Gate check | Repro command | Evidence pointers |
| --- | --- | --- | --- |
| coverage | JaCoCo line/branch miss = 0 | `mvn -q -Djacoco.includes=com.clearfolio.viewer.* org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report` | `docs/qa/evidence/2026-02-21-ac-gates/jacoco.csv` |
| docstring | JavaDoc warnings/errors = none | `mvn -q -DskipTests javadoc:javadoc` | `docs/qa/evidence/2026-02-21-ac-gates/javadoc.log`, `docs/qa/evidence/2026-02-21-ac-gates/javadoc-status.txt` |
| non-blocking web | Request path does not run conversion inline | N/A (code-path verification) | `src/main/java/com/clearfolio/viewer/controller/ConversionController.java`, `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java` |
| lightweight queue | Bounded queue + retry + dead-letter behavior | N/A (code-path verification) | `src/main/java/com/clearfolio/viewer/config/ConversionExecutorConfig.java`, `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java` |
| warning 0 | Compile path warning-free (`-Werror`) | `mvn -q -DskipTests compile` | `docs/qa/evidence/2026-02-21-ac-gates/compile.log` |
| deprecated 0 | Deprecated usage blocked by warning gate | `mvn -q -DskipTests compile` | `docs/qa/evidence/2026-02-21-ac-gates/compile.log` |
| 1-day schedule+security verification | Delivery plan and security checks completed | `semgrep --config auto --metrics=off --error --json --output docs/qa/evidence/<run-id>/semgrep.json src/main/java` and GitHub API checks in plan | `docs/plans/2026-02-20-24h-customer-delivery-plan.md`, `docs/qa/evidence/2026-02-21-ac-gates/semgrep.json`, `docs/qa/evidence/2026-02-21-ac-gates/gh-code-scanning-alerts-open.json` |

## Optional tracks

- client DB pooler
- PostgreSQL 17

## DB and queue operating policy (future persistent DB phase)

- Queue requests should not wait for completion in request path; use status polling/callback pattern.
- Keep DB transactions short; avoid external network calls inside transactions.
- Use timeout/retry and `SKIP LOCKED` for lock-contention-sensitive worker loops.
- Read routing uses provided read-only endpoint/DSN; lock-sensitive or strongly consistent flows stay on primary.
- Pooler detection is best-effort (`SHOW VERSION;` in `pgbouncer`/`pgcat` management DB), fallback state is `unknown`.

## Architecture linkage

- Root architecture map: `ARCHITECTURE.md` (updated 2026-02-21).
- Detailed architecture: `docs/architecture.md`.

## File-level documentation evidence

| File | Change(add/edit/delete/move) | Intent(의도) | Why(이유) | Risk/Notes |
|---|---|---|---|---|
| `docs/engineering/acceptance-criteria.md` | add | Canonicalize mandatory AC policy and evidence map | Prevent drift across PRD/TRD/plan docs | Keep run-id pointers current when evidence folder rotates |
| `docs/qa/acceptance_evidence_checklist.md` | edit (existing baseline) | Reusable detailed checklist | Preserve command-level reproducibility | Must stay aligned with AGENTS.md gates |
| `docs/qa/evidence/LATEST.md` | edit (existing baseline) | Latest evidence entrypoint | Fast operator lookup | Snapshot only, not historical trend |
