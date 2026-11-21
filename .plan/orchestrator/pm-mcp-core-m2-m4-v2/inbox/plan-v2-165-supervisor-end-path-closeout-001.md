envelope_version=1
sender_type=plan
sender_id=plan-v2-165-supervisor-end-path-closeout
epic=pm-mcp-core-m2-m4-v2
kind=landing
created=2026-11-21T13:50:57Z

## What landed

plan-v2-165-supervisor-end-path-closeout shipped as #540 (merged by the platform merge queue as d166651e, squash, head 42f8195f). Standing rules: r7, R02 R05 R06 R07 R08 R10 R11 R12 R14 R16 R17 R18 R19 R20.

```landing-facts
schema=landing-facts/1
plan_id=plan-v2-165-supervisor-end-path-closeout
epic=pm-mcp-core-m2-m4-v2
pr=#540
merge_state=merged
cleanup_owed=false
deliverables_total=5
deliverables_done=5
total_tokens=30693150
total_wall_seconds=78173.0
harness=claude
model=claude-sonnet-5-5
effort=medium
plugin_version=0.1.2173
executor_version=0.1.2173
steps=finalize-step-sync-baseline:done,functional-review:done,code-review:done,architecture-refresh:done,pre-push-quality-gate:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,finalize-step-preference-emitter:done,plan-marshall:plan-retrospective:skipped,record-metrics:done,finalize-step-print-phase-breakdown:done
unresolved_review_threads=0
review_body_findings_total=2
review_body_findings_handled=2
sonar_open_new_issues=0
step.code-review.blocking_count=0
step.code-review.advisory_count=9
step.code-review.rules_cited=8
```

## Residue

Gaps for the orchestrator to place in a staged plan (no plan owns them):

1. **Offer-time window in the activity pump** (follow-up 1 of the lane, dropped by the operator at refine). An offer the transport makes is not visible to the supervision pass, so a line the worker writes for the next task before the pass closes the previous attempt is read as that attempt's tail (`WorkerActivityPump.closeTracked`). The guide (`runtime-worker.adoc`) names the window; CodeRabbit raised it as Major on #540 and the finding was declined with that reason.
2. **Unreadable-tail gap** (follow-up 3, dropped by the operator at refine). A tail that `JobLog` reports unreadable at the settling pass is not read again: while the generation leases its task the attempt's end is appended in that pass and the pump leaves with it, and after the task closed `closeAttempt` appends no end on an unreadable tail and `finishAttempt` still removes the pump, so the attempt is left without an end.

Outcomes of the other follow-ups and the carried items: follow-up 2 (per-line source offsets from `JobLog`) is owned by PLAN-V2-162 Deliverable 3 item 42(c); follow-up 4 (the recorded budget against the exit loss's attempt end) is dropped, the budget stays on the fate and end reason (ADR runtime-worker/021). Carried item 42(b) of PLAN-V2-162 (`recordEnd` overwriting a concurrent `taskClosed` or `turnCompleted`) is closed here. The start-failure rule for a never-connected `KILLED`, `CANCELLED` or `REFUSED` worker stays with PLAN-V2-162 Deliverable 1. The `SupervisionPass.java:340` glitch the lane named was already settled by `4917a034` (#529).

Advisory residue left by the reviews (each logged in the plan, none blocks): the per-reason cases of `WorkerTerminationTest` and the every-end-reason case of `SupervisionPassTest` do not bind the reason (a wrong arm would stay green); `LossReason.recording` has no test for its null guard or empty branch (the only `fix_kind: implementation` finding); the `SupervisionPass` class Javadoc says every lifecycle change passes the transition table while the registration onto the current generation does not; `model-work/09-completion-usage-and-spend.adoc:23` still says a worker the supervisor ends is `killed` while it settles `cancelled` (older than this change); the fate step reports `worker block terminal` also for a kept refused block; ADR runtime-worker/022 and the worker guide say the successor effect is recorded before it runs without naming that the queue lookup comes first and is retried; the fallback terminal-state write of `WorkerEnding.exited` is not a guarded effect, so a retry writes it again (the pass calls `exited` for settled records only); `runtime-worker.adoc` carries a lane report and an operator answer in a current-state document; a few new fixtures use literals; the refusal test asserts the type, not the message; the write-order paragraph of `job-runtime/01` reads as if the token revoke sits right after the fate step.

Operator decisions and deviations recorded in this run:

- The functional review was closed by the operator on criterion D5.C3 alone (the landing message cannot exist before the merge). The operator decided that at refine and at the first review; the same close was applied again at head 42f8195f, where a full round found nothing else blocking and the verifier accepted the grading. This message is the evidence the criterion asks for.
- At the merge gate the operator chose merge now with no second prompt (`final_merge_without_asking=true`, this plan only) and, after a cost note, to let the merge queue rebase instead of a local rebase and force-push (`auto_rebase_threshold=auto_resolvable`, this plan only). A local rebase would have re-fired every head-dependent verdict.
- `plan-marshall:plan-retrospective` was not run: the project config sets its lane to `off` and the step is `default_on: false`. No retrospective or lesson proposals exist for this plan.
- The CodeRabbit walkthrough comment (7a375c), edited after the last push, was accepted directly at the pre-merge barrier (it states Merge Risk Minimal) instead of a third triage dispatch.
- The merge queue offers no per-enqueue message; the repository merge-commit-message default was not changed, so the landed squash message is the queue's default for the title and body.
- Links check on the merge result with `main`: 0 inline schema blocks and 67 broken links, none on a line this change edits (GitHub URLs into `pm-mcp-clients` and anchors of the documentation checkout); R17 asked for 0 and the 67 are pre-existing.
- Cost: finalize re-fired the review steps after every head move (four commits after the first push). The metrics report 30.7M tokens over 21h42m wall (of which about 16h idle) for the whole plan.
- Test evidence: whole-tree verify 11789 tests, `pm-runtime` integration tests 45, Sonar 0 new issues with the gate OK (94.4% new-code coverage), ADR numbers 021 and 022 free on `main` (R11).
