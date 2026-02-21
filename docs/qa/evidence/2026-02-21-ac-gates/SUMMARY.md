# Acceptance Evidence Summary

- generated_at: 2026-02-21
- runner: local CLI + GitHub API
- head_sha: `PENDING_POST_COMMIT`
- pr_url: `PENDING_POST_PR`
- Coverage: `line_missed=0`, `branch_missed=0` (`jacoco.csv`)
- Tests: `Tests run: 115, Failures: 0, Errors: 0, Skipped: 0` (`test.log`)
- Compile gate: PASS (`compile.log`)
- JavaDoc gate: PASS (`javadoc-status.txt`)
- Markdown lint: PASS (`markdownlint.log`)
- Semgrep: `0` findings (`semgrep.json`)
- Code scanning analyses for PR: pending refresh (`gh-code-scanning-analyses.json`)
- Code scanning open alerts for PR: pending refresh (`gh-code-scanning-alerts-open.json`)
- PR merge state: pending refresh (`gh-merge-state.json`)
- PR checks snapshot: see `gh-all-checks.txt`; required-check snapshot: `gh-required-checks.txt`
- Smoke fallback evidence: `docs/qa/evidence/2026-02-21-ac-gates/smoke-fallback-rationale.md`

## Notes

- Docker/compose assets are not present in this repo, so smoke validation used local JVM fallback per runbook policy.
- Replace pending head/PR placeholders after commit + PR checks refresh.
