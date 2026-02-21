# Acceptance Evidence Summary

- generated_at: 2026-02-21
- runner: local CLI + GitHub API
- head_sha: `cebe6387c9329baa768fbdf351e3c6074036e136`
- pr_url: `https://github.com/seonghobae/clearfolio/pull/4`
- Coverage: `line_missed=0`, `branch_missed=0` (`jacoco.csv`)
- Tests: `Tests run: 116, Failures: 0, Errors: 0, Skipped: 0` (`test.log`)
- Compile gate: PASS (`compile.log`)
- JavaDoc gate: PASS (`javadoc-status.txt`)
- Markdown lint: PASS (`markdownlint.log`)
- Semgrep: `0` findings (`semgrep.json`)
- Code scanning analyses for PR: `3` (`gh-code-scanning-analyses.json`)
- Code scanning open alerts for PR: `0` (`gh-code-scanning-alerts-open.json`)
- PR merge state: `mergeStateStatus=BLOCKED`, `mergeable=MERGEABLE`, `reviewDecision=CHANGES_REQUESTED` (`gh-merge-state.json`)
- PR checks snapshot: see `gh-all-checks.txt`; required-check snapshot: `gh-required-checks.txt`
- Smoke fallback evidence: `docs/qa/evidence/2026-02-21-ac-gates/smoke-fallback-rationale.md`

## Notes

- Docker/compose assets are not present in this repo, so smoke validation used local JVM fallback per runbook policy.
- Current merge block reason is review policy (`required_approving_review_count=1`) and active robot review state (`CHANGES_REQUESTED`/pending re-review).
