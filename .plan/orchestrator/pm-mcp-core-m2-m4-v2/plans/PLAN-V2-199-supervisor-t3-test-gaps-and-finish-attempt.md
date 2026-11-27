# PLAN-V2-199: The supervision pass's four T-3 test gaps behind a fixture that settles a generation, and the finishAttempt throw-after-append point

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-V2-162 (#567) landing revision 25, section 9: items 42(f) (four T-3 test gaps) and 42(h) (`finishAttempt` throw-after-append)
lane: runbook

> Staged plan spec, one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off. SELF-SUFFICIENT together with the standing rules.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`). They bind this plan exactly as if they were written below. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R05, R06, R14, R16, R19, R20 (the drafter's candidates, confirmed by the orchestrator against the rules file at staging; an id the lane finds not applicable it says so).

## Objective

The tail pump before the exit, the `START_FAILURE` branch, the requested-end branch and the retried completion of `SupervisionPass.completeEnd` have no direct test. A START_FAILURE test was attempted and reverted: a writer left unsettled in the fixture holds its `close()` for 30 s. The decision comes from `LossDetection.exit` on a settled record, not from the fate write. This plan builds the fixture first, then the four tests with mutation-after controls, and settles the `finishAttempt` point: a throw after the journal append may append the attempt end twice on the retry.

## Deliverables

1. **The fixture first.** A generation settles the way the runner does (a settled record with an exit before the first call, through `SettlementStep.fate`). `close()` returns at once. A test of the fixture itself proves the generation is settled and not merely recorded (it asserts the settled state and that no wait runs for the bound).
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
2. **The four tests, each with a mutation-after control.**
   - The tail pump before the exit: lines written before the exit are appended before the attempt end.
   - The `START_FAILURE` branch: an exit before the first call ends with cause `START_FAILURE`, and the matched control is a worker that called first (not a start failure).
   - The requested-end branch: a requested cause beats a loss and a recorded reason.
   - The retried completion: a failed end retried keeps its cause and appends nothing twice.
   - Each test asserts the reason or cause, not only the type. Existing candidates on the branch (`anExitAfterTheFirstCallIsALoss`, `aFailedEndIsReportedAndItsRetryKeepsItsCause`, `aJobSettledAfterItsTaskClosedEndsTheClosedAttempt`) may already cover parts. The outline lists what is left and drops the rest with its reason.
   - Skills: pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: module-tester
3. **`finishAttempt` throw-after-append (42(h)).** Test first: the pump's journal append succeeds and a later step of `finish` throws, so the retry should not append a second attempt end. If the test fails, fix the order or make the append idempotent, then document it. If it passes, record why the point is closed.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core
   - Persona: implementer

4. **The statements and tests the PLAN-V2-162 landing analysis left (`landings/PLAN-V2-162.md` L3-5, L3-6, L3-7 and the conformance row).** (a) `PM_RUNTIME-152` and `PM_RUNTIME-153` are asserted by free-text substring (`WorkerRecyclingTest.java:298`; 153 is PLAN-V2-198's, deliverable 4c): assert 152 by its constant with `LogAsserts` (project rule). (b) `ProbeBudgetTest` uses `30L` where a constant exists; `ProbeBudget.override` and `clearOverrides` are public static mutators of process-global state: decide whether they become package-private (the production never calls them) and keep the test cleanup. (c) `JobLog.java:360`: `tailText.length() == cap` compares a char count with a byte cap; add the test with a multi-byte tail under the cap parameter and compare bytes, or record why a char count is the right unit. (d) The wall-clock bounds `SecretRedactorTest` `PEM_BOUND` (5 s) and `DiagnosticCollectorRedactionTest` `FLOOD_BOUND` (120 s): say in the test whether a timeout is the control (as the lane's flood test intends) and keep the bound large enough for a loaded host. No production behaviour changes except (b) visibility and (c) if the test fails.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer

**Done when:**
- Deliverables 1 to 4.
- Each of the four tests fails against a scratch mutation of the branch it names (the branch removed, or its decision inverted). The run is reported by log id. Production code is not changed for tests 1 and 2, except where deliverable 3 shows a defect.
- If deliverable 3 shows a defect, the test fails at the post-#567 head of main (sha recorded by the outline) and passes after the fix.
- The suites keep their test counts per class and stay green after the change.
- Build: `verify -Ppre-commit`, `verify` of `pm-runtime`, `verify -Pcoverage`, `verify -Pintegration-tests -pl pm-runtime -am`. Use per-module runs with log ids if a full run exceeds the host's bound.
- Sonar: the open-finding read at the head is `0` or each key is selected for dismissal by the operator (`unknown` for a failed read, never `0`).

## Standing Constraints

- Starts after PR #567 merged. Claims on the branch are HYPOTHESIS until re-read.
- Read every cited line at the head first and drop what a merge settled (name the commit).
- No new hand-written fake (CLAUDE.md, ADR 026): EasyMock doubles only.
- Test data come from the generators. A literal stays only where it is the contract and the test says so.
- A mutation is a scratch edit that is reverted; the tree is verified clean and the run id is reported.
- No dependency or plugin is added. No more than 100 files per PR. No behaviour change except deliverable 3's correction.
- Sonar dismissal only on the operator's selection, through `sonar_rest transition`.

## Claim Labels

- HYPOTHESIS: `completeEnd` pumps the tail before the monitor, `claimEnd` decides `START_FAILURE` from `detection.startFailure()` when no request exists, and `finishAttempt` calls `pump.finish` then removes the pump. Read on the branch (d5c8da5d). Confirm at `SupervisionPass.java` § `completeEnd`, `claimEnd`, `finishAttempt`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: SupervisionPass.java: completeEnd :222-247 calls pump(...) (tail read) at :229 before claimEnd/monitor; claimEnd :263-290 does ending.end(generation, EndCause.START_FAILURE) when requested == null && detection.startFailure() (:284-286); finishAttempt :336-349 calls pump.finish then active.remove.
- HYPOTHESIS: the 30 s hold comes from an unsettled writer in the fixture. Confirm at `SupervisorTestFixture.java` § `stateWriter()`, `WorkerEnding.java` § `exited` and `TerminationProtocol.GRACE_PERIOD`.
  - verdict: contradicted | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: yes | evidence: SupervisorTestFixture.stateWriter() (:482-496) is an anyTimes() EasyMock that only records writes and never blocks; WorkerEnding.exited (:189-250) writes state only if the settled record is empty and waits on nothing; TerminationProtocol.GRACE_PERIOD is 5 s (:56-59), not 30 s. The only 30 s in the area is HarnessStartSpacing.SPACING_BOUND (:45).
- HYPOTHESIS: a throw after the append in `WorkerActivityPump.finish` (after `recorder.append`, at `recorder.forget`) lets the retry append a second end. Confirm at `WorkerActivityPump.java` § `finish` and `ActivityRecorder.java`.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: WorkerActivityPump.finish (:141-157): recorder.append (:147) then unmapped=0, task=null, recorder.forget (:154); SupervisionPass.finishAttempt (:336-349) does not catch it, recordEnd keeps the claim unfinished, so the retry calls pump.finish again and appends a second end. Caveat: forget (ActivityRecorder.java:136) is an in-memory removeIf; a realistic throw is protocol.offTrack inside append.
- OBSERVED: `SupervisionPassTest`, `WorkerActivityPumpTest`, `SupervisorTestFixture` and `WorkerEndingTest` exist at 3ba79c4f.
  - verdict: corroborated | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: All four exist: pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisionPassTest.java, WorkerActivityPumpTest.java, SupervisorTestFixture.java, WorkerEndingTest.java (also ActivityRecorderTest.java).
- OBSERVED (drift): the lane's reverted attempt is reported only in the landing; the fixture's `stateWriter()` is an EasyMock writer (read on the branch).
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: The lane's reverted attempt is reported only in a landing file under .plan (not readable here). The readable half holds: SupervisorTestFixture.stateWriter() :482-496 is an EasyMock mock (createMock(WorkerStateWriter.class)).
- Verify-first clause: before writing a test, list the existing tests that already exercise the branch. A refutation loops back to re-scope.
  - verdict: unverifiable | checked_at: 115d5987ff625e790ef49df7d2bf2c4163c5870a | by: pm-mcp-core-m2-m4-v2/cleanup | rescoped: n/a | evidence: Process clause about what the lane does before writing a test; future-tense.

## Expected Surface

9 existing files.

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisionPass.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerActivityPump.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ActivityRecorder.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerEnding.java`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisionPassTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorTestFixture.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerActivityPumpTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/ActivityRecorderTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerEndingTest.java`
- HYPOTHESIS: `pm-runtime/doc/developer/runtime-worker.adoc` only if deliverable 3 changes a statement.
- OBSERVED (folded from the PLAN-V2-162 landing, `90505ad2`): `pm-runtime/src/main/java/de/planmarshall/runtime/job/ProbeBudget.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobLog.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerRecyclingTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/ProbeBudgetTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobLogTest.java`, `pm-core/src/test/java/de/planmarshall/core/queue/SecretRedactorTest.java`, `pm-core/src/test/java/de/planmarshall/core/build/DiagnosticCollectorRedactionTest.java`

## Dependencies and Sequencing

- Depends on: PR #567 merged. This plan starts after it.
- Overlaps with: PLAN-V2-150 (`WorkerActivityPump`, worker tests) and this series' S1 and S5 on `SupervisorTestFixture`. The gate decides.
- Adjacent to: PLAN-V2-165 (75(b)-(f), the offer-time window; untouched).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-199-supervisor-t3-test-gaps-and-finish-attempt.md
```

## Write-Boundary

The plan touches only its own repository source and tests. It creates and edits NO file under `.plan/orchestrator/` other than its own `inbox/{sender}-{seq}` message. See `persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Re-Ground Record of 2026-11-27 (cleanup, head 115d5987)

Binding, like every earlier record: where a claim above and this record disagree, the record is what is true at the head; the claim bullets are not rewritten. Read by read-only leaves (R21) from the head `origin/main` 115d5987; each contradicted claim is absorbed here (`rescoped: yes`).

- Claim 1: SupervisorTestFixture.stateWriter() (:482-496) is an anyTimes() EasyMock that only records writes and never blocks; WorkerEnding.exited (:189-250) writes state only if the settled record is empty and waits on nothing; TerminationProtocol.GRACE_PERIOD is 5 s (:56-59), not 30 s. The only 30 s in the area is HarnessStartSpacing.SPACING_BOUND (:45).
- **Verify first, before any fixture is written (binding):** the 30 s hold named in the Objective is NOT from an unsettled writer of the fixture at the head (claim 1 above). The only 30 s in the area is `HarnessStartSpacing.SPACING_BOUND` (`HarnessStartSpacing.java:45`), and PLAN-V2-162's follow-up #625 moved the spacing wait outside the monitor. The outline first re-runs the reverted START_FAILURE test at the head and reads where its time goes; if the hold is gone, the fixture of deliverable 1 shrinks to what the four tests need, and the landing says which part of the objective the head settled.
