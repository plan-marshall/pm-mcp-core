# Landing Analysis: PLAN-37 — Ported-primitive tests

epic: pm-mcp-core-m2-m4
workstream: WS-06
pr: #106 (https://github.com/plan-marshall/pm-mcp-core/pull/106), merged as squash `ebcf128` on 2026-10-19

> Landing record for one shipped plan. Lives at `landings/PLAN-37.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source of the landing: inbox message `plan-37-ported-primitive-tests-012.md` (`kind: landing`, `landing-check`: `complete: true`, no missing key), corroborated by the orchestrator against `git show --stat ebcf128` (35 files, +1768 −124), `ci pr queue-state --pr-number 106` (`pr_state: merged`, `merge_commit_sha: ebcf128e…`) and the files at `main`. The plan's figures that the orchestrator cannot re-measure (tokens, wall clock, review-bot dispositions, gate degradation) are marked plan-reported.

## Deliverable Fidelity vs Spec

The spec has four deliverables (the package file's bullets); the plan counts six (`deliverables_total=6`, `deliverables_done=6`). `functional-review`: 5 met, 1 met with residuals, 0 not met.

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| 1. The convention for the tests of the `Ported` scripts, one script at a time, original script and pytest suite as the guide | shipped-as-specified | `pm-providers/pm-provider-git/doc/developer/provider-git-ported.adoc` (+257) and ADRs `provider-git-ported/001` (derived from the original, one script at a time), `/002` (test utilities stay in the test tree until a second module needs them), `/003` (`worktree_sha` tested by equality behaviour over plain-file scenarios) |
| 2. The tests run in Java and Maven alone; no Python, no `plan-marshall` checkout, no comparison of results | shipped-as-specified | The diff adds Java tests (`ported/WorktreeShaPortedTest`, `WorktreeShaScenario`, `PortedFixtures`, `DigestRelations`, `package-info`) and 17 plain-text fixtures under `src/test/resources/ported/worktree-sha/`; no workflow or POM line in the diff touches Python; `store-schemas/13-leases-integrity-testing.adoc` (11 lines changed) states the same |
| 3. The original is `plan-marshall` at the pinned inventory commit; the pin advances deliberately | shipped-modified | The pin advance (to `297ae30`) and the migration inventory are in plan-marshall-documentation PR #95, which is **open** (`ci pr queue-state --pr-number 95` in that repository: `pr_state: open`) and is the operator's to merge; the plan's own message says it was meant to land first and was unmerged when #106 was enqueued |
| 4. First used by the `Ported` stores and primitives of this stage | shipped-modified | First use is one primitive, the working-tree digest `worktree_sha` (`git.worktree-sha`), in `pm-provider-git`, not in `pm-core` as the spec's Expected Surface hypothesised; the stores of the stage are not ported by this plan |

Unplanned edits, all in the diff: `JGitOperationsTest.java` (−81: the digest cases moved into the ported suite), `doc/Traceability.adoc` (+32), `doc/adr/README.adoc` (+1), `doc/specification/job-runtime/03-parsers-and-gates.adoc` (6 lines), `pm-provider-git/README.adoc`, and the package file `doc/plans/differential-test-harness.adoc` with its index row deleted, as the work-package convention requires.

## Metrics and Anomalies

- Tokens: 6,079,005 total (plan-reported). Against the 2.5M threshold of this epic's plans this is more than twice; not re-measurable by the orchestrator.
- Duration: 31,129 s of wall clock (about 8 h 39 min), plan-reported.
- Anomalies, all plan-reported: the first merge-queue run failed on a Maven Central resolution error (`surefire-junit-platform:3.6.0`), the queue dequeued the PR and the second enqueue at the same HEAD landed (infrastructure, not the change); the `module-tests` arm of the pre-push quality gate was DEGRADED (`required_coverage_unknown`); the first security audit read 5 of 17 fixtures and passed green, then was re-run and read all 17; a rebase of the finalize sync-baseline step rewrote two task commits and left their ids stale in the plan's artifacts.

## Routing and Merge Behavior

- Review (plan-reported): `code-review` 0 blocking, 5 advisory, 4 rules cited. CodeRabbit's non-ASCII file-name locale concern was accepted without a change. The optional bot Sourcery posted a review-budget notice twice and never reviewed the last commit; the run went on unreviewed by it, by the operator's choice.
- CI/merge: merged through the merge queue (`merge_mechanism=merge_queue`, `merge_state=merged`, `cleanup_owed=false`). The queue sets the squash message itself; the plan left a PR comment and asks to check the landed commit for bot prose or a second trailer.
- Collision with PLAN-70, the plan that ran beside it: none observed. PLAN-37 declared `pm-core` and the `pm-core` ADR and developer directories and realized `pm-providers/pm-provider-git`; PLAN-70 is still running, so its landing is the second evidence.
- Surface delta (`inbox landing-check`, base `ebcf128^`): declared 8 entries, realized 35 files, `state: expansion_detected`, 32 added and 5 declared entries untouched. The declaration was a hypothesis inferred from the `Where` line (`pm-mcp-core, test scope`) and named the wrong module: the first ported script, the working-tree digest, lives in `pm-provider-git`. The spec is shipped, so it is not corrected; what the gate learns is that a package which ports one script belongs to the module of that script, not to `pm-core`.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-37 --status shipped`
- [x] row `pr` stamped — `orchestrator queue --set-row PLAN-37 --field pr --value "#106"`
- [x] row `landing` stamped — `orchestrator queue --set-row PLAN-37 --field landing --value landings/PLAN-37.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-37-ported-primitive-tests` (the sender of the message)
- [x] epic.md narrative reconciled: the open pull request of the documentation repository, the unfixed advisories, the stale-id residue
- [x] the eleven candidate lessons of this plan concern `plan-marshall:*` components and were forwarded to plan-marshall's `lessons-routing` inbox under the operator rule of 2026-10-17
- [x] resume anchor updated; `queue-view.md` regenerated

## Follow-Ups

- plan-marshall-documentation PR #95 (pin advance to `297ae30`, migration inventory) is open and is the operator's to merge; it was meant to land first. Recorded as a Watch.
- The five advisories the plan left unfixed are staged as PLAN-90 `ported-tests-advisories` (stale "Rule 6" comments on line 2 of `deletion.txt`, `head-change.txt` and `non-ascii-path.txt` — checked; the nested-class sentence at `provider-git-ported.adoc:218`; missing `@throws` on `WorktreeShaScenario.run` and `unescape` and on `PortedFixtures.roster`, `load` and `read` — checked, the three `@throws` in the package do not cover them; wording at `pm-provider-git/README.adoc:13`).
- The stale Maven-parser sentence at `job-runtime/03-parsers-and-gates.adoc:66`, which the plan calls out of its slice, went to PLAN-89 item 4 as an unverified lead (the plan's statement; not re-read).
- The planted-difference trial that drops only the `D` tag byte stays green in the ported suite by design (the digest unit test pins that byte): recorded here, no action.
- The module `pm-provider-git` now carries the convention (ADRs `provider-git-ported/001` to `003`). The next ported script is built by the plan that owns its store or primitive; no plan is staged for it here.
