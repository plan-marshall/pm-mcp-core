# Landing Analysis: PLAN-V2-165 — supervisor-end-path-closeout

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #540 (squash merge `d166651e`, head `42f8195f`, 34 files, +955/-115)

> Landing record, plan-marshall form (`plan-v2-165-supervisor-end-path-closeout`, harness claude, model `claude-sonnet-5-5`, effort medium, 5 deliverables: 4 shipped, 1 shipped modified). One landing message (`-001`, 2026-11-21T13:50:57Z, `merge_state=merged`); no candidate-lesson message (the retrospective step is `off` in the project config).
> Landing facts: `unresolved_review_threads=0`, `review_body_findings_total=2`, `handled=2`, `sonar_open_new_issues=0`, code-review `blocking_count=0`, `advisory_count=9`, `total_tokens` 30 693 150, `total_wall_seconds` 78 173 (21 h 42 min, about 16 h idle), no blocker.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| D1 the persisted end reason | shipped-modified | the operator's answer built the specification amendment, not the request reason in the terminal block: `WorkerTermination.endReason` is unchanged in code (Javadoc only, `:25-30`, mapping `:61-72`); ADR runtime-worker/021 and `timeouts/01:303`, `:311` agree; the PR body's Problem paragraph still repeats the hypothesis "records `drain`". |
| D2 the missing tests | shipped, with weak binders | all 8 listed tests exist and 6 fail on the code they guard (F2 `else closeAttempt`, fate step, `zero_tool`, `StoreRefusal`, `REFUSED`, `LogAsserts` for `PM_RUNTIME-149` and `150`); the per-reason test and the every-end-reason test cannot fail on a wrong arm; the throwing-yield test puts no exception in flight. |
| D3 windows and races | shipped for 5 of 6 | `WorkerRecycling` filters, `onRegistered` re-read, `completed()` after `replaced`, `LOG` and `SUCCESSOR` as guarded effects, `IllegalStateException` for the missing role class: all fixed with tests that fail without them; the start-failure question of a never-connected worker was not touched. |
| D4 documents | shipped, not complete | Javadoc, "terminal-state writer", write-order sentence, "logged once", the fate-step report string and `attemptEndOf` fixed; six groups of statements still say what the code no longer does (item 75 of PLAN-V2-162). |
| D5 follow-ups | shipped (outcomes recorded) | each has one line in the guide and the PR body; the ownership of two is weaker than recorded: PLAN-V2-162 carried neither the start-failure question nor a commitment to per-line offsets. |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (general-purpose, three layers in one brief; no file written, no build run, HEAD not moved). Its counts: 34 files, 18 new test methods, no new `LogRecord`, ADRs 021 and 022 unique. "11 789 tests green", "seen failing" and "67 pre-existing broken links" are the landing message's figures and were not re-run.
- **Verdict:** no blocker. The end path is closed in the code; what is left is traceability of two ownership claims, a set of tests that bind too little and statements the change made false.

### Layer 1 — Specified

