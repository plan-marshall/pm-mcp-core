# Landing Analysis: PLAN-V2-31 — The supervisor's inbound calls and its supervision pass

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #472 (**open**, head `b99b45ce`, base `1e9466ac`; not merged, not reported ready by the lane)

> Landing record for one plan whose pull request is **open**, **delivered as specified, with an end path that cannot complete against the landed
> store and four unnamed defects**. Written by `analyze` (inbox scan) from the lane's landing message
> `rb-v2-31-supervisor-calls-and-pass-001.md` (`deliverables_done` 5 of 5, `merge_state=open`, `cleanup_owed=true`). The reading is of the PR
> head, not of `main`. A claim of the message is a lead; the blocker's chain, the detection arming, the end path's side effects and the
> missing scheduler were re-read at the head.

## Deliverable Fidelity vs Spec

| Deliverable (spec) | Verdict | Evidence |
|--------------------|---------|----------|
| D1 `SupervisorCalls` implements `WorkerCalls` | met, with one gap against the spec's "first sight" | all six methods `@Override` (`SupervisorCalls.java:80,99,153,164,177,187`); the heartbeat refinement (`acknowledged` instead of `called`) is a documented superset; `LossDetection.called` now has no caller (grep). The detection is created at the first call, not at the first sight of the generation (N-1) |
| D2 `SupervisionPass.run()` | met | one `clock().instant()` (`SupervisionPass.java:88`), no thread, no schedule (grep), a pass that finds nothing writes nothing |
| D3 five scenario groups | met, with weak assertions | `SupervisorCallsTest` 18, `SupervisionPassTest` 8; two "no detection created" assertions do not observe detection creation (T-1); the "no generation" test is trivially satisfied (T-2); the tail pump, the `START_FAILURE` branch, the requested-end branch and the retried completion are untested (T-3) |
| D4 ADR 014, the guide, Traceability | met, partly overstated | ADR 014 is the next free number; the PM-WORK-3 and PM-WORK-7 implementation text says the pass "settles an ended job" and drives "the attempt's end", true only against the fixture's mock (Doc-2) |
| D5 the owed rest | met | the guide's "What the package still owes"; no row, file or section removed (the PR body was not read) |

## Three-Layer Analysis

One read-only leaf (`execution-context-level-3`, `orchestrator.analyze`) read `git diff 1e9466ac..b99b45ce` (8 files, +1731/-9) and the files at
the head through `git show`. The orchestrator re-read the store refusal (`JobRecordStore.java:328-332`), `SupervisionPass.advance` and
`completeEnd` (`:100-116`, `:155-185`), `SupervisorCalls.detection` (`:206-218`) and `WorkerEnding.exited` (`:139-158`), and grepped the head
for a caller of the pass. By reading, no build, no test run, no write.

### Layer 1 — Specified

| Finding | Claim | Verdict | Polarity |
|---------|-------|---------|----------|
| L1-1 | D1 to D5 met | met as specified; the premise of the end path is refuted (below) | the lane is right |
| L1-2 | D1 "created at the first sight of the generation" | partly: the detection is created by a call or by `completeEnd`, never by the pass for a running generation | the lane did not name it |
| L1-3 | `StartGuard.firstCall` (+12) is a seam addition | confirmed; `StartFailureGuard.firstCall` already existed and now implements it without `@Override` | info |
| L1-4 | spec says `zero_tool` is settled by its structured result | the pass records every exit with a first call as LOST/EXITED; no special case | spec-level, open |

### Layer 2 — Implemented: the end path

| Link | Reading | Verdict |
|------|---------|---------|
| the pass completes an end only for a settled record | `SupervisionPass.java:108-109`; an absent record returns silently (`:105-107`); no other exit observable exists in this repository | confirmed |
| `exited` writes the terminal worker state through the store | `WorkerEnding.java:150` → `WorkerStateWriter.over` (`SupervisorEnvironment.java:194-213`) → `JobRecordStore.writeWorker` | confirmed |
| `writeWorker` refuses a settled record | `JobRecordStore.java:328-332` (`IllegalArgumentException`), re-read | confirmed |
| the runner writes the fate at settlement | `SettlementStep.java:204-207` → `writeFate` | confirmed |
| the first pass over an ended worker throws and aborts `run()` | the exception is an `IllegalArgumentException`; the only two catches are for `IllegalStateException` (`SupervisionPass.java:144,200`); later generations and `yieldIdleSlot` are skipped | confirmed |
| a retry repeats the side effects | `exited` runs `cancelGrace`, `requests.remove`, `fence`, `revoke` and `slots().release` before the write (`WorkerEnding.java:143-150`); on the retry `request == null`, so the cause becomes LOST/EXITED and a DRAIN, PAUSE or RECYCLE is lost | confirmed |
| the mock hides it | `SupervisorTestFixture.java:422-429` is a recording mock; every pass-end test asserts the mock's writes; no test runs `WorkerStateWriter.over` over a settled record | confirmed |
| the pass is latent | grep of the head: nothing outside the two classes and their tests constructs `SupervisorCalls` or calls `SupervisionPass.run`; the assembly is not inspected | confirmed here, assembly unverified |

