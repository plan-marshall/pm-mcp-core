# PLAN-V2-47: A hold ends at the token budget, and the supervisor's half of the continuation

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: PLAN-115 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall` hand-off.
> Lives at `plans/PLAN-V2-47-continuation-hold-and-budget.md` and is queued as one row file, `queue/PLAN-V2-47.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r4`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R14, R16, R17, R18, R19, R20. (R12 applies only if the trace matrix shows a message to add, see Standing Constraints; R14 and R16 apply because the plan changes `pm-runtime`, and R16 (2) in the form that `WorkerProtocol` is implemented by the assembly; R15 does not apply, no `pm-workflow` code changes; R17 because the plan edits a package file and runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-worker.adoc` (extended); ADR directory: `pm-runtime/doc/adr/runtime-worker/`; layout: workstream WS-04.
- R12 — log message holder: `RuntimeLogMessages` (`de.planmarshall.runtime.log`, prefix `PM_RUNTIME`).

## Objective

Build the supervisor's half of the part of `question-continuation` that PLAN-73 left: a worker that reaches its token budget while it holds its task for an answer ends the hold. Model Work § Task Questions (*Budget*, restored by the operator's answer "Restore it in Model Work 06" and merged with #201) states the rule: the task is suspended where it may resume, else it falls back, and the worker is recycled. `WorkerRecycling.afterTurnCompleted` recycles a `HOLDING` generation at the budget and does nothing else; no notification tells the task protocol that the hold has ended, so the task would stay `awaiting_answer` under a worker that is gone. The plan adds the notification across the module boundary (the task protocol is `pm-workflow`, which `pm-runtime` may not reference), the entries by which the transport tells the supervisor that a hold opened and closed and that an answer was delivered (ADR `runtime-worker/012` owes the last), and the lifecycle of a holding generation behind them. It stops at the boundary: what the task protocol does with the notification (suspend or fall back) is the assembly's adapter over `TaskProtocol`, which already holds `suspend` and `fallback`.

## Deliverables

