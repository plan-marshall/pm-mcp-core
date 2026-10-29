envelope_version=1
sender_type=plan
sender_id=rb-73-question-continuation
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-27T14:41:01Z

# Landing — PLAN-73 question-continuation (runbook lane, merged)

guard: none on this harness (unenforced)

```landing-facts
schema=landing-facts/1
plan_id=rb-73-question-continuation
epic=pm-mcp-core-m2-m4
pr=#201
merge_state=merged
cleanup_owed=false
deliverables_total=10
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2017
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:done,merge:done
step.sonar-roundtrip.open_issue_count=0
step.sonar-roundtrip.open_issue_rules=none
```

Standing rules: r1, R01–R18. Merge commit: `a7a355aa` (merge queue, merge_group run 40521649476).

## Deliverables

The library-side cores of D2, D3, D5, D7, D8 and D9 are merged; D1, D4, D6, D7 (the supervisor half) and D10 are not met
as written, and no deliverable is fully met (`deliverables_done=0`), because the engine/envelope offer wiring, the runtime
supervisor and the assembly transport entries are missing. The package row and `doc/plans/question-continuation.adoc` stay
in place for that remainder.

Per Done-when, all `order: none` (every test written and run green in this run; no `fails at {sha}` clause is claimed):

* hold continues and links return only after `ack` — protocol level met (`TaskProtocolContinuationTest.hold`, `answerAndAcknowledge`).
* suspension resumes in a new generation — the offer is met, the launch is not (`resume`, `fenceKeepsTheAnswer`, `answerAfterFence`).
* each fallback closes `asked` with its reason — met at the protocol level (`fallbackFromHold`, `fallbackOfSuspended`, `fallbackOfHold`).
* the next task carries `facts.answers` — the value is met; no producer creates the next task (`AnswersTest`).
* `ask fresh` / no `questions` never continue — met at the decision function (`QuestionContinuationTest`).
* missing answer ack ends the worker lost and the task resumes or is offered once — the deadline is met; the loss path is the supervisor's and is unwired (`WorkerLossDetectionTest.answerAcknowledgement`, `answerAcknowledged`, `informationalAnswerAcknowledgement`).
* budget while holding — **not met**: the rule is restored in Model Work 06 § Budget (operator decision, commit `31b43960`), but `WorkerRecycling` still only recycles and does not end the hold; the server API `TaskProtocol.fallback(queueTaskId, …)` and `suspendedSurvivesAPause` are its parts.
* restart keeps suspended — **not met**: no restart sweep exists; the protocol fact (no lease, no attempt) is tested.
* third ask is the last offered — ledger met, offer wiring absent (`QuestionBoundTest`).
* hold time not charged, usage marked — mark met, budget exclusion **not met** (`MetricsStoreTest.continuationMark`, `ContinuationMarkParityTest`).
* stage `resumability` records measured forms — result met (`ProfileStagesTest`).

## Personas and skills

`persona-implementer`, `persona-module-tester`, `persona-documenter`, `persona-code-reviewer` (both self-review rounds and
the PR-comment triage). `resolve` reported `skills: none named`; no skills added.

## Hypotheses

Claim 0 (contradicted at HEAD) confirmed: `runtime.worker` held 34 files, `workflow.task` held `TaskProtocol`, and only
`workflow.question`/`workflow.hypermedia` were package-info.

## Findings

* Self-review round 1: 20 findings; fixed the answer-fact bounds and branch-key grammar, the stage value shape, the
  delivery leak and unconditional removal, the false persistence/caller/lexical claims, the missing `runtime.worker`
  ADR/doc, and the non-discriminating restart test. Open by design: nothing wired outside the library, the unwired
  enforcement loop, the suspension offered to any matching waiter, `AnswerFact.Kind` repeating
  `QuestionRecord.AnswerKind` (accepted).
