# One-Day Delivery Workflow

Last updated: 2026-02-22

This workflow is the operator-facing path for delivering Clearfolio Viewer within one business day.

## Scope

- Applies to urgent customer handoff runs for the current MVP backend.
- Follows the mandatory AC policy in `docs/engineering/acceptance-criteria.md`.

## Delivery context

- S2S chain tracked for handoff: `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`.
- Current repo responsibility: Clearfolio Viewer API and non-blocking conversion/status/viewer behavior.
- MVP increment includes blocked-format policy-exception lane (`X-Clearfolio-Policy-Override` headers), viewer bootstrap adapter metadata (`sourceExtension`, `rendererAdapter`), and dead-letter operator retry lane (`POST /api/v1/convert/jobs/{jobId}/retry`).

## Mandatory AC list (exact)

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

## One-day runbook

| Window (KST) | Step | Output |
| --- | --- | --- |
| 09:00-10:00 | Align docs/contracts | Updated architecture/PRD/TRD/diagram docs |
| 10:00-12:00 | Stabilize runtime behavior | API + queue + blocked-format exception-lane + adapter-metadata validation |
| 12:00-15:00 | Close quality gates | Coverage + JavaDoc + warning/deprecated gates |
| 15:00-18:00 | Security verification | SAST and code-scanning evidence |
| 18:00-20:00 | Handoff package | `PR checks evidence` comment + handoff summary |

Detailed timeline and command set: `docs/plans/2026-02-20-24h-customer-delivery-plan.md`.

## Repro command baseline

```bash
mvn -q -DskipTests compile
mvn test
mvn -q -DskipTests javadoc:javadoc
markdownlint-cli2 "docs/**/*.md" > "docs/qa/evidence/<run-id>/markdownlint.log"
semgrep --config auto --metrics=off --error --json --output docs/qa/evidence/<run-id>/semgrep.json src/main/java
```

GitHub merge/security verification commands are maintained in `docs/plans/2026-02-20-24h-customer-delivery-plan.md`.

## Evidence handling rule

- Raw JSON/log outputs are attached to a PR comment titled `PR checks evidence`.
- Do not paste raw evidence payloads into PR body docs or release note prose.

## Optional tracks

- client DB pooler
- PostgreSQL 17

## Architecture linkage

- Root map: `ARCHITECTURE.md` (updated 2026-02-22).
- Architecture detail: `docs/architecture.md`.

## File-level documentation evidence

| File | Change(add/edit/delete/move) | Intent(의도) | Why(이유) | Risk/Notes |
|---|---|---|---|---|
| `docs/workflow/one-day-delivery-plan.md` | add | Provide single operational workflow for one-day delivery | Reduce ambiguity across plan/checklist docs | Keep synchronized with detailed plan timings |
| `docs/diagrams/submit-policy-adapter-flow.md` | add | Visualize submit exception lane and viewer adapter selection | Support audit/readiness handoff | Keep aligned with controller/service contracts |
| `docs/plans/2026-02-20-24h-customer-delivery-plan.md` | edit (existing baseline) | Preserve detailed timeline, commands, and execution log | Keep command-level reproducibility | Date-bound execution log must be refreshed on new run |
| `docs/qa/evidence/LATEST.md` | edit (existing baseline) | Stable pointer to latest evidence bundle | Fast access during handoff | Not a substitute for full run artifacts |
