# PLAN-V2-49: Worker faults in the scenario harness: the fault directives, the fault matrix and a runtime crash with a leased task

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
origin: PLAN-123 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-49-scenario-worker-faults.md` and is queued as one row file, `queue/PLAN-V2-49.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the second of two plans that finish the part of the work package `supervision-scenarios` that belongs to this repository
> (PLAN-35 shipped D2, D3, D4 as #229, `a8da4104`; D1 is its residue). It starts on the merge of PLAN-V2-33 (`scenario-task-protocol`).

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R17, R18, R19. (R12 applies only if the trace matrix shows a message to add; R14 and R16 do not apply: test scope of `pm-workflow` only, `pm-runtime` and `core.store` are read, not changed; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-workflow/doc/developer/workflow-scenario.adoc`; ADR directory: `pm-workflow/doc/adr/workflow-scenario/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); none expected.

## Objective

Finish D1 of the work package `supervision-scenarios` for the deterministic harness: the `pm-scenario` fault directives `kill_worker`, `stall_worker <seconds>`, `end_turn`, `kill_relay` and `restart_runtime` (all refused today, by name, with the package `supervision-scenarios`), the fault matrix of regression scenario 12 of Workflow DSL § Deterministic Scenario Replay Harness replayed twice to the same stores, and a runtime crash with a leased task. The faults are the facts the supervisor reports to the task protocol (the ended attempt and the fence), applied by the harness at the instant the scenario names; the supervisor's own timers, recycling and start-failure guard stay proven by `SupervisorScenarioTest` and `WorkerLossDetectionTest` in `pm-runtime` (#190), which `pm-workflow` cannot depend on. Serves: Roadmap Milestone 4. Where: pm-workflow, test scope, package `workflow.scenario`.

## Deliverables

1. **The fault directives.** `kill_worker <role>` (the worker is lost between its offer and its acknowledgement, or between its acknowledgement and its submit, as the step it follows decides), `end_turn <role>` (the worker ends its turn without a submit), `kill_relay <role>` (its relay is killed under a held wait) and `stall_worker <role> <seconds>` (the worker makes no call for the given time) are read by `ScenarioScript` and run by `ScenarioReplayRunner` on the protocol worker of PLAN-V2-33 (`scenario-task-protocol`). Each applies what the supervisor does for that fact: the `AttemptEnd` the protocol reports (`TaskProtocol.report(call, end, budget, at)`: `worker_lost`, `exited_without_submit`, `waited_again`, …) and the fence of the generation (`TaskProtocol.fence`), at the instant of the manual clock. `stall_worker` compares the seconds with the worker's compiled limits read from `WorkerParameters` (`ackDeadlineSeconds`, `silenceLimitSeconds()` of `core.profile`, read only): shorter than the limit it changes nothing and the worker is not replaced, longer it is a loss at the limit, never before it. Which fact each word maps to is set by the table of the specification (Model Work § Supervision & Recycling, Loss Detection) and recorded in one ADR; a word the table does not map is refused when the script is read.
2. **`restart_runtime`.** The runtime restart rebuilds the engine, `TaskProtocol` and the stores' objects over the same directory of the replay (the stores keep their bytes; nothing in memory survives), and the scenario continues. A lease, a fence and the queue record survive; the worker's generation does not call again until it waits.
3. **The fault matrix (regression scenario 12).** One scenario for each of: a worker killed between offer and acknowledgement, killed between acknowledgement and submit, stalled shorter and longer than its silence limit, ending its turn without a submit, and its relay killed under a held wait. In each: the task is submitted exactly once by the replacing worker; the lost worker is detected at its deadline and never before; its lease is released at the fence; every late call of the replaced generation is refused `generation_fenced` (`assert_refused`); a stalled but live worker is not replaced before its deadline; the re-offers stay within the budget and an exhausted budget closes the task `lost` with `attempts_spent`. Every scenario is replayed twice to byte-identical stores (`ScenarioDeterminismTest`).
4. **A runtime crash with a leased task.** One scenario: a worker holds a lease and has acknowledged; `restart_runtime`; the same generation waits again and still holds the lease, a second generation's wait is answered `no_task`, and the one submit closes the task once (the protocol-level fact of `TaskProtocolSubmitTest` "a lease survives a restart", now in a replayed script), and one in which the crash comes after a `kill_worker` so the successor takes the task from the released queue. The fencing scenario for a pause at once is covered by `SupervisorScenarioTest` (`aPauseAtOnceWithALeasedTask`); the harness gets the protocol half: `TaskProtocol.pausePlan` frees the lease without spending a re-offer.
5. **The tables, the documents and the package file.** `ScenarioScript.REFUSED` no longer names the fault words and `restart_runtime`; the words still unbuilt keep their work package. The developer guide `workflow-scenario.adoc` (_What Follows_ lists the worker faults as later), the specification sentence in § Deterministic Scenario Replay Harness that lists the implemented directives, `package-info.java` and one ADR (the mapping of each fault word to the supervisor's fact; where the deadlines come from) are written. `doc/plans/supervision-scenarios.adoc` is reduced to what is still owed by `plan-marshall-mcp` (the conformance harness and the comparison footprint, D6 and D7; ADR 007 of `workflow-scenario`); it is not deleted and its index row stays.

**Done when:** each fault word is read, run and replayed twice to byte-identical stores; the six scenarios of Deliverable 3 and the two of Deliverable 4 pass, and each carries the assertion for the property it names (submitted once, detected at the deadline and not before, lease released, late call refused `generation_fenced`, no replacement of a live worker); `REFUSED` and `ScenarioScriptTest` no longer name a directive this plan built; the developer guide, `package-info.java`, the ADR and the specification sentence are written; the package file names only D6 and D7 as undelivered. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (none) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:**

- Requirements: PM-TEST-4
- Specification: Workflow DSL § Deterministic Scenario Replay Harness (the simulated actor actions and the mandatory CI regression suite, item 12); Model Work § Supervision & Recycling (Loss Detection, Recycling, Ending); Store Schemas § Task Queue; Timeouts § Worker Supervision & Recycling
- Watch: none anchors on the scenario harness

## Standing Constraints

- **Log messages: none expected.** Test scope. A message the trace matrix shows goes into the holder of the module that logs it (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`) and the inbox message says so.
- **Test-first evidence:** for each fault word the progress record names the failing run (the refusal of the word) and the passing one; where the order cannot be shown, the landing says so (PLAN-35 captured none for D2 to D4).
- **No production code.** Nothing under `src/main` changes; a defect found in `TaskProtocol` or the stores is a lead in the inbox message.
- **The harness simulates the supervisor's reported facts only.** It does not re-implement loss detection, recycling or the start-failure guard (they are `pm-runtime`'s, proven there); a scenario asserts on the protocol's records, never on a supervisor state.
- **Not taken:** the same faults against real processes (`plan-marshall-mcp` / `worker-integration-tests`), the conformance harness and the comparison footprint (`pm-conformance`, ADR 007).

## Claim Labels

- OBSERVED: the fault words are refused with the package `supervision-scenarios`: `kill_worker`, `stall_worker`, `end_turn`, `kill_relay`, `restart_runtime`, `assert_worker_state`, `assert_refused` — `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioScript.java` lines 76 to 79 (read at `a8da4104`); ADR 007 of `workflow-scenario` states D1 was delivered only in part
- OBSERVED: the protocol reports the supervisor's facts through `TaskProtocol.report(TaskCall, AttemptEnd, int budget, Instant)` (line 428; reportable ends `worker_lost`, `exited_without_submit`, `waited_again`, `budget`, `start_failure`, `restart`, `drain`, `withdrawn`, `deadline_expired`), `fence(generationId, at)` (line 806; the attempt ends `restart`, the lease is released, the task is queued) and `pausePlan` (line 773) — `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java`
- OBSERVED: a lost worker spends a re-offer, a pause at once does not: `EndCause.LOST` has `spendsReoffer` true, `PAUSE_AT_ONCE` false — `pm-runtime/src/main/java/de/planmarshall/runtime/worker/EndCause.java`
- OBSERVED: the supervisor-level scenarios exist in `pm-runtime`: acknowledgement and silence at their deadline, exit after the first call, a held consultation suppresses silence, the start-failure guard, a pause at once with a leased task, the missing answer acknowledgement — `pm-runtime/src/test/java/de/planmarshall/runtime/worker/SupervisorScenarioTest.java` (13 `@DisplayName`s, lines 67 to 401); recycling in `WorkerRecyclingTest`; loss signals in `WorkerLossDetectionTest`. `pm-runtime` and `pm-workflow` do not depend on each other (`pm-runtime/pom.xml` and `pm-workflow/pom.xml` name neither), so a script of this harness cannot drive the real supervisor
- OBSERVED: `WorkerParameters` (record in `pm-core/src/main/java/de/planmarshall/core/profile/WorkerParameters.java`, line 44) carries `ackDeadlineSeconds`, `silenceGraceSeconds`, `boundedWaitSeconds` and `silenceLimitSeconds()` (line 95): the values a stall is compared with
- OBSERVED: a lease survives a restart is proven at protocol level by rebuilding `TaskProtocol` over the same `QueueStore` — `TaskProtocolSubmitTest.java` lines 117 to 132
- OBSERVED: the regression suite's item (12) lists the worker faults at every point of the task protocol and the properties each must show — `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` line 132 (the list item (12))
- HYPOTHESIS: the mapping of the words to the protocol's facts is `kill_worker` → `worker_lost` + fence, `end_turn` → `exited_without_submit` + fence (or `waited_again` where the worker calls `pm_wait` again), `kill_relay` → `worker_lost` + fence, `stall_worker` beyond the limit → `worker_lost` + fence — confirm/refute at Model Work 03 § Loss Detection and `pm-runtime/.../worker/LossDetection.java`, `WorkerEnding.java` (verify-at-outline)
- HYPOTHESIS: a fence is applied by the supervisor after the `report`, not instead of it, and the re-offer is spent by `report` (end `worker_lost` counts) while `fence` alone ends the attempt `restart` and spends none — confirm/refute at `QueueTaskRecord.AttemptEnd.counts()` (`pm-core/src/main/java/de/planmarshall/core/queue/QueueTaskRecord.java`, enum at line 344) and `WorkerEnding` (verify-at-outline)
- HYPOTHESIS: the exact set of scenario pairs of item (12) fits in `ScenarioReplayRunnerTest` without a second runner class — confirm at the outline by the line count of the test class (408 lines at `a8da4104`)
- Verify-first clause: before scoping, confirm that PLAN-V2-33 (`scenario-task-protocol`) is merged (the classes below carry its protocol worker), list the open pull requests for one that touches `workflow/scenario/`, and re-read `REFUSED`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScriptedFixtureWorker.java` — the worker's killed, ended, silent and restarted states
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioFixtures.java` — the restart rebuild, the worker parameters fixture
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioDirective.java` — the fault directives
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioScript.java` — the reader and `REFUSED`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunner.java` — runs the fault directives
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioScriptTest.java` — the `REFUSED` assertions
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunnerTest.java` — the six matrix scenarios and the crash scenarios
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioDeterminismTest.java` — each fault scenario replayed twice
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/package-info.java` — Javadoc of the package
- OBSERVED: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` — specification part; § Deterministic Scenario Replay Harness (the sentence that lists the implemented directives and the faults); not the other sections
- OBSERVED: `doc/plans/supervision-scenarios.adoc` — the package file, reduced to D6 and D7
- OBSERVED: `pm-workflow/doc/developer/workflow-scenario.adoc` — developer guide of an existing package (additive; shared bookkeeping under R19, listed because two plans edit it in sequence)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-scenario/` — one ADR, the next free number of the directory (renumber against `origin/main`, R11)
- Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-33 (`scenario-task-protocol`) (this cluster; same classes, strictly after its merge). Nothing among the other live plans.
- Overlaps with: PLAN-46 and PLAN-105 (running): no shared file. No staged plan declares a file of `workflow/scenario`. `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` is also named by PLAN-V2-44 (`positioned-layout`) (§ Positioned Layout), PLAN-V2-26 (staged) and PLAN-V2-78 (parked): 1 shared specification file with each, different sections; whichever merges second merges the base branch before the merge gate.
- Package bookkeeping (R01/R05): the work package `supervision-scenarios` stays open (D6 and D7 external); this plan reduces its file and does not delete it or its index row.
- Adjacent to: `plan-marshall-mcp` / `worker-integration-tests` (real processes), `pm-conformance` (D6, D7), the assembly `pm-mcp-server` and `pm-api`, which stay untouched.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-49-scenario-worker-faults.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