* Self-review round 2: round-1 items judged fixed/partly, 10 new; fixed the resumed answer destroyed by a neighbouring
  poll and by a fence, the `core.store` → `core.queue` cycle (`ContinuationMark` + parity test), the scope-ambiguous
  answer (scope-qualified overload), the server-side fallback of a hold, the `suspend` guard, stale docs and
  `pauseClearsContinuation`. Residue: the duplicate kind vocabulary.
* CodeRabbit reviewed `360aea86`, `049e0308` and `fdda8274`. Findings: the repeated-wait drop, the fenced-continuation
  answer, `holdsQuestion` rejecting `QUEUED`/`OFFERED`, the guide naming a non-existent `record`, the bounds, the
  `pauseClearsContinuation` test — all fixed (`d9326726`, `fdda8274`) and every thread resolved. Two nitpicks answered
  (the shared continuation-form type declined with the layering reason; the stale `@param` removed).
* Sourcery: refused on its own budget (250k diff chars); optional bot, no gate impact.
* Sonar (R18 read at the final head `fdda8274`): **0 open issues**, quality gate **OK** (new-code coverage 87.6 %,
  duplication 0.0 %).

## Reviewers

| Reviewer | Required | State | Head | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed | `fdda8274` (marker `kind=reviewed`, `coveredCommitId=fdda8274`) | 3 reviews; all findings fixed and resolved; re-triggered after each push |
| sourcery | no | refused on quota | — | own budget (250k diff chars), next request 2026-11-02 |

## Operator questions

1. Executor skew `could_not_check` — options {Proceed, Regenerate, Stop}; selected **Proceed with this executor**.
2. D7's missing budget-while-holding rule — options {Restore in Model Work 06 (Recommended), Leave it open, Restore in
   Model Work 03}; selected **Restore in Model Work 06** (commit `31b43960`).
3. Merge of #201 at `e95a3469` — options {Merge now, Hold, Merge now remainder follows}; selected **Merge now**; the merge
   was then re-run from the newer head `fdda8274` after two base-branch merges and the last CodeRabbit findings.

## Rule check

* **Guard: none on this harness (unenforced).**
* Breaks recorded and corrected: edits written to the main checkout by relative path in the first session (copied to the
  worktree; main restored clean); shell file reads instead of the file tools; one parallel-git index-lock collision; the
  D5/D9 commit shared (message amended); the first landing message was consumed before amending, so corrections were
  filed as new messages (`-002`, and this `-003`).
* Builds: the routed job failed `execution_failed` in the first session, so every build ran `in_process`; the first
  full-reactor bound expired, later `clean verify -Ppre-commit` of the whole reactor was green in 837 s (7950 tests).
* Base-branch merges: `origin/main` twice (conflicts in `question/package-info.java`, `AstNodeModelTest`,
  `core-store.adoc`, `workflow-dsl.adoc`), resolved keeping both sides; collided ADRs renumbered against `origin/main`
  (core-store 046 → 047, workflow-dsl 009 → 010, R11).
* Spec read: `spec_source: main_checkout`, no `spec_differ` marker.

## Build server

Setup preflight `ready`; routing `auto` with an executor present, but the routed submission failed `execution_failed`, so
all builds ran `in_process` (`mechanism=in_process_fallback`).

## Residue

Owed to a remainder (the package row and file stay):

* the supervisor half of D7: a worker reaching its token budget while holding ends the hold (the rule is now in Model Work
  06 § Budget) and is recycled; the ack-deadline loss path that resumes or re-offers the task exactly once;
* the restart sweep that keeps a suspended task suspended and ends a hold;
* the engine's `<state>.asked` offer and the envelope's `ask:TASK-NNN` offer, with `QuestionBound` wired into the
  representation;
* the resumed generation's launch from `headless.resumability.resume.argv` with the session id read from the structured
  result, and `WorkerCalls`' answer entry in the assembly (`plan-marshall-mcp`);
* the deletion of the implemented specification sections and watch items, and the package row/file removal.

## Sonar

`resolve` reports `sonar: not_configured`, so the lane's plan-less read is skipped; the public API read taken after the
last push (`pullRequest=201`) reports 0 open issues and a green quality gate, rendered above as the lane's facts.
