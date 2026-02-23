# Clearfolio Viewer MVP (1-day Delivery Plan)

Date: 2026-02-23
Owner: Delivery PM (this repo)
Target: Clearfolio Viewer MVP implemented + PR merged-ready with strict gates evidence

This plan is designed for an existing Java/Maven repo with strict merge gates:

- `mvn -q -DskipTests compile` passes with warnings/deprecated budget = 0.
- `mvn test` passes.
- JaCoCo for production package remains 100% line/branch.
- `mvn -q -DskipTests javadoc:javadoc` passes with no warnings/errors.
- Markdown lint for changed docs passes.
- Security evidence is attached on PR (SAST + GitHub code scanning / required checks).

Canonical policy references:

- `AGENTS.md`
- `docs/engineering/acceptance-criteria.md`
- `docs/qa/acceptance_evidence_checklist.md`
- `docs/workflow/one-day-delivery-plan.md`

## Assumptions

- Plan file path: `docs/plans/2026-02-23-clearfolio-viewer-mvp-1day-delivery-plan.md`.
- Default branch is `main`; repository already builds on Java 21 + Spring Boot + Maven.
- MVP definition is backend-only (non-blocking submit, status polling, viewer bootstrap routes, retry/dead-letter lane) and matches `README.md` endpoint list.
- No Docker/Compose assets exist in this repo; smoke test uses local JVM fallback (document rationale as evidence).
- Tooling available on runner: `gh` authenticated, `semgrep` installed, `markdownlint-cli2` installed.

## Priority order (ranked)

| Rank | Issue/PR | Score | Why now | Next concrete step |
| --- | --- | --- | --- | --- |
| 1 | Create working branch + continuity check | 16 (5/5/5/3/0) | Avoid duplicate PRs; establish delivery traceability early | Run PR continuity check, then `git checkout -b feat/mvp-viewer-20260223` |
| 2 | Contract lock (routes + payload shapes + headers) | 18 (5/5/5/3/0) | Prevent churn that breaks tests/coverage late-day | Align with `README.md` + `docs/*` and freeze endpoint shapes |
| 3 | Implement non-blocking submit + status polling | 18 (5/5/5/3/0) | Core MVP behavior; must be stable before coverage push | Implement WebFlux handlers and ensure conversion is not executed inline |
| 4 | Implement lightweight queue + retry/dead-letter behavior | 17 (5/4/5/3/0) | Enables async semantics and operator retry lane | Bounded executor/worker + job state transitions + retry endpoint |
| 5 | Error contract + validation + blocked-format policy override lane | 16 (4/5/4/3/0) | Error shape drives client integration; policy override is MVP lane | Implement consistent `ApiErrorResponse` + header-based override |
| 6 | Tests to 100% line/branch (production package) | 19 (5/5/5/3/1) | Coverage gate is absolute; leaving it late is schedule risk | Add tests per class; capture `jacoco.csv` and verify missed=0 |
| 7 | JavaDoc closure (public production symbols) | 16 (4/4/5/3/0) | JavaDoc gate is strict; warning-free is easiest while context is fresh | Add/verify JavaDoc; run `mvn -q -DskipTests javadoc:javadoc` |
| 8 | Docs + diagrams refresh (minimal) | 12 (2/3/4/2/1) | Required for handoff; doc lint must pass | Update `docs/architecture.md` and/or `docs/diagrams/*` only if behavior changed |
| 9 | Security verification + required checks evidence | 18 (5/5/5/3/0) | Merge decision requires evidence; must run against PR/HEAD SHA | Run semgrep + GitHub code scanning / required checks capture |
| 10 | PR review workflow (CodeRabbit only) | 15 (3/5/5/2/0) | Review requirements can block merge even if gates pass | Trigger CodeRabbit review; resolve feedback; ensure required checks pass |

Scoring legend: Deadline/milestone (0-5) + Instructions/blocker-ness (0-5) + Labels urgency (0-5) + Impact/risk (0-3) + Effort inverse (0-2).

## Milestones / timeline

KST-based schedule (adjust to local timezone, preserve ordering/dependencies).