- D1: spec amendment built (checked, confirms); the test per reason is decorative (`WorkerTerminationTest:107-139`, four rows with one fate, only `CANCELLED` to `WITHDRAWN` bound), minor.
- D2: throwing-yield test covers the catch at `SupervisionPass.java:134-139` and the log records but not an exception in flight (`advanceReported` catches the generation's own failure first); per-effect resume fails at `RELEASE` (the third effect), binds the fence once, leaves the revoke repeat unbound; the every-end-reason case is tautological for non-loss reasons (`.orElse(attemptEnd)`).
- D3: the unconditional `supervisor.replaced(terminal)` of carried item 42(b) is closed (`onRegistered`, `SupervisionPass.java:325-329`); callers outside the calls monitor that hold a stale snapshot can still install a request after `completed` (`AgentStartedDetector.java:192`, `WorkerDrainParticipant.java:62`, `:76`), read, not tested; transport callers of `detection()` now see `IllegalStateException` instead of `NullPointerException`.
- D5: follow-up 4 (budget against the exit loss) dropped with a reason, verified against ADR 020; follow-ups 1 and 3 are residue (below); follow-up 2 is "owned by 162 item 42(c)" which names only the symptom.

### Layer 2 — Implemented

- Lock order calls, then ending/supervisor is consistent; `exited` holds the ending monitor for the claim and each `done()`.
- Beyond the items (R07), all minor: a failed successor start is never retried by the end path (ADR 022 accepts it); `attemptEndOf`'s `default -> throw` makes a new `EndReason` a runtime failure for every pass of that generation; `WorkerEnding.run`'s `default -> {}` is an empty arm.
- `WorkerEnding.java:216-222` (the unsettled-record fallback write) is not a guarded effect, so a retry writes it again; reachable in tests only.
- `onRegistered` uses `withLifecycle`, so the `SupervisionPass` class Javadoc (`:50-52`) "every lifecycle change passes the transition table" is false.
- No new hand-written fake; two inline EasyMock doubles added.

### Layer 3 — Correct

- Compile-level read: no dangling symbols; `completed` is package-private with every caller in the `worker` package; the anchors `#_ending_fencing_and_drain` and `#_ports_and_the_assembly_adapter` exist; no LogMessages change owed.
- Residue claims of the landing message checked: the offer-time window is real (`WorkerActivityPump.java:62-69`, `:102-123`); the unreadable-tail gap is real (`closeTracked` `Closed(false, null)` at `:108-110`, `closeAttempt` ignores it at `SupervisionPass.java:206-212`, `finishAttempt` removes the pump at `:348`).
- Advisory list of the landing: 8 of 9 confirmed; "`LossReason.recording` has no test for its empty branch" is wrong (`SupervisionPassTest:591` runs every `EndReason` through it, without an assertion there); its null guard is untested.
- Stale statements still present: `runtime-worker.adoc:228-236`, `:534-535` with ADR 019:34-35, `:149-151` with `EndCause.java:17`, `:68`, `model-work/09:23` (older than #540), `model-work/02:122`, `runtime-lifecycle/01:68`, `timeouts/01:133`, the `WorkerActivityPump` Javadoc `:24-28`.

### Review State

CodeRabbit: 2 body findings, both handled (the offer-time window raised as Major and declined with a reason, now residue); the walkthrough comment edited after the last push was accepted directly at the pre-merge barrier. Sonar 0 new issues, gate OK, 94.4% new-code coverage. The functional review was closed by the operator on criterion D5.C3 alone (the criterion asks for the landing message, which cannot exist before the merge).

## Metrics and Anomalies

30.7M tokens, 21 h 42 min wall (about 16 h idle), four head moves after the first push re-fired the review steps. Merge: the operator chose "merge now, no second prompt" and a merge-queue rebase (`final_merge_without_asking`, `auto_rebase_threshold=auto_resolvable`, this plan only). Links check on the merge result: 67 broken links, none on a line this change edits (the landing message's figure, not re-run).

## Routing and Merge Behavior

Merged through the merge queue; `cleanup_owed=false`. No candidate lesson was sent by the plan. Two lessons were forwarded to the plan-marshall lessons-routing inbox by the orchestrator (`-051`, `-052`): a criterion whose evidence appears after the merge cannot be met by the functional review, and a plan records "owned by X" without checking that X's text carries the item.

## Reconciliation Actions

- Row PLAN-V2-165 transitioned to `shipped`; `pr` `#540`, `landing`, `plan_marshall_plan_id` `plan-v2-165-supervisor-end-path-closeout`.
- Residue placed, none new staged: item 75 in PLAN-V2-162 (the start-failure question of a never-connected worker, the offer-time window, the unreadable-tail gap, per-line offsets, the false statements, the small defects of the new end path; the stale line cites and the closed item 42(b) noted in its Dependencies); item 76 in PLAN-V2-126 (the tests that bind too little). PLAN-V2-162 no longer waits for 165.

## Follow-Ups

- Operator: none new. The persisted end reason was answered at refine (specification amended to the coarse mapping of the fate).
- Stale line cites of #540 also sit in PLAN-V2-114, 136 and 156 (`SupervisorTestFixture.java:258`, `WorkerEndingTest:114-117`, the same cites as 162); those specs read every line at the head before editing, so no edit is made.
