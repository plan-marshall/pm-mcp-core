# PLAN-V2-165: The closeout of the supervisor's end path: the persisted end reason, the missing tests, the windows the end path opened and the documents it made false

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: the three-layer analysis of the landing of PLAN-V2-154 (`landings/PLAN-V2-154.md`, 2026-11-20, PR #518 merged as `45a0d33c`) and the operator's rule that leftovers go into a staged plan
lane: plan-marshall

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-165-supervisor-end-path-closeout.md` and is queued as one row file, `queue/PLAN-V2-165.json`,
> in the epic ledger (lane `plan-marshall`: concurrency fixes and a decision, run with the plan lifecycle). The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R10, R11, R12, R14, R16, R17, R18, R19, R20. (R01: no work package file is closed by this plan; `doc/plans/worker-activity.adoc` and `job-confinement-and-environment.adoc` are edited only where Deliverable 6 says. R03, R04, R09, R13, R15 do not apply: no HTTP, no schema format change, no `pm-workflow` code.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-worker.adoc` (the Known-blocker paragraph is rewritten, see Deliverable 6) and `pm-runtime/doc/developer/runtime-job.adoc` (the settlement); ADR directory: `pm-runtime/doc/adr/runtime-worker/` (014 is the last at `origin/main` `63e5e80a`; renumber against `origin/main`, R11); layout: workstream WS-06.
- R12 — log message holder: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`), only if Deliverable 2 logs the refused terminal write; each record is documented in `doc/LogMessages.adoc` (generated from the holders, R12).

## Objective

PLAN-V2-154 (#518) settles the terminal worker block at settlement, adds the retry guard, detects a worker that never calls and classifies the end by the recorded reason; the pass stays latent (the assembly's scheduling is owed to the milestone exit). Its landing analysis found what is left: the persisted end reason is coarser than the timeouts specification, tests the plan's own Done-when asks for, windows the end path opened or left (a spurious cancel, an overwritten snapshot, an exception that stops later generations), four documents the change made false, and four follow-ups the lane named and no plan owns. This plan closes the end path.

## Deliverables

All file and line references are those of `45a0d33c` (the merge of #518) and are re-read at the head of `main` before editing. Carried items 42(a) to (h) of PLAN-V2-114 stay with PLAN-V2-162 Deliverable 3 (`runtime-worker-and-job-residue`); the parts #518 closed (the Traceability wording, the `run()` Javadoc, the ADR 014 blocker text, the yield-masking decision) are stale there and are read at the head.

1. **The persisted end reason (decision with the operator, R07).** The record stores the reason of a supervisor-requested end as the fate gives it (`WorkerTermination`, `WorkerEnding.java:196-202`, `:362`), so a silence-lost worker records `drain` and the timeouts specification (`timeouts/01:302`, `:310`) says `silence` and `ack_missed`: ask the operator whether the request's reason (`idle_recycle`, `silence`, `ack_missed`, `off_track`) reaches the terminal block or the specification is amended to the coarse mapping; build the answer with a test per reason and state it in one ADR (R06).

2. **The tests PLAN-V2-154's Done-when and CLAUDE.md ask for.** A test that makes `yieldIdleSlot` throw and shows the exception in flight is not replaced, `LogAsserts` for `PM_RUNTIME-149` and `150`, a test that settles a job after its task closed while the pump still holds a tail (the F2 fix `28804e96` has no test: removing its `else closeAttempt` at `SupervisionPass.java:229-232` probably leaves everything green), a per-effect resume test of `WorkerEnding` (the second effect fails and the first is not repeated), a test that chains the runner's settlement into the pass (`SettlementStep.fate`, not the test helper), the `StoreRefusal` branch of `withTerminalWorker`, a pass-level `zero_tool` test, and `REFUSED` in the "every fate" test (excluded today although the code maps it).

3. **The windows and races the end path left or opened.** `WorkerRecycling` picks a generation whose end already ran (the spec's "nor picks a generation whose end already ran" is not delivered); `recordEnd`'s unconditional `supervisor.replaced(terminal)` can overwrite a concurrent `taskClosed` or `turnCompleted` (it widens carried item 42(b) of PLAN-V2-162: coordinate, one fix); the request removed before the replacement (F6); a retry re-runs `successor.run()` and logs `WORKER_GENERATION_ENDED` twice and the successor is not under the per-effect guard; `calls.detection` throws `NullPointerException` from `Objects.requireNonNull(roleClasses.apply(role))` (`SupervisorCalls.java:280`) and `advanceReported` does not catch it, so one generation aborts the later ones; whether a never-connected `KILLED`, `CANCELLED` or `REFUSED` worker counts into the start-failure guard (`WorkerTermination.java:52`, read with PLAN-V2-162 Deliverable 1).

4. **The documents #518 left false, and the report's own errors.** `SupervisorCalls`'s Javadoc on the monitor (the first claim above), `WorkerStateWriter` called the "terminal-state writer" (`runtime-worker.adoc` `:465`, `:514`, `SupervisorEnvironment.java:165-175`, the `SupervisionPass` Javadoc `:40-41`, ADR 014:64), the specification's write-order sentence (the block is added, but the token revoke and fence are still placed with the fate while the pass performs them later), "the end is logged once" (`WorkerEnding.java:45`); the fate step's report string "worker block terminal, token and fence not implemented" that reads as if the block were unimplemented; the code-formatting glitch at `SupervisionPass.java:340`; `attemptEndOf` (`:361-371`) re-maps three loss reasons by hand while ADR 020 says "no second loss table"; the stale line cites of the PR body are not edited (history).

5. **The follow-ups the lane named, each adopted, dropped or owned.** The offer-time task boundary in the activity pump (a pump in `SupervisorCalls` or a boundary marker in the job log), per-line source offsets from `JobLog` for an idempotent tail replay (the pump's `appendLines` sets its offset after the loop, so a mid-batch failure re-appends), a pump that outlives its generation for a retryable tail of a settled worker, and the recorded budget against the exit loss's attempt end (`anExitAfterTheFirstCallIsALoss`, answered "pre-existing contract", not decided): per follow-up one line (adopt into a deliverable, drop with the reason, or name the staged plan that owns it).

**Done when:** Deliverables 1 to 5; the recorded end reason is what the answer to Deliverable 1 says and the specification states the same, a throwing idle yield is shown not to replace the exception in flight, no document of the supervisor's end path says what the code no longer does, and every follow-up has its recorded outcome. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-runtime` and `pm-core` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module runs); `verify -Pintegration-tests -pl pm-runtime -am`; the links check (R17) reports `BROKEN LINKS 0` and `INLINE SCHEMA BLOCKS 0` run from the main checkout.

