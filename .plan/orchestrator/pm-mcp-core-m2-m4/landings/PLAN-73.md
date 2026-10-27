# Landing Analysis: PLAN-73 — Question continuation (partial: the library core; the package stays open)

epic: pm-mcp-core-m2-m4
workstream: WS-04
pr: #201 (merged as a7a355aa, 2026-10-27, merge queue)

> Landing record for one plan that shipped **partially**. Source: inbox message `rb-73-question-continuation-003.md` (runbook lane,
> opencode, 2026-10-27T14:41:01Z; the earlier `-001` and `-002` were the lane's stops before the merge, archived as observations),
> corroborated against git at `a7a355aa`, the pull request through `ci`, the SonarCloud public API and the code. The message is a lead;
> every fact marked "checked" was read by me; no test was re-run.

Standing rules: r1, R01 to R18 named by the landing. **R01 (the package row and file removed) and R05 are not applied** (the package is not
done: the remainder is owed). R19 was not in force when the lane started. The lane read the main checkout copy of its spec
(`spec_source: main_checkout`), which was the version adapted and landed in #195 and #200, with the open D7 decision in it.

## Deliverable Fidelity vs Spec

Ten deliverables. The lane counts **0 of 10 fully met** and states per row what is built; I agree with that reading.

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 `ask fresh` in the grammar, parser, printer, verifier | library shipped | Checked: `StateBehavior`, `WorkflowParser`, `WorkflowPrinter`, `SubWorkflowInvariants`, `InstancePruner` and their tests are in the diff. |
| D2, D3, D5 the continuation decision, hold, answer and acknowledgement, resume offer, fallbacks | library cores shipped, **not wired** | Checked: `workflow.question.QuestionContinuation` (decides HOLD, RESUME or FALLBACK), `workflow.hypermedia.AnswerFact`, `TaskProtocol` hold, answer, acknowledge, suspend, resume offer and fallback entries; `TaskProtocolContinuationTest`, `AnswersTest`, `QuestionContinuationTest` exist. **No production caller**: `QuestionContinuation` is referenced only by its own file in `src/main`. |
| D4 the resumed generation's launch | not met | needs `runtime.launch` and `WorkerCalls` (the assembly). |
| D6 the missing acknowledgement as a loss | not met (the deadline is built) | `WorkerLossDetectionTest.answerAcknowledgement` covers the deadline; the loss path is the unwired supervisor's. |
| D7 the budget while holding, the restart | **specification restored; behaviour not built** | Checked: Model Work 06 now has `*Budget*: A worker that reaches its token budget while holding ends the hold: its task is suspended where it may resume, else it falls back, and the worker is recycled` (the sentence #190 had deleted) with the continuation accounting; `WorkerRecycling` still only recycles; no restart sweep. |
| D8 the question bound | ledger shipped, offer wiring absent | Checked: `QuestionBound` with `QuestionBoundTest`. |
| D9 the metrics `continuation` mark, the `resumability` stage result | shipped | Checked: `MetricsRow`/`MetricsCodec` continuation mark, the parity test, `ProfileStagesTest`; ADR `core-store/047`. |
| D10 the document sections and watch items deleted | not done | the package row, the file and the covered sections stay (R01 and R05 not applied). |

**Whole diff, checked** (`git diff a7a355aa^1 a7a355aa`, first parent `471fe190`): **52 paths (20 `A`, 32 `M`); +2,724/−72**, among them two
specification files (`model-work/06`, `store-schemas/05`), `doc/Traceability.adoc`, ADRs `core-store/047` and `workflow-hypermedia/001`, the guides
`core-store.adoc` and the new `workflow-hypermedia.adoc`.

**Surface delta** (`inbox landing-check`): `expansion_detected`. **12 undeclared** (`InstancePruner`, the two ADR/guide pairs, test files). **27
declared and untouched:** the package row and file (kept), the specification and watch parts the plan did not edit, and the directory
entries of `core/queue`, `core/role`, `runtime/launch`, `engine` and the two `runtime/worker` ports `WorkerCalls`/`WorkerProtocol` (the
wiring is owed). The lane read a surface that was still directory-level (R19 came after its start).

## Metrics and Anomalies

- **Checked CI:** #201 `success` over 30 checks; **SonarCloud `pullRequest=201`: gate OK, 0 open issues, new coverage 87.6** (read after the
  lane's last push); `branch=main` still 2 open. CodeRabbit reviewed three heads (all findings fixed, threads resolved); Sourcery refused on quota.
- **Two base merges, real conflicts resolved by the lane:** `question/package-info.java`, `AstNodeModelTest`, `core-store.adoc`, `workflow-dsl.adoc`;
  the collided ADRs were renumbered against `origin/main` (R11: `core-store` 046 → 047, `workflow-dsl` 009 → 010).
- **A library without a production caller, again:** PLAN-34's grader, PLAN-66's pump and recorder, PLAN-73's continuation: the supervision loop
  and the assembly wiring remain the common missing piece (the Open Defect of 2026-10-26 stands).
- **The D7 decision was put to the operator in the lane's second session** (after the orchestrator's paste) and answered "Restore it in Model Work 06":
  the Open Defect "#190 deleted the specification sentence PLAN-73 D7 depends on" is **closed**.
- **Rule breaks the lane lists:** edits written to the main checkout in the first session (restored clean), shell file reads, one parallel-git
  collision, a commit shared by two deliverables; **the first landing message was consumed before it could be amended, so corrections came as
  `-002` and `-003`** (the lane's own words). The routed build job failed at spawn, every build ran in process; the whole-reactor
  `clean verify -Ppre-commit` ran green in 837 s (7950 tests). `total_tokens=unknown` (36th), `plugin_version=unknown`.

## Routing and Merge Behavior

Personas as stated (implementer, module-tester, documenter, code-reviewer for two rounds and the comment triage). Merged through the merge queue
after the operator's "Merge now" at `e95a3469`, then re-run from the newer head `fdda8274` after the second base merge and the last CodeRabbit
findings. The lane's worktree and branch are gone (`git worktree list`).

## Collision Analysis

`corpus declaration-currency` over the 52-path footprint: **PLAN-61 (running)** shares `ProfileStages`, `MetricsCodec`, `MetricsRow` and their tests and
**its branch conflicts with `main` now in `pm-core/src/main/.../core/profile/ProfileStages.java` and `pm-core/doc/developer/core-profile.adoc`** (trial merge): the
lane merges the base before its gate (its step 7). Other staged and parked specs sharing code with this footprint: PLAN-43 (`WorkflowParser`,
`WorkflowPrinter`, `InstancePruner`, `SubWorkflowInvariants`, the AST tests), PLAN-47 (`QuestionBound`, `QuestionContinuation`: PLAN-47's `workflow/question`
package now holds PLAN-73's classes), PLAN-97 and PLAN-98 (`ProfileStages`), PLAN-100, 51, 60, 92 (`TaskProtocol`), PLAN-99 (`MetricsRow`), PLAN-48 and 63 (a test file each).
ADR numbers taken: `core-store` 047 (next 048), `workflow-dsl` 010 (next 011), `workflow-hypermedia` 001 (next 002).

## Reconciliation Actions

- Row PLAN-73: `running` → `shipped`; `pr` `#201`; `landing` `landings/PLAN-73.md`; `plan_marshall_plan_id` `rb-73-question-continuation`. The status records the merged
  pull request, **not the package**: the package `question-continuation` stays `open` in the index, so `supervision-scenarios` stays blocked on it (and on `worker-activity`).
- `epic.md`: annotation; the Open Defect about the deleted budget-while-holding sentence is closed; a remainder defect for `question-continuation` and `worker-activity`
  together (the supervision loop, the restart sweep, the engine and envelope offer wiring, the resumed launch, the assembly entries).

## Follow-Ups

1. **The remainder of `question-continuation`** and of `worker-activity` (the common supervision loop, in this repository or `pm-mcp-server`): no staged plan.
2. The package row and file, the implemented specification sections and watch items: removal owed when the remainder lands.
3. The first landing message was consumed too early by the lane (a lane-side defect worth a lessons-routing note for the lane, not filed here).
