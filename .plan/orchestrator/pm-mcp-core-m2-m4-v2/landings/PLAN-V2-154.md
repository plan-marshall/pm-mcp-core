# Landing Analysis: PLAN-V2-154 — supervisor-end-path-settlement

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #518 (squash merge `45a0d33c`, 29 files, +1906/-189)

> Landing record, runbook lane (`rb-v2-154-supervisor-end-path-settlement`, harness opencode, model `deepseek-v4.1-flash/high`). Two messages: revision 1 (`-001`, 2026-11-19T23:32Z, `merge_state=open`, absorbed as a Watch and archived) and revision 2 (`-002`, 2026-11-20T11:44Z, `merge_state=merged`, drained 2026-11-20).
> `inbox landing-check`: `complete: false`, missing `total_tokens` and `plugin_version` (both `unknown`). The lane stopped once at its context budget, was restarted by the operator on 2026-11-20, rebuilt its state from git and the pull request (the progress file had stopped at "self-review round 1 pending"), and finished; the commit `28804e96` (the F2 fix, source only, no test) was pushed before the restart and its author cannot be attributed (same author and committer identity as the lane's commits).

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 the terminal worker write at settlement | shipped-as-specified | `SettlementStep.withTerminalWorker` takes the lock, reads the stored block and derives the terminal block (`WorkerTermination`); a worker's scope is PROJECT or MACHINE so `lockKey` equals the `writeWorker` lock; `theFateStepWritesTheTerminalWorkerBlock`, `theFateStepKeepsTheNewestWorkerState`, `theFateStepKeepsAStoredTerminalBlock` fail on the parent; `aSettledRecordEndsThroughTheRealStoreWriter` uses the real writer and store; the retained fallback writer for an unsettled record is dead in production; the pass tests settle through their own helper, not `SettlementStep.fate`; the `StoreRefusal` branch has no direct test |
| D2 retry guard, one failure not aborting the pass, idle yield | shipped-modified | claim and progress live on the generation's `Request`, effects run outside the monitor; `aFailedEndIsReportedAndItsRetryKeepsItsCause` makes the `workQueued` supplier throw (not the terminal write); per-effect resume has no test; **the Done-when "a throwing `yieldIdleSlot` does not replace the exception in flight" has no test** and `PM_RUNTIME-149`/`150` are asserted nowhere (re-read: the only `yieldIdleSlot` tests are `WorkerRecyclingTest`'s); `WorkerRecycling` can still pick a generation whose end already ran |
| D3 detection of a worker that never calls, end work outside the monitor | shipped-modified | `calls.detection` at the first `advance`; `aDeadlineIsDispatchedWhileTheEndRuns` fires a real deadline on the virtual clock during `exited` and fails on the parent by timeout; the lock order is not widened but two windows are new (C5, C6) |
| D4 `LossReason.attemptEnd()` | shipped-as-specified | a documentation decision (ADR 020); `attemptEndOf` (`SupervisionPass.java:361-371`) re-maps three loss reasons by hand although ADR 020 says "no second loss table" |
| D5 end classification, warm worker's log tail | shipped-modified | the pass classifies an unrequested end by the recorded reason; `EndCause.TASK_DONE` still has no production caller; N-2 confirmed and fixed (the tail was attributed to the next task); zero_tool unit-level only; the F2 fix has no test |
| D6 documents | shipped-modified | ADRs 017 to 020, guides, watch, Traceability written; **four documents the change made false remain** (L3-4) |

## Three-Layer Analysis

