# Landing Analysis: PLAN-24 — Plan instance composition

epic: pm-mcp-core-m2-m4
workstream: WS-03
pr: #67 (https://github.com/plan-marshall/pm-mcp-core/pull/67), merge commit 2ebec2a

> Landing record for one shipped plan. Lives at `landings/PLAN-24.md`. Written by the
> `analyze` verb after verifying claims against ground truth (actual code, artifacts,
> PR state) — a pasted claim is a lead, never a fact. See
> `persona-plan-orchestrator/standards/orchestration-model.md` for the analysis and
> reconciliation contract.

Source: inbox message `plan-24-plan-instance-001.md` (landing-facts complete, no missing key), drained on
2026-10-15. The orchestrator verified the following:

- #67 is `merged` (squash through the merge queue); its merge commit 2ebec2a is on `origin/main`.
- Documentation PR `plan-marshall-documentation` #73 is `merged` (1eaa6c6). It was rebased onto documentation
  `main` after #72 and #74 to #79. It keeps the specification text that documentation #76 rewrote, because the
  code does not implement that text yet. `PM-IMPL-5` and the roadmap item (now `[~]`) say so.
- `doc/LogMessages.adoc` on documentation `main` has rows for PM_WORKFLOW-2 and PM_WORKFLOW-101. Their templates
  match `WorkflowLogMessages` on `origin/main`.
- The index row `plan-instance` is `done`. The new package `lane-route-pruning` is in the index as `open` and
  depends on `plan-instance`. ADR `workflow-plan/011` (lane-route pruning deferred) is present.
- The plan directory is archived (`archived-plans/2026-10-15-plan-24-plan-instance`).
- The diff 2ebec2a^..2ebec2a has 76 files, +9899/−30. ADRs workflow-plan/001–011, workflow-diagram/011 and
  core-store/028 are present.

The orchestrator did not re-run the build or the tests. The deliverable verdicts rest on the plan's functional
review (6 met, 2 met with residuals, 0 not met), checked against the diff.

Harness: Claude Code (operator rule since 2026-10-13).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| Plan instance composition: versions and composition log | shipped-as-specified (against the specification before documentation #76) | `workflow.plan` (`PlanInstanceStore`, `CompositionCause`, `PruneReason`, the `composition-rules.json` resource); ADRs workflow-plan/001–009; PM_WORKFLOW-2, -101 |
| Rendering of an instance | shipped | `workflow.diagram` instance renderers (Mermaid, ASCII, AsciiDoc table); ADR workflow-diagram/011; fixtures `instance-fixture.*` |
| Resource URIs | shipped-modified | ADR workflow-plan/010: the URIs are bound in `pm-mcp-server`, not here |
| Pruning the other lane's route (TASK-15) | not shipped, split | infeasible within the plan; split into the package `lane-route-pruning` (`open`) with ADR workflow-plan/011 |
| Specification after documentation #76 | **gap** | the code still ships the prune reasons `recipe_override` and `auto_merge_off` (`PruneReason`, `CompositionCause`, `composition-rules.json`). The specification now names `plan_default` and `merge_by_operator`, with axes, overrides and phase sub-workflows. By operator decision (2026-10-15, lesson `2026-10-15-07-001`) the alignment is follow-up work. No package for it exists yet in `doc/plans/` |

**Surface delta** (computed by the orchestrator, since the landing check had no surface data): 5 declared
entries, 76 files realized, 27 outside the declared surface. Of the 27:

- `workflow.diagram`: four instance renderers, the generator and its tests and fixtures, ADR 011 and the developer
  guide.
- `workflow.verify`: `CompiledInvariants` and `RemediationInvariants` with their test, and the developer guide.
- `workflow.log`: `WorkflowLogMessages` with its test.
- `core.store`: `ScopeTransaction` and its test, ADR core-store/028 and `core-store.adoc`. This is shared with the
  store plans. PLAN-09 had merged before it, and the change caused the ADR number collision of lesson
  `2026-10-15-07-003`.
- `doc/plans/lane-route-pruning.adoc`.
- `composition-rules.json`. It lies under `src/main/resources`, which the surface did not declare.
- The test fixture `plan-template-fixture.pmwf`.

## Metrics and Anomalies

- Tokens: 9,939,109 (landing-facts). Read it with the epic's token-overstatement watch in mind.
- Duration: 86,659 s (about 24.1 hours), the longest run of this epic so far.
- Anomalies:
  - Six fix tasks (TASK-16 to TASK-21) for review findings, plus several finalize loop-backs. The triage budget
    ran out (3 of 3) and code review granted 2 more rounds. One of the defects (`HeaderJson.intValue` on
    `1e999999999`) was found only on a second review pass (lesson `2026-10-15-07-004`).
  - A whole-tree `verify -Ppre-commit` ran inside a leaf, hit the 590 s cap and had to be rerun in the background
    (lesson `2026-10-15-07-002`).
  - The ADR number collided after the rebase (core-store/024, which #69 had taken, became 028; lesson
    `2026-10-15-07-003`).
  - The documentation PR stayed open while documentation #76 rewrote the same specification (lesson
    `2026-10-15-07-001`).

## Routing and Merge Behavior

- Review: code review, automatic review and the Sonar round trip are `done`; Sonar reports 0 new-code issues. In
  total 140 review findings were triaged.
- CI/merge: merged through the merge queue as 2ebec2a, after PLAN-09's 3236ed5. No collision was reported. The
  shared `core.store` file `ScopeTransaction` was resolved by the rebase.

## Reconciliation Actions

- [x] row `status` → `shipped` — `orchestrator queue --transition PLAN-24 --status shipped`
- [x] row `pr` stamped — `#67`
- [x] row `landing` stamped — `landings/PLAN-24.md`
- [x] row `plan_marshall_plan_id` stamped — `plan-24-plan-instance`
- [x] epic.md narrative reconciled:
  - the PLAN-24 watch is retired to `settled.md`;
  - a new watch records the gap between the code's prune reasons and the specification;
  - the queue annotation is updated.
- [x] resume anchor updated
- [x] `queue-view.md` regenerated and committed with the row change

## Follow-Ups

- No candidate-lesson messages arrived. The plan wrote four lessons straight into the local corpus
  (`2026-10-15-07-001` to `-004`). One part of `-002` concerns plan-marshall: a leaf ran a whole-tree gate past
  the Bash cap. It is routed to `lessons-routing` as a recurrence of `pm-mcp-core-m2-m4-281` (message `-286`).
- Code/specification alignment of the plan instance (axes, overrides, phase sub-workflows, `plan_default`,
  `merge_by_operator`): no package exists yet. Stage it at `cleanup`, together with `lane-route-pruning`.
