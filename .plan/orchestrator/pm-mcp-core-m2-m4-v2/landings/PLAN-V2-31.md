# Landing Analysis: PLAN-V2-31 — The supervisor's inbound calls and its supervision pass

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
pr: #472 (**merged 2026-11-17 as `3d453e6c`**, head `2d5068bd`, first parent `4bc06ae3`; 8 files, +1740/-9; first read open at `b99b45ce`, base `1e9466ac`)

> Landing record for one plan, **merged on the operator's decision of 2026-11-17 with the end path left open**: **delivered as specified, with an end path that cannot complete against the landed
> store and four unnamed defects**. The first reading (below) is of the open head `b99b45ce`; the section "Merge delta" records what the closing message and the merge changed. Written by `analyze` (inbox scan) from the lane's landing message
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

## Merge delta (closing message `rb-v2-31-supervisor-calls-and-pass-002.md`, 2026-11-17)

The closing message (`merge_state=merged`, `deliverables_done` 5 of 5, `cleanup_owed=false`, supersedes `-001`) reports #472 merged through the merge queue as
`3d453e6c` on the operator's decision of 2026-11-17 ("merge it as it is; the end-path blocker stays with the follow-up plan"). A second read-only leaf
(`execution-context-level-3`, `orchestrator.analyze`) read `git diff b99b45ce..2d5068bd` and the tree at `3d453e6c`; the orchestrator re-read
`SupervisionPass.run` and `finishAttempt` (`:88-99`, `:197-210`) and `WorkerRecycling.yieldIdleSlot` (`:186-205`), and the commit `9ae1b999`'s stat.

| Id | Point | Verdict | Severity |
|----|-------|---------|----------|
| M-1 | the delta of the plan's own files after `b99b45ce` is one commit, `9ae1b999` (`SupervisionPass.java` +31, `SupervisorCalls.java` +6); the two base merges touch no file of the plan | confirmed (stat re-read) | ok |
| M-2 | fix (a), `run()` in `try { … } finally { recycling.yieldIdleSlot(); }`: correct for the yield; **a throwing `yieldIdleSlot` replaces the in-flight exception**, which under B1 is the store refusal, so the cause is hidden; the yield can reach `recycle` and `ending.end` | confirmed by reading (`:92-98`, `WorkerRecycling.java:190-215`) | minor |
| M-3 | under B1 the ended generation stays non-terminal with its slot already released; the yield (now run after the throw) filters only on `WARM`, non-terminal, `WAITING`, no lease, so it may pick that dead generation and call `ending.end` on it | reading of the filter re-read; the `end` behaviour on an exited process is **not** checked | minor, unverified |
| M-4 | the `run()` Javadoc says "a single failing generation never starves a role that waits for a slot"; the generations after the throwing one are still unobserved on every pass | confirmed (Javadoc `:84-87` re-read) | minor |
| M-5 | fix (b), `finishAttempt` removes the pump after the finish (`:197-210`, re-read): it helps only when `pump.finish` throws something other than `IllegalStateException`; a throw after the append (in `forget`) leaves `finished` unset and the retry appends a second `attempt_end` | leaf reading of `WorkerActivityPump` | minor |
| M-6 | fix (c), `SupervisorCalls.lost()` through `known(...)` skips terminal and absent generations; also closes the `losses.put` leak after a forget (narrows N-9); its Javadoc does not say so | leaf reading | minor |
| M-7 | **no test** for any of the three fixes (no throwing-generation test, no terminal-generation `lost` test); the test, ADR and guide files are byte-identical between `b99b45ce` and `3d453e6c` | confirmed (empty diff by the leaf) | minor |
| M-8 | the guide's Known-blocker paragraph and ADR 014 `:116-124` still say "the throw escapes `run()`, so the idle yield never runs" (false after `9ae1b999`) and "this pull request is not ready to merge" (stale after the merge) | leaf reading | minor |
| M-9 | the pass's pump reads through `JobLog.readLines`, which #475 made redact every line; `nextOffset` is computed on the raw bytes, so a key block spanning pump windows stays unmasked (the residue of PLAN-V2-151 item 44(d)) | leaf reading | info |
| M-10 | B1 at the merged head is unchanged: `JobRecordStore.java:328-332` refuses a settled record, `WorkerEnding.exited` writes at `:150` after the side effects at `:143-149`, `SupervisionPass.advance` routes a settled record to `completeEnd` (`:113-115`); the pass is still unscheduled (repository-wide grep: no production caller of `run()` or constructor of `SupervisorCalls`) | confirmed | blocker (latent) |
| M-11 | N-1, N-2, N-3 (the yield now runs, the later generations are still aborted), N-4 to N-8, N-15 and T-1 to T-3 are unchanged; N-9 is narrowed; Doc-2 (Traceability text), Doc-3 (ADR dated 2026-11-16, records a 2026-11-17 decision) and Doc-5 (the guide's Decisions list stops at ADR 009) are still there | leaf reading | as before |
| M-12 | footprint 8 files, all in the plan's surface but `SupervisorEnvironment.java` (`StartGuard.firstCall`, the lane's admitted seam, `:266-276`); no pom, schema, native-image file, `LogRecord` or generic catch; ADR 014 is the next free number in `runtime-worker/` (001 to 013 at `4bc06ae3`) | confirmed | clean |
| M-13 | the lane's five open items hold (the `fresh` end recorded LOST, `harnessEnrolled` narrower than D1, the start-failure count owed, the owed cleanups, the `waitOpened` finally untested) | confirmed in substance | as the lane says |
| M-14 | omitted by the lane: M-2 to M-8, N-15 and M-9; its sentence "fail-capability shown by mutating the committed fix three times" refers to the D3 tests, not to the three `9ae1b999` fixes | confirmed | info |

Review and quality numbers of the closing message: CodeRabbit reviewed head `2ecac207` (after three quota refusals and one escalated trigger), 4 findings, 3 fixed in `9ae1b999`,
the blocker answered with the recorded reason, all threads resolved (`review-gate` `merge_allowed: true`, by the lane); Sourcery on a 7-day quota; Sonar 0 open, gate OK; CI green on `2d5068bd`.
`landing-check` complete=false for `total_tokens` and `plugin_version` (E27). Two base merges re-ran the gate on the merged tree. The lane saw one load-dependent timeout of
`DiagnosticCollectorRedactionTest` (PLAN-V2-151's flood test, passes in isolation).

### Residue after the merge

1. **The follow-up plan for the end path stays owed** (operator decision of 2026-11-17, `handle-open` item 1, proposal 1): B1 and N-3 (the terminal write that the store accepts and the retry guard), N-1,
   N-2, N-4, N-8. It is **not staged**; it waits for the operator's word "stage it", and is gated against the `runtime.job` plans, PLAN-V2-149 and PLAN-V2-150. M-2 and M-3 (the yield's masking and
   the dead generation) belong to its design of the end path.
2. **Carried into PLAN-V2-114, item 42**, whose condition ("only after #472 has merged") is now met: N-5, N-6, N-7, N-9, N-15, T-1 to T-3, Doc-2, Doc-3, Doc-5, `@Override` on
   `StartFailureGuard.firstCall`, and the points M-4 to M-8 (the Javadoc of `run()` and `lost()`, the three untested fixes, the stale ADR and guide paragraphs).
3. **Decision recorded:** the merge of #472 as it was (operator, 2026-11-17).