1. **The entries of the inbound port and the lifecycle of a hold.** `WorkerCalls` gains `holdOpened(generationId, queueTaskId, at)`, `holdClosed(generationId, queueTaskId, at)` and `answerDelivered(generationId, at)`; `SupervisorCalls` (the class of the plan this one follows) implements them: a hold moves the generation to `WorkerLifecycle.HOLDING` through the transition table (a transition the table does not have is refused, as everywhere), keeps its lease and its model slot, counts no idle wake-up, and suppresses the silence deadline as a consultation's wait does (`LossDetection.waitOpened`/`waitClosed`); `answerDelivered` calls `LossDetection.answerDelivered(at)` (the deadline of the answer's acknowledgement, ADR `runtime-worker/012`); the hold's time is not charged to the worker budget (the generation's budget accounting reads the hold's instants; the trace matrix says where the budget is measured and the plan records the reading).
2. **The notification `WorkerProtocol.holdEnded`.** A new method with a record of the generation, the queue task, the attempt, the cause (`token_budget`; the closed list of the record is this plan's, one value) and the instant. `WorkerRecycling.afterTurnCompleted` sends it for a `HOLDING` generation that reached its budget, once per generation, before it recycles through `WorkerEnding`, so the protocol sees the hold end while the lease is still held and before the fence; a generation that is not `HOLDING`, or is under its budget, sends nothing, and the existing rules (the context of the latest turn only, a generation that closed a task or holds is the only one recycled) stay as they are.
3. **The tests, deterministic** (JUnit 5, `@DisplayName`, AAA, `VirtualClock`): (a) `WorkerRecyclingTest`: a holding generation at the budget sends one `holdEnded` and then recycles with `RecycleCause.TOKEN_BUDGET`, the recording protocol shows the order `holdEnded`, fence, release; a holding generation one token under the budget sends nothing; an executing or waiting generation at the budget sends nothing; the notification is sent once when the turn is reported twice; (b) `SupervisorCallsTest`: each new entry, the refused transition, the unknown generation ignored, the answer's deadline fires `ACK_MISSED` at its deadline and not before for a continuing worker and an acknowledging worker is the control; (c) every existing `WorkerProtocol` test double implements the new method (six classes, listed in the surface).
4. **Document the decisions.** One ADR in `pm-runtime/doc/adr/runtime-worker/` (next free number) records: the notification is sent before the recycle; the cause list has one value; the runtime does not decide suspend or fall back (alternatives: pass the role's `questions` and the harness's resumability into the runtime and decide here, which duplicates `QuestionContinuation` of `pm-workflow` and breaks the module rule; recycle first and let the protocol find the hold ended by the fence, which loses the hold's cause). Extend `pm-runtime/doc/developer/runtime-worker.adoc` (the hold, the entries, the notification), update the "Not built" sentence of `doc/plans/question-continuation.adoc` to what exists after this plan, and the `doc/Traceability.adoc` lines of `PM-WORK-2` and `PM-WORK-3` that name `WorkerCalls` and `WorkerProtocol`.
5. **State the owed rest, in the pull request body, and keep the package open.** Not built here, with the owner: the assembly's `WorkerProtocol.holdEnded` (it calls `TaskProtocol.suspend` where `QuestionContinuation` allows the resume and `fallback` otherwise) and its calls of the three new entries (`pm-mcp-server`, `plan-marshall-mcp`: a named follow-up after the SNAPSHOT deploy, because an abstract method added to an interface the assembly implements breaks its compile until it follows — pre-1.0, no default method that would hide the gap); the resumed generation's start from the stored session (the `WorkerStartRequest.resumeSessionId` and the composer's `resume.argv` exist, the call is the transport's); the restart sweep that keeps a `suspended` task suspended and ends a hold (a recovery step beside `JobRecoveryStep`, with the queue store); the engine's `<state>.asked` and the envelope's `ask:TASK-NNN` offer wiring and the `facts.answers` producer (`pm-workflow` and the assembly). The package `question-continuation` stays `open` (R01 is not applied); no specification section is deleted.

**Done when:** a worker that reaches its token budget while holding sends `holdEnded` exactly once, before its recycle, and one that does not hold sends none; the three entries exist and are implemented; the answer's acknowledgement deadline is armed by `answerDelivered`; every `WorkerProtocol` double compiles against the new method; the ADR, the guide, the package file sentence and the Traceability lines are written; the owed rest is listed in the pull request body. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-runtime` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:** PM-WORK-2 (the continuation of a task after its question), PM-WORK-3; `doc/specification/model-work/06-envelopes-questions-deadlines.adoc` § Continuing after a Question (*Order*, *Budget*, *Answer acknowledgement*, *Fencing and restarts*); `doc/specification/model-work/03-supervision-and-recycling.adoc` § Recycling; `doc/specification/timeouts/01-job-budgets-and-waits.adoc` § Worker Budget; ADRs `pm-runtime/doc/adr/runtime-worker/009`, `012`; the package file `doc/plans/question-continuation.adoc` and the landing of #201.

## Standing Constraints

Plan-specific constraints (the epic-wide rules are in `standing-rules.md`, named in the First Instruction above):

- **Log messages: none expected.** `WorkerRecycling` already logs `WORKER_GENERATION_RECYCLED` with the cause. If the trace matrix shows a new record, it goes into `RuntimeLogMessages` at the next free identifier of its level, and the plan says so in its inbox message.
- **The cause list of the notification is closed and has one value.** A cause for the fallback reason is not added to `QueueTaskRecord.FallbackReason`: the closed list of `fallback_reason` (`not_allowed`, `hold_expired`, `resume_expired`, `session_missing`, `configuration_changed`, `harness_changed`, `worktree_moved`, `resume_unacknowledged`) is a store schema (`store-schemas/16-task-queue.adoc`) that a parity test reads (R10). The reason a budget fall-back records is the adapter's choice; this plan's ADR states the reading (`not_allowed`: the suspension was not allowed), and a plan that finds the list too small asks the operator at refine (R07) instead of extending it. **Decided 2026-10-29 (operator walk, decision 9 of 18; the orchestrator was above 95% sure): `not_allowed`.** The specification has two branches at the budget ("suspended where it may resume, else it falls back"); the fall-back branch is exactly the one where the suspension is not allowed, so no value is added.

## Claim Labels

- OBSERVED: `WorkerRecycling.afterTurnCompleted` recycles a `HOLDING` generation at the budget and ends nothing else: the `HOLDING` exceptions at `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerRecycling.java:143-148` and `return recycle(generation, RecycleCause.TOKEN_BUDGET)` at line 149; `WorkerRecyclingTest` asserts only the cause for a holding generation (`WorkerRecyclingTest.java:172-175`)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: files read at a8da4104
- OBSERVED: `WorkerProtocol` has four methods (`fence`, `agentStarted`, `offTrack`, `closeUnavailable`) and none for a hold — `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerProtocol.java`; `WorkerCalls` has six (`callHeartbeat`, `waitOpened`, `turnCompleted`, `taskClosed`, `planPaused`, `harnessEnrolled`) and none for a hold or an answer — `WorkerCalls.java:25-80`; ADR `runtime-worker/012` lines 29-34 says the entry is owed
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: files read at a8da4104
- OBSERVED: nothing in `src/main` sets `WorkerLifecycle.HOLDING` (the only lifecycle set in production is `STARTING` at registration, `WorkerSupervisor.java` `register`); the transition table has it: `WorkerLifecycle.java:70-71`
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: grep HOLDING over pm-runtime/src/main at a8da4104
- OBSERVED: the rule is in the specification — `doc/specification/model-work/06-envelopes-questions-deadlines.adoc:427` (*Budget*), restored by #201 (landing `landings/PLAN-73.md`, D7: "specification restored; behaviour not built")
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- OBSERVED: the task protocol side exists in `pm-workflow`: `TaskProtocol.hold` (line 462), `answer` (485/500), `acknowledgeAnswer` (564), `fallback` (597/619) and `suspend` (670) — `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java`; `LossDetection.answerDelivered` exists (`LossDetection.java:135`)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: files read at a8da4104
- OBSERVED: six test classes implement `WorkerProtocol`: `SupervisorTestFixture.RecordingProtocol` (line 339), `WorkerActivityPumpTest.NoSignals` (75), `OffTrackScenarioTest.RecordingProtocol` (321), `HarnessStartSpacingTest.SilentProtocol` (121), `ActivityRecorderTest.RecordingProtocol` (120), `StartFailureGuardTest.RecordingProtocol` (306)
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: grep "implements WorkerProtocol" at a8da4104
- OBSERVED: the closed list of `fallback_reason` has eight values and none names the budget — `doc/specification/store-schemas/16-task-queue.adoc:31` and the schema line 80; `QueueTaskRecord.FallbackReason`
  - verdict: corroborated | checked_at: a8da4104e3efad83b2ca9c1b28ec724ad402fd8a | by: pm-mcp-core-m2-m4/spec-draft(runtime-worker) | rescoped: n/a | evidence: file read at a8da4104
- HYPOTHESIS: the hold's time is charged to the worker budget today only if the budget accounting counts the hold's wall time — where the worker budget is measured for a warm generation was not read (the spec says the hold time does not count toward the worker budget, Model Work § Hold) — confirm/refute at the trace matrix (verify-at-outline) and record the reading in the ADR; if the accounting lives in `pm-workflow` or the assembly, this plan names it as owed instead of changing it
- HYPOTHESIS: the plan that precedes this one (PLAN-V2-31 (`supervisor-calls-and-pass`)) has landed and `SupervisorCalls` exists; if it has not, this plan does not start (see Dependencies)
- Verify-first clause: before editing, re-read `WorkerRecycling`, `WorkerCalls` and `WorkerProtocol` at the head of `main`, list the open pull requests for one that names them (`runtime-control` and `epic-resource-and-release` are staged and declare neighbours), and confirm that no implementation of `WorkerProtocol` exists in the assembly that this plan would break unannounced; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerCalls.java` — three entries added
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerProtocol.java` — `holdEnded` and its record
- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/WorkerRecycling.java` — `afterTurnCompleted` sends the notification before the recycle
- HYPOTHESIS: `pm-runtime/src/main/java/de/planmarshall/runtime/worker/SupervisorCalls.java` — the class of the preceding plan (new there), the three entries implemented
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerRecyclingTest.java` — the order and the negative cases
- HYPOTHESIS: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorCallsTest.java` — the class of the preceding plan, the entries
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorTestFixture.java` — `RecordingProtocol` records `holdEnded`
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/WorkerActivityPumpTest.java` — `NoSignals` implements the method
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/OffTrackScenarioTest.java` — `RecordingProtocol` implements the method
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/HarnessStartSpacingTest.java` — `SilentProtocol` implements the method
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/ActivityRecorderTest.java` — `RecordingProtocol` implements the method
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/worker/StartFailureGuardTest.java` — `RecordingProtocol` implements the method
- OBSERVED: `doc/plans/question-continuation.adoc` — the package file, the "Not built" sentence updated (the package stays open)
- OBSERVED: `pm-runtime/doc/developer/runtime-worker.adoc` — extended (shared bookkeeping, R19)
- HYPOTHESIS: `pm-runtime/doc/adr/runtime-worker/` — one ADR, the next free number (shared bookkeeping)

## Dependencies and Sequencing

- Depends on: PLAN-V2-83 (`skills-conformance-foundation`: the dependencies and rule R20); and then: the plan PLAN-V2-31 (`supervisor-calls-and-pass`) of this cluster (it creates `SupervisorCalls` and `SupervisorCallsTest` and extends `SupervisorTestFixture`; this plan extends all three). PLAN-73 (#201) and PLAN-30 (#190) are shipped.
- Overlaps with, by file: PLAN-V2-31 (`supervisor-calls-and-pass`) (3 entries, the reason for the dependency). PLAN-V2-73 (`runtime-control`, staged) declares `WorkerDrainParticipant` and `WorkerSupervisor`, PLAN-V2-27 (`epic-resource-and-release`, staged) declares `WorkerEnding`, `WorkerEndingTest` and `EndCause`: this plan declares none of them (0 shared entries); PLAN-V2-23 (`decision-engine`) none; `scenarios-workflow` cluster / PLAN-35: `OffTrackScenarioTest` is edited here by one nested class only (1 shared entry if that cluster edits it again; the landed #229 is its last change). PLAN-46, PLAN-105, PLAN-V2-39 and PLAN-V2-43: no shared file.
- Adjacent to: `doc/plans/question-continuation.adoc` depends on `client-profile-measured-values` (waiting for `plan-marshall-mcp`'s follow-up): that dependency is the package's, not this plan's, since the plan changes no profile value. The Quarkus assembly `pm-mcp-server` in `plan-marshall-mcp` stays untouched and owes the follow-up named in Deliverable 5.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-47-continuation-hold-and-budget.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.

## Carried Open Items

Open items of the previous epic that this plan owns (register: `owner-register.md` of the epic; the full text is in the archived ledger `pm-mcp-core-m2-m4/epic.md`). They are not deliverables and extend no Done-when: read each while outlining, re-check it against `origin/main` (Claim Labels) and either adopt it into a deliverable of the outline or say in the landing why not.

- **#190 deleted the specification sentence that PLAN-73's Deliverable 7 depends on (found by the c** (Op47) — #190 deleted Model Work 03 § Recycling Warm Workers incl. "a worker holding its task for an answer that reaches the budget ends its hold"; D7 depends on that sentence; WorkerRecycling recycles a HOLDING generation at the budget without ending the hold
- **The question-loop verification of plan-marshall-mcp is done (question-loop-verification in its** (Op49) — the question-loop verification of plan-marshall-mcp is done: this plan has no open cross-repository dependency left; #197 wrote the resumability candidates and max_hold_seconds / max_age_seconds 3600 into Client Profiles 01/02 and Model Work 01/06
