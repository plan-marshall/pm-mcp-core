# Landing Analysis: PLAN-25 — Bounded and progress waits

epic: pm-mcp-core-m2-m4
workstream: WS-05
pr: #145 (merged as a53d312d97370eb2c01f434ce4d07df999153466)

> Landing record for one shipped plan. Source: inbox message `rb-25-waits-001.md` (runbook lane, opencode,
> 2026-10-23T07:28:44Z), corroborated against git at `a53d312`, the PR and its 30 checks through the `ci`
> abstraction, and the SonarCloud pull-request API (plain HTTP, read only). Facts marked "checked" were read
> from those sources; the rest is the lane's own report.

Standing rules: r1, R01 to R13, R15 and R18 named by the landing. R14 and R16 are out of scope for the
module; **R17 is not named, and could not have been: see the process finding below.**

## Process finding: the lane did not see the adapted spec

The hand-off of this plan was adapted on 2026-10-22 (runbook command, R17, the package-bookkeeping, links-check
and build clauses added to the Done-when). **None of that reached the lane.** The runbook lane reads the spec from
the main checkout, where it is tracked; with `orchestrator.use_worktree` on, the orchestrator's edits live in the
shared ledger worktree and reach the main checkout only through `land`. Checked: the main checkout's
`.plan/orchestrator/pm-mcp-core-m2-m4/plans/PLAN-25-waits.md` (last commit `caa6eb5`, #140) holds the original
text, with no R17 and the `/plan-marshall task=…` hand-off. The landing agrees: it quotes the original rule list
and the original Done-when (three clauses and Sonar). The same holds for PLAN-26 and PLAN-87, which are running
on the unadapted specs. The adaptation was reported as done when it was only done in the ledger worktree.

So this landing is judged against the spec the lane read. The clauses I added are reported below as **not
checked by the lane**, not as unmet by it.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 — the bounded long-poll and the progress-reset wait, selected from the client profile and the progress token | shipped-as-specified | Checked: `WaitSelection.select` takes a progress wait only when the profile's `progress_resets_timeout` is set and the request carries a non-blank `_meta.progressToken`, otherwise the bounded wait; `pm_build` that qualifies holds the progress upper bound, `pm_wait` keeps the bounded window; an interval not shorter than the window is halved so at least one frame is emitted. `ModelWait`, `ModelCall`, `ConnectionSettings`, `WaitSettings` are new in `pm-workflow` `workflow.engine`; `ModelWaitTest` is +220 lines. |

Done-when as the lane read it:

| Done-when | Verdict | Evidence |
|-----------|---------|----------|
| a bounded wait answers at its window (virtual clock) | met (lane's test) | `ModelWaitTest.shouldAnswerABoundedWaitAtItsWindow`, order `mutation-after`. Not re-run here; CI is green (see below). |
| a progress wait emits no gap longer than the interval | met (lane's tests) | three tests, order `mutation-after`; the failing output the lane quotes (`a gap of PT29M55S … exceeds PT5S`) came from a one-frame mutation of the committed code. |
| the selection follows profile and token in all four combinations | met (lane's tests), code read | the selection above matches the four cases. |
| Sonar: no open issue, gate green | met | Checked: the pull-request search for #145 returns `total: 0`, the gate reads `OK`, new coverage 94.1 (threshold 80). |

The lane's design decision that a `pm_wait` keeps the bounded window in both cases and that progress frames hold
the host's timer (ADR `workflow-engine/012`, checked present) was not compared against Timeouts § Waits by this
verb. The order `mutation-after` is honest: the spec did not require test-first.

**Not checked by the lane (clauses of the adapted spec the lane never saw):**
- `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-core -am` were not run locally (the lane says so). Checked instead: CI ran `integration-tests / test` and `build (25)`, `build (26)`, `sonar-build` green on the final head, and Sonar's new coverage is 94.1. The per-module 80 % coverage rule is not evidenced for `pm-workflow` as a whole.
- The links check (R17) was not run; the lane may not read outside the repository root. Not run here either: the main checkout is behind `origin/main`, so a run would not describe #145. Open.
- Package bookkeeping is partly done. Checked: `doc/plans/waits.adoc` deleted, its row removed from the index, and the `Depends on` cells of `task-queue-and-protocol` and `operator-wait-and-machine-scope` updated. **Not done:** the implemented sections of the specification part and the covered watch items were kept, not deleted. The lane gives a reason (other parts anchor the rows), the specification file gains a pointer (+7 lines), and the watch documents `implementation-watch/job-runtime.adoc` and `hypermedia-format.adoc` are untouched, so `PM-WATCH-JOB-5` is neither deleted nor reported on. The lane says `PM-WATCH-JOB-30` stays open.

## Metrics and Anomalies

- Tokens: unknown (opencode measured none; `missing_keys: [total_tokens]`).
- `self-review: budget-exhausted`: three rounds, the last reported three nits, fixed and gate-verified but not re-reviewed; the operator chose "Proceed to the pull request". Round 1 found a blocker (a README self-dependency) and a major (interval floor / out-of-memory), both fixed in `94890ea`.
- Footprint (checked, `surface_delta`: `expansion_detected`, 14 declared, 13 realized, 1 added, 7 missing): added `doc/Traceability.adoc` (the work-package flow requires it); missing are the seven `workflow.hypermedia` and hypermedia-document paths. The lane refuted the hypothesis that part of the package lands in `workflow.hypermedia` (the composite wait belongs to `finalize.concurrent-wait`); the spec's Expected Surface over-declared, as it flagged.
- The merged diff contains no `pm-runtime` file. The lane reverted the five OpenRewrite rewrites in its worktree (they conflicted with `origin/main`, where #144 had committed the same files) and never staged them. This is consistent with #144 having resolved that churn.
- Build: setup preflight `ready`; the first routed job failed with `executor_absent_from_worktree` and every later build ran in process (third lane to report it); one gate hit the 617 s bound after 5266 passing tests and was re-run with `--timeout 1800`. The setup-time `resolve` reported the project root `down (unreachable)` while the worktree preflight said `ready`.
- Guard: none on this harness (unenforced).

## Routing and Merge Behavior

- Review: CodeRabbit (required, quota-limited) reviewed `f7a3ec2` with two inline findings and a review-body nit: one fixed (`5f2ce54`), one declined with a reason (the specification fixes the window; the margin is a profile-validation rule) and withdrawn by the reviewer, one answered. Re-triggered twice after later pushes, both refused as rate limited; merged under the standing review authorisation, the reviewed head being `f7a3ec2` and the merged head `37274e5`. Sonar: nine `java:S5778` in the test lambdas, fixed in `0f71605`. Sourcery (optional): refused on its budget.
- CI/merge: checked, #145 reads `merged`, head `feature/25-waits`, merge commit `a53d312d97370eb2c01f434ce4d07df999153466`, first parent `3cc1585`; the `ci checks status` reads `overall_status: success` over 30 checks (some skipped by design). **The merge queue dequeued the PR once**: the merge-group run failed in `build / build (25)` because Maven Central answered 403 for `surefire-junit-platform:pom:3.6.0`; the operator chose to re-enqueue, and it merged.
- Collisions: #145 touches `doc/plans/README.adoc` and `pm-workflow/doc/developer/workflow-engine.adoc`. PLAN-26 (running) shares the index and writes `workflow-model.adoc`; PLAN-103 (staged) shares the index.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-25 --status shipped`
- [x] row `pr` stamped — `#145`
- [x] row `landing` stamped — `landings/PLAN-25.md`
- [x] row `plan_marshall_plan_id` stamped — `rb-25-waits` (from the sender)
- [x] epic.md narrative reconciled: queue annotation, `total_tokens` recurrence, Open Defects (the spec edit visibility, the incomplete bookkeeping, the lane-reported `ProfileValidator` lead), Watches (merge queue 403, executor, `/tmp`)
- [x] resume anchor updated in `resume_anchor.md`
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- **Land the ledger before the next emit that adapts a spec**, or hand the lane the adaptation another way. PLAN-26 and PLAN-87 are running on the unadapted specs: they have no R17, no links-check or build clause, and PLAN-26 has no note about PLAN-95 and PLAN-94 (its lane merges the base before the gate anyway, as every lane does). The operator decides whether to land now (`land` needs the operator's word) and whether to tell the running lanes.
- **Lead from the lane, unverified:** `pm-core` `ProfileValidator` accepts a bounded wait above `idle_timeout_seconds − 30` when progress resets no timer (it rejects only `bounded >= idle`), while the specification requires the 30-second margin; `ConnectionSettings` enforces the specified rule, so a loader-accepted profile in that band is refused when a wait is built. Pre-existing, in the profile loader PLAN-19 shipped; no plan owns it. Not checked against the validator by this verb.
- **Owed to the milestone exit** in `plan-marshall-documentation`: the concept and user documentation of the waits and the roadmap check-off (an existing Watch).
- **Not in this repository:** the MCP tool binding of `pm_wait` and `pm_build` and the relay's progress sender.
- The claims of PLAN-26, 28, 42 and the other specs that cite `waits` as a dependency now read a package that is done; `cleanup` re-grounds them.
