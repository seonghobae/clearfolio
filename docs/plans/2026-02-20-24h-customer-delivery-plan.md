# 24h Customer Delivery Plan (Integrated Viewer)

Date: 2026-02-20
Owner: Platform delivery team
Target: customer-deliverable package within one business day

## Scope and stance

- Runtime stance for this release: WebFlux non-blocking web path is adopted over Servlet/MVC.
- Delivery context chain: `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`.
- Scope boundary: this repo delivers Clearfolio Viewer API/runtime behavior; internal WAS/gateway/platform orchestration remains a documented integration track.

## Mandatory AC list (exact)

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

Canonical policy reference: `docs/engineering/acceptance-criteria.md`.

## One-day schedule

| Time (KST) | Workstream | Output | Verification gate |
|---|---|---|---|
| 09:00-10:00 | Contract lock | PRD/TRD/UML/README alignment | doc lint pass |
| 10:00-12:00 | Core implementation | API + queue + error contract stabilization (+ dead-letter retry endpoint) | `mvn test` pass |
| 12:00-14:00 | Coverage closure | missing line/branch tests added | JaCoCo line/branch 100 |
| 14:00-15:00 | Docstring closure | JavaDoc on public symbols | docstring audit miss=0 |
| 15:00-16:00 | Build hygiene | warning/deprecated cleanup | compile warning/deprecated=0 |
| 16:00-18:00 | Security verification | SAST + code scanning gate evidence | no blocking security gate |
| 18:00-19:00 | Final smoke | core APIs and viewer routes smoke | smoke pass |
| 19:00-20:00 | PR evidence and handoff | `PR checks evidence` comment + handoff summary | review requested |

## Workflow references

- Workflow entry: `docs/workflow/one-day-delivery-plan.md`
- Detailed plan (this file): `docs/plans/2026-02-20-24h-customer-delivery-plan.md`
- Acceptance evidence checklist: `docs/qa/acceptance_evidence_checklist.md`
- Architecture map: `ARCHITECTURE.md` (updated 2026-02-21)

## Security verification commands

```bash
# Set target repository and PR before running GitHub code scanning checks.
REPO_OWNER="<owner>"
REPO_NAME="<repo>"
PR_NUMBER="<pr-number>"
RUN_ID="<run-id>"
EVIDENCE_DIR="docs/qa/evidence/${RUN_ID}"
mkdir -p "${EVIDENCE_DIR}"

semgrep --config auto --metrics=off --error --json --output "${EVIDENCE_DIR}/semgrep.json" src/main/java
gh api "/repos/${REPO_OWNER}/${REPO_NAME}/code-scanning/analyses?pr=${PR_NUMBER}"
gh api "/repos/${REPO_OWNER}/${REPO_NAME}/code-scanning/alerts?pr=${PR_NUMBER}&state=open"
```

Raw security outputs and merge-gate JSON should be attached to a PR comment titled `PR checks evidence`.
Do not place raw evidence JSON in PR body documentation.

## Delivery evidence checklist

- `docs/qa/evidence/<run-id>/jacoco.csv`
- `docs/qa/evidence/<run-id>/semgrep.json`
- compile/test logs (warning/deprecated count)
- markdown lint output for updated docs
- PR checks and merge-state evidence comment (`PR checks evidence`)

## File-level evidence pointers

| File | Change(add/edit/delete/move) | Intent(의도) | Why(이유) | Risk/Notes |
|---|---|---|---|---|
| `pom.xml` | edit (existing implementation baseline) | Verify WebFlux runtime dependency exists | Mandatory non-blocking web AC evidence | No code change in this plan update; document pointer only |
| `src/main/java/com/clearfolio/viewer/controller/ConversionController.java` | edit (existing implementation baseline) | Verify request/response and state-gated routes | Non-blocking submit + viewer state contract evidence | Status endpoint remains simple lookup; conversion not inline |
| `src/main/java/com/clearfolio/viewer/config/ConversionExecutorConfig.java` | edit (existing implementation baseline) | Verify bounded queue configuration | Lightweight queue AC evidence | In-memory queue only (MVP) |
| `docs/qa/evidence/2026-02-21-ac-gates/SUMMARY.md` | edit (existing evidence baseline) | Reuse latest gate summary pointer | Keep delivery package traceable | Snapshot date-bound; rerun needed for new commit SHA |

## Execution log (latest)

| Executed at (KST) | Runner | Scope | Result | Artifact |
|---|---|---|---|---|
| 2026-02-21 | local CLI | Compile gate (`mvn -q -DskipTests compile`) | PASS | `docs/qa/evidence/2026-02-21-ac-gates/compile.log` |
| 2026-02-21 | local CLI | Test gate (`mvn test`) | PASS | `docs/qa/evidence/2026-02-21-ac-gates/test.log` |
| 2026-02-21 | local CLI | Coverage gate (JaCoCo) | PASS (line/branch missed=0) | `docs/qa/evidence/2026-02-21-ac-gates/jacoco.csv` |
| 2026-02-21 | local CLI | Doc lint gate | PASS | `docs/qa/evidence/2026-02-21-ac-gates/markdownlint.log` |
| 2026-02-21 | local CLI | SAST (semgrep) | PASS (0 findings) | `docs/qa/evidence/2026-02-21-ac-gates/semgrep.json` |
| 2026-02-21 | GitHub API | Code scanning alerts (PR) | PASS (open alerts=0) | `docs/qa/evidence/2026-02-21-ac-gates/gh-code-scanning-alerts-open.json` |

## Optional tracks (not executed in this one-day run)

- client DB pooler
- PostgreSQL 17
