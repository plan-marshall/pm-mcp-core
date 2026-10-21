# Landing Analysis: PLAN-90 — Close the ported-test advisories of PLAN-37

epic: pm-mcp-core-m2-m4
workstream: WS-06
pr: #120 (https://github.com/plan-marshall/pm-mcp-core/pull/120), merged as squash `73ec37a` on 2026-10-20

> Landing record for one shipped plan. Lives at `landings/PLAN-90.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `rb-90-ported-tests-advisories-001.md` (`kind: landing`, sender `rb-90-ported-tests-advisories`, runbook lane on opencode, model `deepseek-v4.1-flash`, plan-reported). `inbox landing-check`: `complete: false`, `missing_keys: [total_tokens]`.

## Deliverable Fidelity vs Spec

The landing counts four deliverables (`deliverables_total=4`, `deliverables_done=4`).

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 Stale "Rule 6" comments on three fixtures | shipped-as-specified | `git show --stat 73ec37a` lists `deletion.txt`, `head-change.txt`, `non-ascii-path.txt` (5 lines each, +3/-2); `grep -rn "Rule 6"` over `src/test/resources/ported` at `main` finds no hit |
| D2 Nested-class sentence in `provider-git-ported.adoc` | shipped-as-specified | the file changed by one line in the squash (`doc/developer/provider-git-ported.adoc`, 1+/1-); the code fact behind it (`refusesScenario` outside the nested classes) is the landing's, not re-read by the orchestrator |
| D3 Missing `@throws` on the ported test helpers | shipped-modified | `PortedFixtures.java` +11/-1 (`load`, `read`, `roster`), `WorktreeShaScenario.java` +1 (`unescape`); `run` read at `73ec37a`: `@throws IOException` and `@throws GitAPIException` are both present at lines 126–127, so the landing's refutation of the advisory holds. Modified: a probe was added to the `refusesRoster` test (`WorktreeShaPortedTest.java`, +3/-1, a `duplicate fixture` refusal case), a file the spec did not declare |
| D4 Package line in `pm-provider-git/README.adoc` | shipped-as-specified | line 13 of the README and the first sentence of `ported/package-info.java` (lines 9–10, "…whose `plan-marshall` original is classified `Ported` in the migration inventory") read the same |

Hypothesis of the spec, as the landing settles it: "the sentence at `provider-git-ported.adoc:218` and the wording at `README.adoc:13` are inexact" — confirmed by the diff of both files; the exact old wording was not re-read.

"No production code, no test assertion and no fixture directive changes": `git show --stat 73ec37a -- pm-providers/pm-provider-git/src/main` lists nothing. One assertion was ADDED to an existing test (the duplicate-fixture case); no existing assertion changed.

Surface delta (`inbox landing-check`): `unmeasured` (neither side supplied). Orchestrator count against the spec's `## Expected Surface`: 8 files merged; 7 were declared; undeclared: `WorktreeShaPortedTest.java` (`added`). Declared and untouched (`missing`): none.

## Metrics and Anomalies

- Tokens: `unknown` (the opencode harness measured none). The fourth runbook landing on opencode carrying it. It is the one missing key of `landing-check`.
- Tests (plan-reported): 6780 tests in each of five `verify` runs; `pm-provider-git` 29 tests after the fix; `refusesRoster` failed before the fix (1 failure) and passed after. Not re-run by the orchestrator.
- Deliverable order (plan-reported): test-first for the roster refusal probe only; wording and Javadoc fixes have no failing test.
- Link check: the landing reports none. The changed documents are two `.adoc` files with no new link; not run by the orchestrator.
- Executor: the lane read the stale installed `runbook_lane` (pinned 0.1.1961): `build-classify` is `unknown_verb`, and `resolve` omits `base_branch`, `build_timeout_seconds` and `guard_log`; the supplied canonical build names (`quality-gate`) were rejected by Maven, and the operator chose the executor with real Maven arguments (plan-reported). Fail-closed direction taken: the full gate ran.
- Rule breaks: none reported beyond the stale executor; guard: none on this harness (unenforced).

## Routing and Merge Behavior

- Review: `coderabbit` reviewed at `8bbbf8a` with one minor inline finding (a duplicate fixture rejected by `Set.of` before the roster context was added), fixed fail-first in `635b10a`, thread answered, follow-up review at the current head with no actionable comments (plan-reported). `sourcery` (optional) refused on its review budget. Two self-review rounds (plan-reported): round 1 two nits fixed in `0eacd4f`, round 2 clean.
- CI/merge (read from the PR through `ci`): `state: merged`, merge commit `73ec37a` on `main` (an ancestor of `origin/main`); `checks status`: `overall_status: success`, 30 checks, including `build / build (25)`, `build / build (26)`, `integration-tests`, `quarkus-alignment` and `SonarCloud Code Analysis` (pass). All seven commits the landing names exist in the object store.
- SonarCloud reported 1 new issue with the quality gate passed; the lane did not triage it. Not read by the orchestrator.
- Collision with the plans that ran beside it (PLAN-64, PLAN-82): none observed; PLAN-90 shared no file with either at emit time and the merged files do not appear in their declared surfaces. PLAN-90 touched no `doc/plans/README.adoc` row (the spec's standing constraint to remove its own row was not exercised: no index row is mentioned in the squash).
- Cleanup: `cleanup_owed=false`; the landing says so, and the branch `chore/90-ported-tests-advisories` is the PR head of a merged PR.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-90 --status shipped`
- [x] row `pr` stamped — `#120`
- [x] row `landing` stamped — `landings/PLAN-90.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-90-ported-tests-advisories` (the sender, per the runbook rule)
- [x] epic.md: the token defect and the opencode watch get their fourth landing; the executor watch updated; the Sonar issue recorded as an unverified lead
- [x] the landing carries no candidate lesson for this store
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- `WorktreeShaPortedTest.java` was touched beyond the declared surface (one added probe). A shipped plan's spec is not corrected after the fact; recorded here so the pattern is visible: a plan that adds a test for a Javadoc contract touches the test class.
- One SonarCloud issue on PR #120 is untriaged: a Watch in `epic.md`.
- Index: `origin/main` at `73ec37a` carries no `doc/plans/` file and no `README.adoc` row naming `ported-tests-advisories` or `advisories`, and the squash did not touch `doc/plans/`. PLAN-90 was staged from the PLAN-37 landing, not from a package file, so there was no row for the plan to remove. Nothing is owed.