| Timebox | Milestone | Dependency | Output | Verification evidence |
| --- | --- | --- | --- | --- |
| 09:00-09:30 | Repo intake + continuity | None | Branch created; duplicate PRs avoided | `git status`, continuity JSON snapshot in PR comment |
| 09:30-10:15 | Contract lock | 09:00-09:30 | MVP endpoint spec frozen | Reference `README.md` + `docs/engineering/acceptance-criteria.md` |
| 10:15-12:30 | Core MVP implementation | 09:30-10:15 | Submit/status/viewer/retry endpoints + queue worker | Local curl smoke (health + one submit + one status) |
| 12:30-14:30 | Gate closure: tests + coverage 100% | 10:15-12:30 | Tests complete for all production classes | `docs/qa/evidence/<run-id>/jacoco.csv` + `test.log` |
| 14:30-15:30 | Gate closure: compile warnings/deprecations | 12:30-14:30 | Warning/deprecated budget=0 | `docs/qa/evidence/<run-id>/compile.log` |
| 15:30-16:15 | Gate closure: JavaDoc | 12:30-14:30 | JavaDoc warnings/errors=none | `docs/qa/evidence/<run-id>/javadoc.log` + `javadoc-status.txt` |
| 16:15-17:00 | Docs lint + minimal docs refresh | 10:15-12:30 | Updated docs (only if needed) | `docs/qa/evidence/<run-id>/markdownlint.log` |
| 17:00-18:00 | Security verification evidence | PR exists + HEAD known | Semgrep + GitHub code scanning snapshot | `semgrep.json`, `gh-code-scanning-*.json`, `gh-required-checks.txt` |
| 18:00-19:00 | PR hardening | 17:00-18:00 | CodeRabbit + review status unblocked | CodeRabbit thread links + `gh-merge-state.json` |
| 19:00-20:00 | Final evidence bundle + handoff | All above | Evidence folder committed; PR evidence comment posted | `docs/qa/evidence/<run-id>/SUMMARY.md` + PR comment titled `PR checks evidence` |

## Risks (top 5)

- Coverage gate risk: 100% line/branch means every new branch/exception path needs explicit tests.
- JavaDoc gate risk: any public symbol without JavaDoc (or doc warnings) blocks `javadoc:javadoc`.
- Compile gate risk: `-Xlint:all -Werror` means warnings/deprecations fail the build.
- Non-blocking risk: accidental inline conversion on request thread violates the WebFlux stance and can cause latency collapse.
- Merge readiness risk: branch protection (required checks / review requirements) can leave PR `BLOCKED` even when local gates pass.

## Next actions (non-interactive)

All commands are copy/pasteable. Replace placeholders in ALL CAPS.

### 0) Branch + continuity (existing PR-first)

```bash
# Best-effort PR continuity check (existing PR-first).
# If the current branch already has a PR, `gh pr view` will succeed.
gh pr view --json number,url,headRefName,headRefOid 2>/dev/null || true

git checkout -b BRANCH_NAME
git status

# After branch creation, verify whether a PR already exists for this head.
gh pr view --json number,url,headRefName,headRefOid 2>/dev/null || true
```

Notes:

- Choose `BRANCH_NAME` using your repo naming policy (e.g., via a branding/naming check) rather than inventing a name ad-hoc.

### 1) Implement MVP scope (definition lock)

MVP endpoints and invariants (backend):

- `POST /api/v1/convert/jobs` accepts multipart upload and returns quickly with `jobId` + `statusUrl`.
- `GET /api/v1/convert/jobs/{jobId}` polls job state.
- `POST /api/v1/convert/jobs/{jobId}/retry` re-queues dead-lettered jobs (operator lane).
- `GET /viewer/{docId}` is canonical HTML viewer entrypoint; bootstrap JSON remains at `/api/v1/viewer/{docId}`.
- Error payloads are stable and consistent (`ApiErrorResponse`), with validation for bad inputs.
- Blocked formats are rejected unless explicit policy override header is present.

### 2) Local verification (smoke)

```bash
mvn -q -DskipTests compile
mvn test
mvn spring-boot:run

# In another terminal
curl -sS http://localhost:8080/healthz
```

If Docker/Compose is missing, create evidence rationale file under `docs/qa/evidence/<run-id>/smoke-fallback-rationale.md` (see section 5).

### 3) Evidence bundle setup

```bash
RUN_ID="2026-02-23-mvp-1day"
EVIDENCE_DIR="docs/qa/evidence/${RUN_ID}"
mkdir -p "${EVIDENCE_DIR}"
```

### 4) Strict gates (compile/test/javadoc/coverage)

```bash
# Compile gate (warnings/deprecations must be 0 due to -Werror)
mvn -q -DskipTests compile > "${EVIDENCE_DIR}/compile.log" 2>&1

# Tests
mvn test > "${EVIDENCE_DIR}/test.log" 2>&1

# Coverage (JaCoCo)
mvn -q -Djacoco.includes=com.clearfolio.viewer.* \
  org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent \
  test \
  org.jacoco:jacoco-maven-plugin:0.8.13:report > "${EVIDENCE_DIR}/jacoco.log" 2>&1

cp "target/site/jacoco/jacoco.csv" "${EVIDENCE_DIR}/jacoco.csv"

# JavaDoc gate
mvn -q -DskipTests javadoc:javadoc > "${EVIDENCE_DIR}/javadoc.log" 2>&1
printf "%s\n" "PASS" > "${EVIDENCE_DIR}/javadoc-status.txt"
```