### Layer 3 — Correct

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| B1 | the end path cannot complete in production against the landed store | confirmed | **blocker for the assembly**, latent until the pass is scheduled |
| N-1 | **no detection for a worker that never calls**: `LossDetection.started` arms "silent from spawn", but `advance` never creates the detection for a running generation; a worker that hangs at launch is never lost by silence, only the runner's budget ends it; no test | re-read | major |
| N-2 | log tail misattributed across a warm worker's tasks: the pass pumps only while a task is leased, `taskClosed` does no final pump, the pump is per generation, so lines written before `taskClosed` are appended to the next task and earlier attempts get no end entry | leaf reading, not re-read; the recorder's treatment of earlier attempts unverified | major (unverified) |
| N-3 | `exited` is not idempotent and the pass has no retry guard: any transient store failure leaves fence, revoke and release done and the generation non-terminal; whether a double `ModelSlots.release` harms is unverified | confirmed (generalises B1) | major |
| N-4 | `completeEnd` holds `synchronized(calls)` across `ending.exited`, which writes the store and can start a successor process; `lost(...)` takes the same monitor on the one timer thread, so every deadline dispatch stalls during an end | leaf reading, not re-read | minor to major |
| N-5 | a possible lock-order cycle `calls → guard → supervisor` against `supervisor → guard` in `WorkerSupervisor.start`; the cycle exists already in `afterTaskClosed → clear`, the new call paths widen it | leaf reading, latent | minor |
| N-6 | `completeEnd` uses the generation snapshot taken at the start of `run()`; a concurrent `taskClosed` or `turnCompleted` since then is overwritten | leaf reading | minor |
| N-7 | `IllegalStateException` swallowed in `pump` and `finishAttempt` with no log record; a mid-batch failure can re-append lines | leaf reading | minor |
| N-8 | a `fresh` worker's normal end is recorded LOST (`EndCause.TASK_DONE` has no production caller); `zero_tool` is not special-cased | confirmed (the lane named the first half) | major, spec |
| N-9 | `detections`, `losses`, `roleDigests`, `active` and `finished` are cleaned only by `completeEnd`; a registry retirement does not clean them | confirmed, narrower than the lane says | minor |
| N-15 | `SupervisorCalls.detection`, `loss` and `forget` are `public` and used only in the package | new | minor |
| T-1..T-3 | weak or missing assertions (above) | new | minor |
| Doc-2, Doc-3, Doc-5 | Traceability implementation text overstates; ADR dated 2026-11-16 records a 2026-11-17 decision; the guide's Decisions list stops at ADR 009 | new | minor |
| N-12 | generic catches | none; `Optional` and `@Nullable` rules kept; no `LogRecord` added | clean |

## Review and Quality Numbers

| Number | Message | Re-read |
|--------|---------|---------|
| unresolved review threads | 0 | not re-queried; **no reviewer ran** (CodeRabbit not triggered, Sourcery skipped), so zero is the absence of a review, not a handled one |
| review body findings total / handled | 0 / 0 | same |
| Sonar open new issues | 0 | the SonarCloud check on the head is green (`gh pr view 472`); the project's open issues were not queried |

CI on head `b99b45ce` is green (`build / build (25)`, `(26)`, `sonar-build`, `integration-tests`), merge state `CLEAN`. `landing-check`:
`complete=false`, missing `total_tokens` and `plugin_version` (register E27). Surface delta unmeasured. The lane ran four self-review rounds and
reported the blocker instead of fixing it, on the operator's word of 2026-11-17.

## Residue

1. **A follow-up plan owes the end path** (`epic.md`, Open Defect "PLAN-V2-31 end path"): the terminal worker write that is compatible with the
   store (B1, N-3), the detection armed for a worker that never calls (N-1), the attempt attribution of a warm worker's log (N-2), the monitor held
   across the store write (N-4), the retry guard of `exited`, the `fresh` and `zero_tool` end classification (N-8). The design choice is the
   operator's (a store-compatible write, a second exit observable, or a relaxed store refusal).
2. **Carried into PLAN-V2-114, item 42 (after #472 merges):** N-5, N-6, N-7, N-9, N-15, T-1 to T-3, Doc-2, Doc-3, Doc-5, `@Override` on
   `StartFailureGuard.firstCall`.
3. **Open decision, the operator's:** whether #472 merges as it is. The classes are unused by production code (latent), the checks are green, no
   required reviewer ran, and a follow-up plan builds on them.
4. **Not routed:** the `waitOpened` finally path (untested by the lane's own statement, not checked); the 15 s acknowledgement default of the
   shipped `claude` profile (lives in `plan-marshall-mcp`).
