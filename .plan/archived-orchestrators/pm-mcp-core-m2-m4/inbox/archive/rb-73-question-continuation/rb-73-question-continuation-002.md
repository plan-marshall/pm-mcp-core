envelope_version=1
sender_type=plan
sender_id=rb-73-question-continuation
epic=pm-mcp-core-m2-m4
kind=landing
created=2026-10-27T11:16:28Z

# Landing — PLAN-73 question-continuation (runbook lane, stopped before the merge)

guard: none on this harness (unenforced)

```landing-facts
schema=landing-facts/1
plan_id=rb-73-question-continuation
epic=pm-mcp-core-m2-m4
pr=#201
merge_state=open
cleanup_owed=true
deliverables_total=10
deliverables_done=0
total_tokens=unknown
harness=opencode
model=deepseek-v4.1-flash/high
effort=high
plugin_version=unknown
executor_version=0.1.2017
steps=setup:done,implement:done,build-gate:done,self-review:budget-exhausted,pr:done,reviews:pending,merge:pending
step.sonar-roundtrip.open_issue_count=unknown
step.sonar-roundtrip.open_issue_rules=unknown
```

## Deliverables

Library-side cores of D2, D3, D5, D7, D8 and D9 are implemented and green; D1, D4, D6, D7 (the supervisor half) and D10 are
not met as written, and none of the ten deliverables is fully met (`deliverables_done=0`): the engine/envelope offer
wiring, the runtime supervisor and the assembly transport entries are missing. The package row and file stay in
`doc/plans/`.

Per Done-when (all `order: none`, all tests written and run green in this run):

* hold continues and links return only after `ack` — protocol level met (`TaskProtocolContinuationTest.answerAndAcknowledge`).
* suspension resumes in a new generation — the offer is met, the launch is not (`resume`, `fenceKeepsTheAnswer`).
* each fallback closes `asked` with its reason — met at the protocol level (`fallbackFromHold`, `fallbackOfSuspended`, `fallbackOfHold`).
* the next task carries `facts.answers` — the value is met, no producer exists (`AnswersTest`).
* `ask fresh` / no `questions` never continue — met at the decision function (`QuestionContinuationTest`).
* missing answer ack ends the worker lost and the task resumes or is offered once — the deadline is met, the loss path is the supervisor's (`WorkerLossDetectionTest.answerAcknowledgement`).
* budget while holding — **not met**.
* restart keeps suspended — **not met** (no restart sweep; `suspendedSurvivesAPause` records the protocol fact).
* third ask is the last offered — ledger met, offer wiring absent (`QuestionBoundTest`).
* hold time not charged, usage marked — mark met, budget exclusion **not met** (`MetricsStoreTest.continuationMark`, `ContinuationMarkParityTest`).
* stage `resumability` records measured forms — result met (`ProfileStagesTest`).

## Personas and skills

`persona-implementer`, `persona-module-tester`, `persona-documenter` and `persona-code-reviewer` (both self-review
rounds) applied as standards. `resolve` reported `skills: none named`; no skills added.

## Hypotheses

Claim 0 (contradicted at HEAD) confirmed by reading: `runtime.worker` held 34 files, `workflow.task` held
`TaskProtocol`, and only `workflow.question`/`workflow.hypermedia` were package-info.

## Findings

* Self-review round 1 (subagent): 20 findings. Fixed: the answer-fact bounds and branch-key grammar, the stage value
  shape, the delivery leak and its unconditional removal, the false persistence/caller/lexical claims in the docs, the
  missing `runtime.worker` ADR/doc, and the non-discriminating restart test. Open by design: nothing is wired outside the
  library; the limits are recorded but the enforcement loop is the supervisor's; `resume` offers a suspension to any
  matching waiter; `AnswerFact.Kind` repeats `QuestionRecord.AnswerKind` (accepted, documented).
* Self-review round 2 (subagent): the round-1 items above judged fixed/partly, plus 10 new findings. Fixed: the
  resumed answer destroyed by a neighbouring poll and by a fence, the `core.store` → `core.queue` cycle (own
  `ContinuationMark` + parity test), the scope-ambiguous answer (scope-qualified overload; a bare id delivers to nobody
  when two scopes hold it), the server-side fallback of a hold, the `suspend` guard, the stale docs and the
  `pauseClearsContinuation` test. Documented as residue: the duplicate kind vocabulary.
* CodeRabbit (reviewed `360aea86`, `kind=reviewed`): 3 inline findings and 1 outside-diff finding, all fixed in
  `d9326726` and their threads resolved; 2 nitpicks answered in `6440332691` and `6440351693`.
* Sourcery: refused on quota (its own budget, 250k diff chars, next request 2026-11-02) — optional bot, no gate impact.
* Sonar (R18 read of the public API at `360aea86`): gate PASSED, 8 open issues, all in changed files; all 8 fixed in
  `5c52abf5`, re-analysis pending at the stop, so the merge head's count is `unknown`, never `0`.

## Reviewers

| Reviewer | Required | State | Head | History |
|---|---|---|---|---|
| coderabbit | yes | reviewed, then silent | `360aea86` (marker `kind=reviewed`, `coveredCommitId=360aea86`) | triggered 3×; re-triggered after the last push, answered `NEUTRAL (skipping)`; 0 included reviews remain |
| sourcery | no | refused on quota | — | trigger refused on its budget; optional |

Standing review authorization for coderabbit (`quota_limited`): a qualifying review exists on a head of this PR, every
finding is handled and every thread resolved, it was re-triggered once after the last push, and the other conditions are
assessed on `5c52abf5`.

## Operator questions

1. Executor skew `could_not_check` — options {Proceed, Regenerate, Stop}; selected **Proceed with this executor**.
No free-text instruction was given beyond "continue with the plan".

## Rule check

* **Guard: none on this harness (unenforced).**
* Breaks carried from the first session (recorded, corrected): edits written to the main checkout by relative path and
  copied to the worktree afterwards; shell file reads instead of the file tools; one parallel-git index-lock collision;
  the D5/D9 commit shared (message amended).
* This session: builds ran `in_process` (the routed job failed `execution_failed` in the first session); the full-reactor
  gate exceeded its bound and was continued per module; the self-review was run twice, both rounds' findings recorded.
* Spec read at setup: `spec_source: main_checkout`, no `spec_differ` marker.

## Build server

Setup preflight `ready`; routing `auto` with an executor present, but the routed submission failed `execution_failed`, so
every build ran `in_process` (`mechanism=in_process_fallback`).

## Residue

* CI was `build / gate PENDING` on `5c52abf5` at the stop: the merge gate's CI condition is not met, so no merge was
  attempted.
* Not implemented: the engine `<state>.asked` / envelope `ask:TASK-NNN` offer and bound wiring; the supervisor's hold
  losses and budget recycling; the resumed generation's launch and session read; the assembly's `WorkerCalls` answer
  entry; the `docs-consistent` deletion of the implemented specification sections and watch items.
* The package row and `doc/plans/question-continuation.adoc` remain (owed to the remainder).