## Standing Constraints

- **Read every cited line at the head first.** The lines and counts of a carried item are those of the landing analysis it names, read on an older head; re-read each at `origin/main` before editing and drop what a merge since then settled (name the commit).
- **Work to the Done-when.** Do not stop to ask the operator which part to continue with; ask only a decision the spec marks ask-at-refine (R07) or one a carried item says needs the operator, and put it in the outline's question, not in a stop.
- **Every carried item gets an outcome** in the landing message and the pull request: adopted into a deliverable, split off to a NAMED existing plan, or dropped, each with its reason; "not in this run's surface" is no reason.
- **No behaviour change beyond the corrections the items state.** A change a carried item does not ask for is ask-at-refine (R07).
- **Where the project analyses its pull requests with Sonar** (the plan lifecycle's Sonar step reports it configured; where it cannot scan, read the pull request over plain HTTP: `pullRequest=N`, never `branch=<feature>`): an open Sonar issue is fixed in a pushed commit, or dismissed with a stated reason; the quality gate is green before the pull request is reported ready.
- **No dependency or plugin is added.**
- **No more than 100 files per pull request** (the watch of the epic ledger; CodeRabbit refuses a larger one): cut the plan into pull requests by deliverable or by module before the first one is opened.
- **Tests that can fail.** A new test fails on the code it guards (a failing run or a mutation, reported by log id); a test of a refusal asserts the reason, not only the type (CLAUDE.md, R20).

## Claim Labels

- OBSERVED: `SupervisorCalls.java` (lines 42 to 44 at `45a0d33c`) still says the supervision pass takes the instance's monitor while it completes an end, so a transport call and the terminal write never interleave; after #518 the pass completes the end outside the monitor and `recordEnd` re-takes it for the replacement only.
- OBSERVED: no test of `SupervisionPass` makes `yieldIdleSlot` throw (the only tests of a yield are `WorkerRecyclingTest`'s, of the recycling method), although the Done-when of Deliverable 2 of PLAN-V2-154 asks that a throwing yield does not replace the exception in flight; the log records `PM_RUNTIME-149` and `150` are defined (`RuntimeLogMessages.java:1242`) and asserted by no test (CLAUDE.md: every `LogRecord` a class logs is asserted by its constant with `LogAsserts`).
- HYPOTHESIS: the persisted `end_reason` of a supervisor-requested end is derived from the fate, so a silence-lost worker that is cancelled and settles `KILLED` records `drain` where `timeouts/01:302` and `:310` say `silence` and `ack_missed` — read by the analysis leaf (`WorkerTermination`, `WorkerEnding`), not run; the operator's design of 2026-11-17 may accept the coarse mapping, which Deliverable 1 asks.
- HYPOTHESIS: `WorkerEnding` removes the generation's request before `recordEnd` replaces the generation, so a `planPaused` or `forceEnd` in that window installs a request for an ended generation (a spurious cancel and a leaked map entry); and `WorkerRecycling.yieldIdleSlot` can still pick a waiting generation whose end is requested or failed — read, not run; the tests of Deliverable 3 settle them.

## Expected Surface

Source, tests, specification and documentation (specific files, R19):

- OBSERVED (the supervisor's end path and its documents; the analysis leaf read them at `45a0d33c`): `doc/log-messages/pm-runtime.adoc`, `doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc`, `doc/specification/model-work/03-supervision-and-recycling.adoc`, `doc/specification/timeouts/01-job-budgets-and-waits.adoc`, `pm-runtime/doc/developer/runtime-worker.adoc`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/SettlementStep.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/WorkerTermination.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/log/RuntimeLogMessages.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ActivityRecorder.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisionPass.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorCalls.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorEnvironment.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerActivityPump.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerEnding.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerRecycling.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerSupervisor.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobSettlementTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/WorkerTerminationTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisionPassTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorCallsTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerActivityPumpTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerEndingTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerRecyclingTest.java`; the ADRs of `pm-runtime/doc/adr/runtime-worker/` (017 to 020, and the next free number, R11).

## Dependencies and Sequencing

- Depends on: PLAN-V2-154 (shipped, #518, `landings/PLAN-V2-154.md`: the plan this one closes), PLAN-V2-47 (shipped, #506), PLAN-V2-31 (shipped, #472), PLAN-V2-83.
- Overlaps with (class level): PLAN-V2-162 (`runtime-worker-and-job-residue`: carried item 42 and the start-failure wiring on the same supervisor classes: emit one after the other, 165 first, or read the other's tree), PLAN-V2-146 to 150 (the confinement and worker chain); the gate decides at emit.
- Not here: the scheduling of the pass in `plan-marshall-mcp` (owed to the milestone exit).

## Hand-Off Command

```text
/plan-marshall task="implement .plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-165-supervisor-end-path-closeout.md"
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
