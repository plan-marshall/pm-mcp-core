# Landing Analysis: PLAN-40 — Developer documentation in every module for the packages already shipped

epic: pm-mcp-core-m2-m4
workstream: WS-10
pr: #167 (merged as 2ee281335a0fa2e0c635489b75b4ce25a4eb52d3)

> Landing record for one shipped plan. Source: inbox message `rb-40-module-developer-docs-001.md`
> (runbook lane, opencode, 2026-10-24T16:10:03Z), corroborated against git at `2ee28133`, the PR through
> the `ci` abstraction, the lane's progress file (`.plan/temp/rb-40-module-developer-docs/progress.md`) and
> a links check run from the orchestrator. The message is a lead; every fact below marked "checked" was
> read from git, the PR or a run of mine, not taken from it.

Standing rules: r1, R13 and R17 named by the landing, as the spec names them.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — `pm-core` guides | shipped-as-specified | Checked: `git diff --name-status f52546cb 2ee28133` adds `core-analysis`, `core-findings`, `core-log`, `core-spi`, `core-toon` below `pm-core/doc/developer/`; `core-service` and `core-store` are `M` (extended, not overwritten); `pm-core/README.adoc` is `M`. `core-spi-validation` is not a file of its own: the spec says `core-spi.adoc` covers `core.spi.validation`, and the landing reports it as inside `core-spi.adoc` (not re-read). |
| D2 — `pm-workflow` guides | shipped-as-specified | Checked: `workflow-classification` and `workflow-log` added; `workflow-dsl` and `workflow-ast` extended; README `M`. The spec left `workflow-log` to "decide at outline"; the lane chose its own file. |
| D3 — `pm-runtime` guides | shipped-modified | Checked: six files added (`runtime-ingest`, `-lock`, `-log`, `-security`, `-web`, `-workspace`); `runtime-credentials`, `-enrolment`, `-start` extended. **`runtime-lsp.adoc` is not in the diff because it already existed** (checked: present on `origin/main`); the spec listed it as new, so its staleness is the spec's, not the lane's. `runtime-log` was written as its own file, the option the spec allowed. |
| D4 — provider guides | shipped-modified | Checked: `provider-github`, `provider-gitlab`, `provider-sonar` added in their modules; `provider-git` and `provider-http` extended. **The spec's `provider-ci.adoc` was not written: `pm-provider-ci` is gone since #156 (PLAN-86) and `provider-http.adoc` carries the shared HTTP base.** The lane reports this and the spec's Expected Surface still names `pm-provider-ci` (a HYPOTHESIS entry that cannot be true any more). |
| D5 — moved guides, package retirement | shipped-as-specified | Checked: `doc/plans/module-developer-docs.adoc` is `D` and the index row left `doc/plans/README.adoc` (no `module-developer-docs` match in the index on `origin/main`). The PR body (read through `ci pr view`) carries the seven-row table of old path, new path(s) and dropped copy, as the spec asks. The inbound pointers were rewired: `doc/Traceability.adoc`, seven specification files, `implementation-watch/credentials-and-enrolment.adoc` and ADR `core-store/003` are in the diff. |

Whole diff, checked: **44 `.adoc` paths** (16 `A`, 27 `M`, 1 `D`) and no `src/`, `test/` or `pom.xml` path — documents only, as the spec requires. The landing says "37 `.adoc` paths"; the measured figure is 44, so the landing's count is wrong by 7 and the claim "documents only" stands.

Done-when, clause by clause:

1. **Links check reports `BROKEN LINKS 0`, run recorded in the PR body — NOT MET as written.** Checked by re-running `trace.py` from the main checkout at `2ee28133`: `BROKEN LINKS 1`, `pm-runtime/doc/developer/runtime-enrolment.adoc:200` → `.../doc/requirements/12-migration.adoc`. The requirement file is `11-migration.adoc` (checked on the documentation repository's `origin/main`). The lane's own claim is accurate: its worktree run reported 0 because the sibling path did not resolve there. The break is real and open on `main`.
2. **Documents only, so the docs-only classification applies and no full `verify` is owed — NOT MET as written, with no harm.** The change is documents only (above), but the lane's `build-classify` answered `buildable` (the checkout holds no docs-only opt-in), so the lane's fail-closed direction ran a full `verify` (7325 tests, green at the merge head per the landing). The cost was time (one 1072 s run, two bounds that expired, two runs cut by a server restart), not correctness. The spec clause assumes an opt-in the checkout does not hold.
3. **Every package with production code in every module has its developer file — reported met, not re-run.** The landing counts 44 packages with a non-`package-info` file, each mapped to a guide. This verb checked that the 16 files the spec's scan named as missing exist (15 as new files plus `runtime-lsp`, already there; `core-spi-validation` inside `core-spi`), not the 44-package scan itself.
4. **The taken-over guides are complete here — reported met, not re-run.** The seven-row table is in the PR body; the guides stay in `plan-marshall-documentation` until the documentation session removes them (the spec says so).
5. **Relative links resolve — NOT MET as written**: the one link of clause 1.

## Metrics and Anomalies

- Tokens: unknown (opencode harness measured none; `inbox landing-check` reports `missing_keys: [total_tokens]`).
- Duration: not supplied.
- Anomalies:
  - **Three self-review rounds, the third found one minor defect, the round limit was reached** and the operator chose "proceed, report budget-exhausted" (`self-review:budget-exhausted` in `steps`, a true statement and not a defect of the landing). Rounds found 15, 8 and 1 findings.
  - CodeRabbit's review of `0f37d10e` carried 5 major and 18 minor findings; all handled (5 threads fixed and resolved, 14 minors fixed, 4 kept as documented preferences).  - The build gate was interrupted: one 545 s bound and one 1500 s bound expired, `LspClientTest.stubborn` failed once (`START_FAILED ... TIMEOUT`, the known flaky test), two runs were cut by a server restart. Green at `14068c3b` (merge head, 241 s).
  - Rule breaks the lane reported itself: one `git grep` (scoped, replaced by the `grep` tool), one shell `rm` of the named package file, `pgrep` for leftover builds. None touches the ledger.
  - The guard was `none on this harness (unenforced)`, as on the other opencode landings.
  - The primary checkout was pulled to `2ee28133` by the lane's cleanup, not by the orchestrator.

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `0f37d10e` at 14:00; re-triggered after the last push at 14:44 and rate-limited; merged under the standing review authorisation (qualifying review exists, every finding handled). Sourcery (optional) refused on its quota at 13:49 and never gated the merge.
- CI/merge: checked, PR #167 reads `merged`, head `feature/40-module-developer-docs`, merge commit `2ee281335a0fa2e0c635489b75b4ce25a4eb52d3`, first parent `f52546cb` (#166). The project's own `check-changes` classified the head as documentation-only and skipped `build / build`, per the landing; not re-read here.
- Sonar: `not_configured` (the standing missing-credential class, `sonar-gate --state not_configured` answered `merge_allowed: true`); the landing's `step.sonar-roundtrip.open_issue_count` is `n/a`, which is the honest answer for a project without Sonar configured, and a documents-only change has no Java to analyse. No Sonar read was made by this analysis.
- Collision: none observed with the runbook lanes in flight (PLAN-29, 31, 32). **Not predicted by the gate but expected:** this plan edited `pm-core`, `pm-workflow`, `pm-runtime` and four provider READMEs and the `doc/developer/` of those modules; PLAN-29, 31 and 32 write the developer files and ADRs of their own packages and may edit the same READMEs. They must merge the base (now `2ee28133`) before their merge gate (Watch).

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-40 --status shipped`
- [x] row `pr` stamped — `#167`
- [x] row `landing` stamped — `landings/PLAN-40.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-40-module-developer-docs` (from the sender)
- [x] epic.md narrative reconciled: queue annotation for PLAN-40, `total_tokens` recurrence folded into the existing Open Defect, one new Open Defect (the broken link), the serializer Watch retired, the `pm-provider-ci` Watch re-grounded, one new Watch (lanes in flight merge the base)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **One broken link is open on `main`: `runtime-enrolment.adoc:200`, `12-migration.adoc` should read `11-migration.adoc`.** The operator chose to record it in the landing rather than open a follow-up PR (Open Defect). It is a one-token fix that touches one file of this repository; the next plan that edits `pm-runtime/doc/developer/` (PLAN-29, 31 or 32 are writing beside it) can carry it, or it rides a documentation PR. Until fixed, every links check from the main checkout reports `BROKEN LINKS 1`, and every lane that records R17 against `main` will see the break.
- **The spec's Expected Surface still names `pm-provider-ci/doc/developer/`**, a path that does not exist. The row is terminal and the spec leaves the gate, so no declaration is corrected.
- **`pm-provider-ci` watch**: `doc/plans/module-developer-docs.adoc:8` left with this plan; the others stay (Watch updated).
- **`total_tokens=unknown`**: folded into the standing Open Defect (opencode runbook landing).
- **The landing counts 37 `.adoc` paths where the diff holds 44.** Recorded here; the lane's count is the weaker figure and nothing relies on it.
