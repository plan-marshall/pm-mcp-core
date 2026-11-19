# PLAN-V2-154: The end path of a supervised worker completes against the store: the terminal write at settlement, the retry guard, the detection of a silent worker

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: the landing of PLAN-V2-31 (`landings/PLAN-V2-31.md`, B1, N-1 to N-4, N-8, M-2, M-3, M-10; PR #472 merged as `3d453e6c`) and the operator's decisions of 2026-11-17 (`handle-open` items 1 and 5, answered 1 and 1; the word "stage it" of the same day)

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-154-supervisor-end-path-settlement.md` and is queued as one row file, `queue/PLAN-V2-154.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
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

The supervision pass of PLAN-V2-31 completes an end only for a settled job record, and the store refuses to write a worker block into a settled record (`JobRecordStore.java:328-332`), so the end path cannot complete in production; the pass is latent (nothing constructs `SupervisorCalls` or calls `SupervisionPass.run`) and must not be scheduled before this plan lands. The operator chose the design on 2026-11-17: **the runner's settlement writes the terminal worker block together with the fate, and the supervision pass then completes only the fence, the token revoke and the slot release.** The record stays final once settled, and the settled record stays the pass's one exit observable. The plan also closes the defects of the same path the landing found: the retry guard, the detection of a worker that never calls, the monitor held across the store write, the end classification of a `fresh` or `zero_tool` worker, the attempt attribution of a warm worker's log tail, and the mapping `LossReason.attemptEnd()` that has no caller.

## Deliverables

1. **The terminal worker write at settlement (test first; B1).** `SettlementStep` (`pm-runtime/.../job/SettlementStep.java`, `writeFate` at lines 204 to 207 at `origin/main`) writes the terminal worker block in the same transaction as the fate, so a settled record carries a terminal worker block and `JobRecordStore.writeWorker` is never asked to change a settled record. `WorkerEnding.exited` stops writing the worker state through `WorkerStateWriter.over` for a settled record: it fences, revokes the token and releases the slot, and reads the terminal state from the record. The store's refusal stays as it is (the operator chose against relaxing it).
   **Done when:** a test runs the real `WorkerStateWriter` and the real `JobRecordStore` (no recording mock) over a job that settles with a worker block and the pass ends it: the pass completes, the record is unchanged by the pass, the fence, the token revoke and the slot release each happened once; `SupervisorTestFixture` still has its recording writer for the other scenarios and a comment-free display name says which test uses the real one; `JobRecordStoreTest` still shows the refusal of a worker write into a settled record.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: implementer
2. **The retry guard of `WorkerEnding.exited` and one failure not aborting the pass (N-3, M-2, M-3).** `exited` runs `cancelGrace`, `requests.remove`, `fence`, `revoke` and `slots().release` before any write (`WorkerEnding.java:143-150` at `63e5e80a`); a retry therefore repeats them and re-derives the cause as LOST. The side effects run once per generation (the guard is the generation's own terminal mark, not a second structure), a store failure is reported as a failure of that generation and `SupervisionPass.run` goes on to the later generations; the idle yield (`WorkerRecycling.yieldIdleSlot`, `run()`'s `finally`) neither hides the in-flight exception nor picks a generation whose end already ran.
   **Done when:** a test makes the terminal write fail once for generation A of two: B is observed in the same pass, A is retried in the next pass with its cause intact (a DRAIN stays a DRAIN) and its slot released once; a throwing `yieldIdleSlot` does not replace the exception in flight; the Javadoc of `run()` says what it now guarantees.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: implementer
3. **The detection of a worker that never calls, and the monitor (N-1, N-4).** `LossDetection.started` arms "silent from spawn", but `advance` never creates the detection for a running generation, so a worker that hangs at launch is ended only by the runner's budget; `completeEnd` holds `synchronized(calls)` across `ending.exited`, which writes the store and can start a successor process, so the one timer thread stalls (`SupervisorCalls`). The detection is created for a running generation at its first `advance`; the end work moves out of the monitor (the generation is claimed under it, the work runs outside it, the result is recorded under it again).
   **Done when:** a test with a fake clock shows a silent worker lost at the deadline of "silent from spawn" with no call ever made; a test shows `lost(...)` dispatched while another thread is inside `ending.exited`; the lock order stays the one the guide names (the cycle of the landing's N-5 is not widened; N-5 itself is PLAN-V2-156 item 42).
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: implementer
4. **`LossReason.attemptEnd()` (Decisions 2026-11-17, item 5, folded in).** `LossReason.attemptEnd()` (`LossReason.java:58` at `63e5e80a`) has no caller; the pass keeps its own end-cause table (`SupervisionPass.java:210-218` at the head of #472). The plan makes `LossReason.attemptEnd()` the one mapping from a loss to the attempt end and uses it in the pass, or deletes it and keeps the table, and records which in an ADR (R06). It is not left as a method nobody calls.
   **Done when:** `git grep attemptEnd` finds either its callers in the pass or no definition; one table maps a loss to its attempt end; the ADR names the choice and the reason.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: implementer
5. **The end classification of a `fresh` and a `zero_tool` worker, and the log tail of a warm worker (N-8, N-2; verify first).** A `fresh` worker's normal end is recorded LOST because `EndCause.TASK_DONE` has no production caller, and `zero_tool` is not special-cased (confirmed in the landing); the plan makes a normal end of a task a task-done end and keeps LOST for a loss. N-2 (lines written before `taskClosed` appended to the next task of a warm worker, earlier attempts getting no end entry) is a leaf reading not re-read: read `WorkerActivityPump` and `ActivityRecorder` for it first, fix it only where it holds, and say in the landing which it was.
   **Done when:** a `fresh` worker that ends after its task is recorded as a task-done end with no loss; a `zero_tool` worker's end is the one the spec `model-work/04` states for it (read it first); a test with two tasks on one warm worker shows each attempt's log lines under its own attempt and an end entry for each, or the landing names N-2 as refuted with the file and line that refute it.
   - Skills: pm-dev-java:java-core, plan-marshall:ref-code-quality
   - Persona: implementer
6. **The documents (R05, R06, R10, R11).** The Known-blocker paragraph of `runtime-worker.adoc` and the lines of ADR `014` that say the throw escapes `run()` or that the pull request is not ready to merge are rewritten to what the code now does (an ADR is not edited for a decision that stands: add the new ADR and mark 014's paragraph superseded in the way the directory already does); `runtime-job.adoc` describes the settlement's terminal worker block; one ADR per design decision of Deliverables 1 to 4; the next free numbers renumbered against `origin/main`. `doc/implementation-watch/job-runtime.adoc` and `doc/Traceability.adoc` are updated for what is implemented now (the Traceability text of PLAN-V2-31 that the landing's Doc-2 says overstates is corrected where this plan's own rows touch it; Doc-3 and Doc-5 stay with PLAN-V2-156 item 42).
   **Done when:** the links check (R17) reports `BROKEN LINKS 0` or only links of the sibling's own deleted items; the guide, the ADRs and the code say the same about who writes the terminal block and what the pass completes (the pull request body lists the line of the code each statement matches); the pull request body lists **Owed to the milestone exit**: the scheduling of the pass in the assembly (`plan-marshall-mcp`) is not done here.
   - Skills: pm-documents:ref-asciidoc, pm-documents:ref-documentation
   - Persona: documenter

**Done when:** the six deliverables' tests exist and pass beside every existing test of `pm-runtime` and `pm-core`; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body). **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready.

**Trace starting set:** PM-WORK-7 and the worker supervision requirements of `doc/Traceability.adoc` (read the rows of PLAN-V2-31); Job Runtime § Settlement; Model Work § Worker Launch and § Task Protocol (loss, re-offer). Watch: Job Runtime; Model Work.

## Standing Constraints

- **The store's refusal stays.** `JobRecordStore.writeWorker` keeps refusing a settled record (`JobRecordStore.java:328-332`); the record is final once settled and the settled record is the pass's one exit observable (operator, 2026-11-17). No second exit observable, no relaxed refusal.
- **The pass is not scheduled by this plan** and the assembly must not schedule it before this plan ships; the scheduling is `plan-marshall-mcp`'s.
- **No generic catch** in production code (CLAUDE.md); the failure of one generation is a value or a typed exception the pass handles, not `catch (Exception)`.
- **Add no dependency and no plugin** (CLAUDE.md). Pre-1.0: no compatibility shim for the old order of writes.
- **The residue named for PLAN-V2-156 item 42 stays there** (N-5, N-6, N-7, N-9, N-15, T-1 to T-3, Doc-3, Doc-5, `@Override` on `StartFailureGuard.firstCall`, M-4 to M-8): this plan fixes M-2 and M-3 only because its design of the end path decides them.

## Claim Labels

All lines read at `origin/main` `63e5e80a` unless the claim says another sha.

- OBSERVED: the store refuses a worker write into a settled record — `pm-core/src/main/java/de/planmarshall/core/store/JobRecordStore.java` lines 328 to 332 (landing M-10, re-read at `3d453e6c`)
- OBSERVED: `WorkerEnding.exited` runs the side effects (`cancelGrace`, `requests.remove`, `fence`, `revoke`, `slots().release`) before the terminal write — `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerEnding.java` lines 143 to 150 (landing Layer 2, confirmed at `b99b45ce`; re-read at `3d453e6c` by M-10)
- OBSERVED: the pass completes an end only for a settled record, and an absent record returns silently — `SupervisionPass.java` lines 105 to 115 (`advance` routes a settled record to `completeEnd`)
- OBSERVED: the runner writes the fate at settlement — `pm-runtime/src/main/java/de/planmarshall/runtime/job/SettlementStep.java` lines 204 to 207 (`writeFate`)
- OBSERVED: the test fixture's writer is a recording mock, so no test runs `WorkerStateWriter.over` over a settled record — `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorTestFixture.java` lines 422 to 429
- OBSERVED: the pass is latent — a repository-wide search finds no production constructor of `SupervisorCalls` and no production call of `SupervisionPass.run` (the assembly `plan-marshall-mcp` is not inspected)
- OBSERVED: `LossReason.attemptEnd()` has a definition and Javadoc only — `git grep attemptEnd` at `origin/main` `58f53566` and at the head of #472
- HYPOTHESIS: the settlement can write the terminal worker block in its existing transaction without a new store member — confirm or refute at `SettlementStep.java`, `JobRecordStore.java` and `JobRecordCodec` § worker (verify-at-outline); if the worker block's terminal state needs a member the record does not carry, that is a format change the plan reports, not decides (R13: version 1 in place is allowed only as the rule says)
- HYPOTHESIS: N-2 (the log tail of a warm worker is attributed to its next task) holds — confirm or refute at `WorkerActivityPump.java` and `ActivityRecorder.java` (verify-at-outline; the landing's reading was a leaf's, not re-read)
- HYPOTHESIS: a double `ModelSlots.release` is harmful, so the retry guard must prevent it — confirm or refute at `ModelSlots` (verify-at-outline; unverified in the landing)
- Verify-first clause: before editing, list the open pull requests for one that touches `pm-runtime/src/main/java/de/planmarshall/runtime/worker/` or `.../job/SettlementStep.java` (PLAN-V2-59, 96, 149 and 150 are the plans of this epic that edit `runtime.job` or the worker package); re-read the cited lines at `origin/main`; a refutation of B1 (a settled-record write the store now accepts) loops back to re-scope.

## Expected Surface

Code (`pm-runtime`):

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/SettlementStep.java` — Deliverable 1, the terminal block with the fate
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerEnding.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisionPass.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorCalls.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerRecycling.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/LossReason.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/EndCause.java` — Deliverables 1 to 5
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/LossDetection.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorEnvironment.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerActivityPump.java` — Deliverables 3 and 5 (swept at the outline; edited only where the reading holds)
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerEndingTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisionPassTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorCallsTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerRecyclingTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerLossDetectionTest.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorTestFixture.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/JobSettlementTest.java` — the proofs
- Read, not edited (the operator chose against relaxing its refusal): the job record store of pm-core, class JobRecordStore.

Documents:

- OBSERVED: `doc/implementation-watch/job-runtime.adoc`
- Not surface (R19, shared bookkeeping): the Traceability rows of PLAN-V2-31, the developer guides runtime-worker and runtime-job of pm-runtime, the ADR directory runtime-worker of pm-runtime, the log message listing, the plan index. No build file.
- NOT touched (named so that nobody adds them): the egress proxy, the confinement composer classes of the job package, the activity recorder unless N-2 holds, the schemas of pm-core.

- OBSERVED (cleanup 2026-11-19, understated surface; the files the Deliverables name and the surface did not): `pm-core/src/main/java/de/planmarshall/core/queue/QueueTaskRecord.java`, `pm-core/src/test/java/de/planmarshall/core/store/JobRecordStoreTest.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/launch/ModelSlotScheduler.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/worker/ModelSlots.java`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-31 (shipped, #472 `3d453e6c`). The assembly must not schedule the pass before this plan ships.
- **Overlaps with live plans (read from the Expected Surfaces on 2026-11-17, re-read at the emit by `corpus cross-check`):** PLAN-V2-149 (`confinement-records-and-checks`: `pm-runtime/.../job/` records and checks; its D1 to D3 touch `JobRecord`, `JobRecordCodec` and `WorkerEgressProxy`, none of this plan's files by name; the gate decides), PLAN-V2-150 (`worker-activity-observation-and-signals`: `ActivityRecorder`, the `worker/` package and `SupervisorTestFixture`; shares `SupervisorTestFixture.java` with this plan, and `WorkerActivityPump`/`ActivityRecorder` if N-2 holds), PLAN-V2-96 (`role-qualification-adapter`, running: `pm-runtime/.../worker/` new files), PLAN-V2-59 (`egress-record`, running: `SubprocessJobRunner`, `JobRecord`; not `SettlementStep`). **Sequence:** after PLAN-V2-150 where they share `SupervisorTestFixture`; the gate's verdict at the emit decides, and a multi-file overlap is the operator's.
- Adjacent to: PLAN-V2-156 item 42 (the minor residue of PLAN-V2-31), old PLAN-27 (predecessor epic) (`WorkerEnding`, `EndCause` in its text: read its outline for the report), `plan-marshall-mcp` (schedules the pass; stays untouched).

## Re-ground record (cleanup 2026-11-19 at `e2ae8627`, BINDING over any contradicting older text of this spec)

Written by `pm-mcp-core-m2-m4-v2/cleanup` from the verdicts of a read-only leaf over this spec (its statements are not re-read here; the executing plan re-reads each at its outline). Since the first pass (`d506e818`) #484, #490, #494, #495, #496, #503, #504, #506, #511 and #512 merged. Where this section and an older text of this spec differ, this section is the later reading.

- The claim list of this spec is not parsed by the corpus; the read-only re-ground of 2026-11-19 is recorded here.
- D4 is partly done: `LossReason.attemptEnd()` has a caller (`SupervisionPass.java:224`); only the decision ADR remains. D3: a double release is idempotent (`ModelSlotScheduler.release` `:260-274` filters by `jobId`), so the claim reads as idempotence. ADRs 015 (#490) and 016 (#506) exist: D6 numbers from 017. #506 changed `SupervisorCalls`, `WorkerRecycling`, `WorkerCalls`, `WorkerLifecycle`, `WorkerProtocol`: re-read the D2 and D3 line cites. Open: silent-from-spawn detection (no `calls.detection` for a running generation in `advance`), `TASK_DONE` has no production caller. "Adjacent to PLAN-V2-27" is old PLAN-27 (confinement, #161).
- Understated: `ModelSlots`, `ModelSlotScheduler`, `JobRecordStoreTest`, `QueueTaskRecord.AttemptEnd`.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-154-supervisor-end-path-settlement.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
