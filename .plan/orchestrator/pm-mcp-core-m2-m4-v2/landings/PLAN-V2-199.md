# Landing Analysis: PLAN-V2-199 — the supervision pass's T-3 test gaps, the finishAttempt throw-after-append point

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #648 (https://github.com/plan-marshall/pm-mcp-core/pull/648), merge `2adcbc2a` (squash through the merge queue; the lane's pull request went 639, 643, 646, 648 by three close-and-reopen retries for the CodeRabbit account quota), analysed on the forty-fifth run, 2026-11-28

> Landing record. Source: the plan's inbox message `plan-v2-199-supervisor-t3-test-gaps-005` (landing-facts present, `merge_state=merged`, `complete: false`, see Metrics) with four candidate lessons (001 to 004), read against git (`git diff 2adcbc2a^ 2adcbc2a`: 15 files, +528/-49) by one read-only leaf (`execution-context-level-3`, R21) that was given the spec and the message as data and ran no build. The orchestrator re-read L3-1 at `ActivityJournal.java:190-221` and `FileLockManager.java:210-236` (head).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the fixture first | shipped, in a different place | a helper `settleAsExitBeforeTheFirstCall` in `SupervisionPassTest` (about `:316-330`) drops `connectedAt` and settles through `SettlementStep.fate`; `SupervisorTestFixture` untouched; the fixture test `anExitBeforeTheFirstCallIsSettledWithoutASpacingWait` asserts the settled state, `START_FAILURE`, lifecycle `START_FAILED` under `assertTimeoutPreemptively(3 s)` |
| D2 tail pump before the exit | shipped | `theTailIsAppendedBeforeTheAttemptEnd`: kind order TEXT, TEXT, TEXT, ATTEMPT_END, the cause `exited_without_submit` |
| D2 START_FAILURE with its matched control | shipped | `aSettledWorkerIsAStartFailureOnlyWithoutItsFirstCall`, two rows: no first call gives `START_FAILED`, called first gives `LOST` and `exited` |
| D2 requested end beats loss and recorded reason | shipped | `aRequestedEndWinsOverTheLossAndTheRecordedReason`: record carries TASK_DONE, the end is `DRAIN`, exactly one end |
| D2 retried completion | shipped | `aCompletionThatThrowsAfterTheAppendAppendsTheEndOnce`: one end after the failure and after the retry, `drain` kept, the generation terminal only after the retry |
| D2 mutation-after control per test | not shown by reading | the mutation runs and their log ids are not in the diff |
| D3 `finishAttempt` throw-after-append | shipped, a fix chosen | tests first (ADR runtime-worker/026: "reproduced this before the fix"); idempotence per attempt; the order fix, "mark before append", a catch and a journal read rejected each with its reason; `WorkerActivityPump` +54, `ActivityRecorder` +24, `SupervisionPass` comment-only |
| D4(a) `PM_RUNTIME-152` by constant | shipped, slightly off the stated form | `WorkerRecyclingTest:299-300` `assertSingleLogMessagePresentContaining(WARN, ...HOLD_ENDED_TASK_UNREADABLE.resolveIdentifierString())`; 153 left to PLAN-V2-198 |
| D4(b) `ProbeBudget` | shipped | `MODELS_SECONDS` replaces `30L` at `ProbeBudgetTest:84` (`:43`, `:67` are the contract table); `override` and `clearOverrides` package-private, callers `ProbeBudgetTest` and `ProbeJobTest` in the same package |
| D4(c) `JobLog` chars against bytes | shipped | a `Tail(window, keptBytes)` record, condition `tail.keptBytes() == cap` (`JobLog:363`); `aCapBelowTheHeadBoundNeverRepeatsAMultiByteFirstLine` (514 times "ä" and a newline, cap 1023) is a real regression test (old: 512 != 1023, the first line repeated) |
| D4(d) wall-clock bounds | shipped | Javadoc states the bound is the control; `PEM_BOUND` 5 s to 10 s (below the measured 18 s regression), `FLOOD_BOUND` 120 s with its derivation |
| Objective: the 30 s hold | partly shown | the helper's Javadoc says a second start on the same harness would wait the whole `HarnessStartSpacing.SPACING_BOUND`, so it settles the turn-holding generation and starts nothing: consistent with the Re-Ground Record (claim 1 contradicted); the landing's own account of the re-run of the reverted test was not read |

## Three-Layer Analysis

### Layer 1 — Specified

Four deliverables shipped; one row (the mutation controls) cannot be shown by reading; the fixture lives in a helper of the test class, not in `SupervisorTestFixture` (acceptable).

### Layer 2 — Implemented

15 files, +528/-49. Outside the Expected Surface: `doc/Traceability.adoc` (an ADR 026 row) and the new ADR `pm-runtime/doc/adr/runtime-worker/026-the-attempt-end-is-appended-once-across-a-retried-completion.adoc`, both expected bookkeeping. About 30 assertion messages sampled, all 20 to 60 characters; EasyMock only, no new hand-written fake, generators used, literals are wire names with a comment.

### Layer 3 — Correct

No major defect. A retry cannot skip a needed append: `ended` is set only inside the callback after `appendNext` returned normally, and a throw before the commit leaves it unset; the match is on `queueTaskId`, so a kept end for task A with `finish(B)` appends B's end. The `JobLog` change is correct (`keptBytes == cap` holds exactly when the window was not trimmed).

