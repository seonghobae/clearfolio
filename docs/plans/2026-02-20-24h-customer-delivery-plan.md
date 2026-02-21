# 24h Customer Delivery Plan (Integrated Viewer)

Date: 2026-02-20
Owner: Platform delivery team
Target: customer-deliverable package within one business day

## Scope

- Mandatory AC bundle:
  - 100% line/branch test coverage
  - 100% public JavaDoc coverage
  - non-blocking request path
  - lightweight event queue
  - warning 0
  - deprecated 0
  - security verification evidence

## One-day schedule

| Time (KST) | Workstream | Output | Verification gate |
|---|---|---|---|
| 09:00-10:00 | Contract lock | PRD/TRD/UML/README alignment | doc lint pass |
| 10:00-12:00 | Core implementation | API + queue + error contract stabilization | `mvn test` pass |
| 12:00-14:00 | Coverage closure | missing line/branch tests added | JaCoCo line/branch 100 |
| 14:00-15:00 | Docstring closure | JavaDoc on public symbols | docstring audit miss=0 |
| 15:00-16:00 | Build hygiene | warning/deprecated cleanup | compile warning/deprecated=0 |
| 16:00-18:00 | Security verification | SAST + code scanning gate evidence | no blocking security gate |
| 18:00-19:00 | Final smoke | core APIs and viewer routes smoke | smoke pass |
| 19:00-20:00 | PR evidence and handoff | PR comment with all evidence | review requested |

## Security verification commands

```bash
# Set target repository and PR before running GitHub code scanning checks.
REPO_OWNER="<owner>"
REPO_NAME="<repo>"
PR_NUMBER="<pr-number>"

semgrep --config auto --error --json --output target/semgrep.json src/main/java
gh api "/repos/${REPO_OWNER}/${REPO_NAME}/code-scanning/analyses?pr=${PR_NUMBER}"
gh api "/repos/${REPO_OWNER}/${REPO_NAME}/code-scanning/alerts?pr=${PR_NUMBER}&state=open"
```

## Delivery evidence checklist

- `target/site/jacoco/jacoco.csv`
- `target/semgrep.json`
- compile/test logs (warning/deprecated count)
- markdown lint output for updated docs
- PR checks and merge-state evidence comment

## Execution log (latest)

| Executed at (KST) | Runner | Scope | Result | Artifact |
|---|---|---|---|---|
| 2026-02-21 | local CLI | Compile gate (`mvn -q -DskipTests compile`) | PASS | `docs/qa/evidence/2026-02-21-ac-gates/compile.log` |
| 2026-02-21 | local CLI | Test gate (`mvn test`) | PASS | `docs/qa/evidence/2026-02-21-ac-gates/test.log` |
| 2026-02-21 | local CLI | Coverage gate (JaCoCo) | PASS (line/branch missed=0) | `docs/qa/evidence/2026-02-21-ac-gates/jacoco.csv` |
| 2026-02-21 | local CLI | Doc lint gate | PASS | `docs/qa/evidence/2026-02-21-ac-gates/markdownlint.log` |
| 2026-02-21 | local CLI | SAST (semgrep) | PASS (0 findings) | `docs/qa/evidence/2026-02-21-ac-gates/semgrep.json` |
| 2026-02-21 | GitHub API | Code scanning alerts (PR) | PASS (open alerts=0) | `docs/qa/evidence/2026-02-21-ac-gates/gh-code-scanning-alerts-open.json` |