Note: `javadoc-status.txt` is a delivery artifact; if `javadoc.log` contains warnings/errors, replace `PASS` with a short failure reason and fix before proceeding.

### 5) Doc lint + smoke fallback rationale

```bash
npx -y markdownlint-cli2@0.11.0 "docs/**/*.md" > "${EVIDENCE_DIR}/markdownlint.log" 2>&1

# If no Docker/Compose smoke is possible, record rationale.
cat > "${EVIDENCE_DIR}/smoke-fallback-rationale.md" <<'EOF'
# Smoke fallback rationale

- Repo has no Docker/Compose assets.
- Smoke validation executed via local JVM run (`mvn spring-boot:run`) + HTTP checks.
- Minimum checks: `GET /healthz` + one submit/status flow if applicable.
EOF
```

### 6) Security verification (SAST + GitHub code scanning / required checks)

```bash
# GitHub CLI authentication must be configured for `gh pr` / `gh api` calls.
gh auth status

# SAST (Semgrep)
semgrep --config auto --metrics=off --error \
  --json --output "${EVIDENCE_DIR}/semgrep.json" src/main/java

# GitHub PR context (set these)
REPO_OWNER="REPO_OWNER"
REPO_NAME="REPO_NAME"
PR_NUMBER="PR_NUMBER"
HEAD_SHA="HEAD_SHA"

# Required checks + merge state snapshot
gh pr checks "${PR_NUMBER}" --required > "${EVIDENCE_DIR}/gh-required-checks.txt"
gh pr view "${PR_NUMBER}" --json mergeStateStatus,mergeable,reviewDecision,url,headRefOid,baseRefName \
  > "${EVIDENCE_DIR}/gh-merge-state.json"

# Check-runs snapshot (head SHA)
gh api "/repos/${REPO_OWNER}/${REPO_NAME}/commits/${HEAD_SHA}/check-runs" \
  > "${EVIDENCE_DIR}/gh-check-runs.json"

# Code scanning evidence scoped to PR
gh api "/repos/${REPO_OWNER}/${REPO_NAME}/code-scanning/analyses?pr=${PR_NUMBER}" \
  > "${EVIDENCE_DIR}/gh-code-scanning-analyses.json"
gh api "/repos/${REPO_OWNER}/${REPO_NAME}/code-scanning/alerts?pr=${PR_NUMBER}&state=open" \
  > "${EVIDENCE_DIR}/gh-code-scanning-alerts-open.json"
```

### 7) PR workflow (branch, commit, push, PR, CodeRabbit, required checks)

```bash
# Commit (keep commits small; avoid committing secrets)
git add -A
git commit -m "feat: implement Clearfolio Viewer MVP endpoints"

git push -u origin BRANCH_NAME

# Create PR (use --draft until gates are green)
gh pr create --base main --head BRANCH_NAME \
  --title "Clearfolio Viewer MVP (one-day delivery)" \
  --body "Implements MVP backend + evidence for strict gates.\n\nRefs: docs/engineering/acceptance-criteria.md" \
  --draft
```

CodeRabbit review commands (PR comment):

- `@coderabbitai review` (incremental) or `@coderabbitai full review` (fresh full pass)
- `@coderabbitai configuration` (diagnostics)
- `@coderabbitai resolve` (resolve CodeRabbit threads after fixes)

Required-checks workflow:

- Keep PR draft until `mvn` gates + security evidence are complete.
- After gates are green, mark PR ready and run: `gh pr checks PR_NUMBER --required`.

### 8) Evidence summary + PR evidence comment

Create/update:

- `docs/qa/evidence/${RUN_ID}/SUMMARY.md` (human-readable summary)
- `docs/qa/evidence/${RUN_ID}/context.txt` with `head_sha` and `pr_url`
- `docs/qa/evidence/LATEST.md` to point to `${RUN_ID}`

PR evidence comment rule:

- Post a PR comment titled `PR checks evidence` and include the evidence folder path `docs/qa/evidence/${RUN_ID}`.
- Include pointers to `gh-required-checks.txt`, `gh-merge-state.json`, `semgrep.json`, and `gh-code-scanning-alerts-open.json`.
- Do not paste raw JSON payloads into PR body.