| Defect | File:line | Reading | Effect | Severity |
|--------|-----------|---------|--------|----------|
| L3-1 | `ActivityJournal.java:199-221`, `FileLockManager.java:217-234`, `ActivityRecorder.java:99-111` | **re-read:** `appendNext` writes inside `try (var transaction = lockManager.openTransaction())`; `Transaction.close()` rethrows an unlock failure after the write, so `recorder.append` throws before `onWritten`, `ended` stays unset and the retry of `finish` appends a second `attempt_end` (CodeRabbit 5e77be is TRUE); ADR runtime-worker/026 calls the signal report "the one step after the commit that can" throw, which is false | the point #648 closes is open for a lock-release failure; the two retry tests drive the throw through an `outside_footprint` signal and do not cover it | minor |
| L3-2 | `ActivityRecorder.java:110` | `SecretRedactor.redact(written[0])` runs before `onWritten`; pure and unlikely to throw | a third post-commit step in the same window | info |
| L3-4 | `WorkerActivityPump.finish`, ADR "Consequences" | the retry's `completeEnd` pumps the tail before `finish`, so lines the log gained since are appended after the committed end; the kept-end path returns the old entry with an older `unmappedLines`; the failed off-track signal is not re-reported (one signal lost for the attempt); all admitted by the ADR | known costs | info |
| L3-5 | `WorkerActivityPump` | `ended` is a non-volatile field, like the other pump fields, used by a single pass thread | none while the pump is single-threaded | info |
| vacuity | `anExitBeforeTheFirstCallIsSettledWithoutASpacingWait` | the 3 s preemptive timeout catches a 30 s wait, not a 1 to 2 s wait | the "no wait" assertion is coarse | info |

**Layer 3 verdict:** findings — 5 points (one minor, four info); not shown by reading — the mutation-after run of each test and its log ids, that each new test fails against the pre-fix code at the head, `verify -Ppre-commit`, `verify` of `pm-runtime` and `pm-core`, coverage, integration tests, Sonar, the per-class test counts, the landing's account of the re-run of the reverted START_FAILURE test, and the callers of `ProbeBudget.override` in `plan-marshall-mcp`. No major finding, nothing reported at once.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md Code Standards | assertion messages of 20 to 60 characters, EasyMock only, generators | conforms | the sample above |
| CLAUDE.md Module Documentation | one ADR per design decision | conforms | ADR runtime-worker/026, with the false sentence of L3-1 |
| CLAUDE.md Git Workflow | merge through the queue | conforms | squash `2adcbc2a`; the branch was rebased once over nine upstream commits (operator-approved at the overlap gate on `doc/Traceability.adoc`), patch-identical per `git range-diff` |

## Metrics and Anomalies

- Harness claude, model `claude-sonnet-5-5`, effort `unknown`, plugin `0.1.2184`, executor `0.1.2184`; `total_tokens=26392893`, `total_wall_seconds=86899` (24 h 8 min); `complete: false` for four keys, all unobserved facts recorded `unknown`, never 0: `effort`, `unresolved_review_threads`, `review_body_findings_total`, `review_body_findings_handled`. `functional-review`: 3 deliverables met, 2 met with residuals, 0 not met; code review: 0 blocking, 6 advisories, 5 rules cited.
- `pr` of the `create-pr` step reads `#639`; the merged pull request is #648 (three close-and-reopen retries for CodeRabbit's account quota; the operator answered three escalations). The lane's metrics: all 31 dispatch-boundary rows are keyless (lesson 004).

## Routing and Merge Behavior

- Merge: through the merge queue as `2adcbc2a`.
- Four candidate lessons (001 `plan-marshall:automatic-review` close-and-reopen recovery across PR numbers for an account-scoped quota, 002 `phase-3-outline` Maven `-Dtest` filters with commas, not plus signs, 003 `review_completeness trigger-bot` traceback, 004 `manage-metrics` `--step-id` on every `record-dispatch-boundary` call) forwarded verbatim to the plan-marshall `lessons-routing` inbox as `pm-mcp-core-m2-m4-v2-103` to `-106` (uncommitted in that ledger until its land).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#648`; `landing` `landings/PLAN-V2-199.md`; `plan_marshall_plan_id` `plan-v2-199-supervisor-t3-test-gaps`
- [x] the five messages archived (the four lessons forwarded first)
- [x] L3-1 and the ADR sentence staged as deliverable 5 (d) of the staged PLAN-V2-150 (it owns `ActivityRecorder`, `WorkerActivityPump` and `ActivityJournal`); L3-2, L3-4, L3-5 and the vacuity point are recorded here without an owner (the ADR admits L3-4)
- [x] the Decisions entry and the Open Defects written; resume anchor updated; `queue-view.md` regenerated and committed

## Follow-Ups

- **Staged:** PLAN-V2-150 deliverable 5 (d) (test first, a lock manager whose release throws after the write; fix keyed on the committed entry; correct the ADR sentence).
- **Owed:** the mutation-after runs of the four tests (the log ids), if the operator wants them shown; `ProbeBudget` callers in `plan-marshall-mcp`.
- **Owner question (recorded, asked one at a time):** L9, how the retry knows an entry is committed when the lock release failed (read the journal's last entry, 60%, against letting `appendNext` return the committed entry).