- **Dispatch:** one read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) — reason: a full-ship landing is read by its own leaf with no size qualifier; the leaf wrote nothing and checked SonarCloud over HTTP.
- **Re-read by the orchestrator** (all held): `SupervisorCalls.java:42-44` at `45a0d33c` (the Javadoc still says the pass takes the monitor while it completes an end; the pass now completes it outside the monitor); `git grep` of the tests for a throwing yield and the `PM_RUNTIME-149`/`150` constants (none; the log message is defined at `RuntimeLogMessages.java:1242`); the review gate (`unresolved_review_threads: 0`, `review_body_findings_total: 22`: both equal the landing; `review_body_findings_handled=22` re-verified from 22 `finding:` lines of the progress file; 16 review threads, 0 unresolved); the SonarCloud check on #518 (`SUCCESS`, 2026-11-20T11:31Z).
- **Not re-read, recorded as unverified leads:** the persisted end-reason mapping (`WorkerTermination`, `WorkerEnding.java:196-202`, `:362`) against `timeouts/01:302,310` (C1; the file path the leaf cited does not resolve in the merge, the finding is the leaf's); `ModelSlotScheduler.release` idempotence; whether `START_FAILED` reaches the start-failure guard for a `KILLED` or `REFUSED` never-connected worker; whether the assembly's role-class function can return null; the lane's failing-run logs; the Traceability PM-WORK-7 row.
- **Verdict:** layer 1 one minor, rest info; layer 2 findings; layer 3 two major (one an untested Done-when, one an unreported deviation), minor and info. No blocker.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity | Evidence |
|---------|-------|---------|----------|----------|
| L1-1 | the terminal block needs no new store member; "the same lock" | checked | confirms | `JobRecordStore.writeFate`; `JobRecord.java:664` |
| L1-2 | N-2 (the warm worker's tail goes to the next task) holds | checked at the parent | confirms | parent `SupervisionPass.advance` pumped only `if (task != null)` |
| L1-3 | D4 "one table maps a loss to its attempt end" | checked | refutes (partly) | `SupervisionPass.java:361-371` |
| L1-4 | D5 zero_tool "the end the specification states": the statement is `model-work/03:55-57`, not 04; unit-tested only | checked | info | |
| L1-5 | the item-42 residue stays with PLAN-V2-156 item 42 | checked | refutes (partly) | the lane added a new `public SupervisorCalls.remember`, the opposite of item 42(e); M-2, M-3, the ADR 014 and guide blocker text, the `run()` Javadoc and the Traceability PM-WORK-3 row were closed, as the spec allows |

No finding names a labelled claim of a staged spec that this landing refutes; nothing is persisted by `corpus set-verdict`.

**Layer 1 verdict:** findings — 5

### Layer 2 — Implemented

| Deliverable | Code trace | Test that can fail | Unexplained file / unborne claim | Verdict |
|-------------|------------|--------------------|---------------------------------|---------|
| D1 to D5 | as above | the tests the lane cites fail on the parent (judged by reading the tests, the lane's logs not opened) | `LessonStore.java` (+ `LessonStoreUntrustedCitationTest`), `SlotGrammarTest`, `InputBindingTest`: whitespace-only rewrites of the pre-commit profile in a separate commit (`42181171`); revision 1 said "eight pm-workflow files", the final diff has two | checked |
| R12 | `PM_RUNTIME-149`, `150`, `151` defined and documented in `doc/log-messages/pm-runtime.adoc` | only 151 asserted with `LogAsserts`; 149 and 150 asserted nowhere (CLAUDE.md) | | checked |
| report | n/a | n/a | the PR body's line cites are about 15 lines off (written at head `42181171`) and say the test makes "the end's store fail" where it makes the work-queue supplier throw; "no second loss table" (L1-3); the progress file's task notes say the self-review round 2 was not dispatched | recorded |

**Layer 2 verdict:** findings — 4

### Layer 3 — Correct

| Defect | File:line | Triggering input | Effect | Severity |
|--------|-----------|------------------|--------|----------|
| L3-1 (C1) | `WorkerTermination.java:48-72`, `WorkerEnding.java:196-202`, `:362` (the leaf's reading, not re-read) | a silence-lost worker, cancelled, fate KILLED | the persisted `end_reason` of a supervisor-requested end is fate-derived: the record says `drain` where `timeouts/01:302`, `:310` say `silence` and `ack_missed`; the request's reason reaches the record only in the unsettled fallback write the pass never uses; the Javadoc admits "coarse", the landing and PR body report no deviation; whether the operator's design of 2026-11-17 accepts it is not shown | major (an unreported deviation, a decision) |
| L3-2 (C3) | `SupervisionPass.java:122-140` (re-read: no test) | a throwing idle yield | the Done-when of D2 has no test; `PM_RUNTIME-149`, `150` unasserted | major (Done-when unmet) |
| L3-3 (C2) | `SupervisionPass.java:229-232` | a job settled after its task closed while the pump holds a tail | the F2 fix has no test: removing the `else closeAttempt` probably leaves everything green; an unreadable tail of a settled worker still drops the pump with no end entry | minor |
| L3-4 | `SupervisorCalls.java:42-44` (re-read), `runtime-worker.adoc:465,514`, `SupervisorEnvironment.java:165-175`, `SupervisionPass` Javadoc `:40-41`, ADR 014:64, the specification's write-order sentence, `WorkerEnding.java:45` | reading them | four documents the change made false are not fixed: the monitor guarantee, `WorkerStateWriter` as the "terminal-state writer", the write order (block added, revoke and fence still placed with the fate while the pass performs them later), "the end is logged once" | minor (documentation; D6 "documents say what the code does" is not delivered) |
| L3-5 (C4) | `WorkerRecycling.java:220-233` | `yieldIdleSlot` over a waiting generation whose end is requested or failed | `ending.end` no-ops but "recycled" is logged and a cause returned (the spec's "nor picks a generation whose end already ran" is not delivered) | minor |
| L3-6 (C5) | `SupervisionPass.java:109`, `:304`; `WorkerSupervisor.java:189` | a concurrent `taskClosed` or `turnCompleted` | `recordEnd` does `supervisor.replaced(terminal)` unconditionally on the snapshot taken at the start of `run()` and overwrites it (widens item 42(b)) | minor |
| L3-7 (C6) | `WorkerEnding.java:209-211` | `planPaused` or `forceEnd` between `requests.remove` and `replaced(terminal)` | installs a request for an ended generation (a spurious `cancel`, a leaked map entry): the review's F6, not fixed | minor |
| L3-8 (C7) | `WorkerEnding.java:203-212` | `workQueued` or the successor throws after the effects | the retry re-runs `successor.run()` and re-logs `WORKER_GENERATION_ENDED`; the successor is not under the guard | minor |
| L3-9 (C8) | `SupervisorCalls.java:280`, `SupervisionPass.java:125` | the role-class function returns null | `Objects.requireNonNull` throws `NullPointerException`; `advanceReported` catches four other types, so it escapes `run()` and aborts the later generations (unverified whether the assembly can return null) | minor |
| L3-10 (C9, C10) | `WorkerTermination.java:52`; `WorkerActivityPump.java:151-168` | a never-connected `KILLED`/`CANCELLED`/`REFUSED` non-zero_tool worker; a mid-batch failure | counts as `START_FAILURE` before the fate switch (whether it reaches the guard is not shown); the pump sets its offset after the loop and re-appends lines | minor |
| L3-11 | `SupervisionPass.java:340`, `SettlementStep.java:~190`, `WorkerTermination` | reading | a code-formatting glitch, a report string that reads as if the block were unimplemented, an unimported `{@link JobRecordStore}`, a public class | info |

Exactly-once across a retry holds for the fence, revoke and release once each completes (at-least-once for the effect that threw); a double end is prevented (`run()` is synchronized); a settled record with an unreadable block is handled (`PM_RUNTIME-151`, the end completes). The pass stays latent: no production caller of `SupervisorCalls` or `SupervisionPass` (`git grep`).

**Layer 3 not shown by reading:** the build and coverage runs (the lane's counts 11 529 to 11 648), the integration tests (43), the lane's mutation logs.

**Layer 3 verdict:** findings — 11 (no blocker)

### Review-finding table of revision 1 (F-items at `45a0d33c`)

F1 fixed (`11ccf0e0`); F2 fixed in code, no test; F3 fixed; F4 fixed; F5 partly (per-effect `Request.with`, the successor not covered); F6 **not fixed**; F7 not fixed, now commented; F8 fixed; F9 **not fixed** (`REFUSED` still excluded from the "every fate" test); the D2 test gaps (a throwing yield, `LogAsserts` for 149/150) **not fixed**; the D3 test gap fixed by addition.

### Conformance to the Project's Own Rules

| Rule source | Rule | Verdict | Evidence |
|-------------|------|---------|----------|
| CLAUDE.md § Code Standards | every `LogRecord` a class logs is asserted by its constant with `LogAsserts` | **deviates** | 149, 150 |
| spec D2 Done-when | a throwing `yieldIdleSlot` does not replace the exception in flight | **deviates** (untested) | L3-2 |
| spec R07 | ask at refine, report a deviation from a specification | **deviates** | L3-1: the end reason, not reported |
| spec D6 | documents say what the code does | **deviates** | L3-4 |
| CLAUDE.md § Tool Usage | no shell file operations | conforms with reported deviations | two read-only greps, one chained command, no file operation outside `.plan/temp/` |
| CLAUDE.md § Git Workflow | the review gate met | conforms | 0 unresolved, 22 findings handled; the reviewer rate-limited on later heads, the exemption with the qualifying review on record |

## Metrics and Anomalies

- Tokens: `unknown`. Duration: not in the message. The lane stopped once and was restarted; the second self-review round was not dispatched after the review handling (a recorded deviation).
- Anomalies: CodeRabbit was rate-limited on the later pushes (NEUTRAL on `1910069d`, `cbf0ab56`, `5ca9a553`), Sourcery refused on quota; Sonar raised 10 new issues after the first review and 3 after the second, all fixed.

## Routing and Merge Behavior

- Review: coderabbit required (`review_complete_after_handling`): three reviews, 22 body findings and 16 threads, all handled; **both gate numbers equal the landing's claim, `review_body_findings_handled=22` re-verified from the progress file**; four items answered with a stated reason and named as follow-ups (below).
- CI/merge: merge queue, `45a0d33c`. **Sonar: confirmed.** The leaf read SonarCloud over HTTP for `pullRequest=518`: 0 open issues, quality gate OK, new-code coverage 91.8 %; the provider shows the check `SUCCESS` at 11:31Z.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#518`; `landing` stamped; `plan_marshall_plan_id` `rb-v2-154-supervisor-end-path-settlement`
- [x] epic.md: the decision, the Watch of revision 1 retired, the Watch "landed"
- [x] the residue staged as PLAN-V2-165 `supervisor-end-path-closeout` (WS-06, plan-marshall: concurrency fixes and a decision); the item-42 text of PLAN-V2-162 re-read (the parts #518 closed are stale there)

## Follow-Ups

- The four follow-ups the lane named (the offer-time task boundary in the activity pump, per-line source offsets from `JobLog`, a pump that outlives its generation, the recorded budget against the exit loss's attempt end) had no owner: they are PLAN-V2-165 Deliverable 5.
- C1 needs the operator's decision (the persisted end reason): PLAN-V2-165 Deliverable 1, asked at refine.
- The assembly's scheduling of the pass stays owed to the milestone exit (`plan-marshall-mcp`).
