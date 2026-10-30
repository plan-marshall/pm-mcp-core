# PLAN-V2-33: The scenario harness runs the task protocol: scripted workers with a lease and a generation

epic: pm-mcp-core-m2-m4-v2
workstream: WS-02
origin: PLAN-122 of the epic pm-mcp-core-m2-m4

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-33-scenario-task-protocol.md` and is queued as one row file, `queue/PLAN-V2-33.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.
> Origin: the first of two plans that finish the part of the work package `supervision-scenarios` that belongs to this repository
> (PLAN-35 shipped D2, D3, D4 as #229, `a8da4104`; D1 is its residue). The second is PLAN-V2-49 (`scenario-worker-faults`), which depends on this one.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r2`), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11, R12, R13, R15, R17, R18, R19. (R12 applies only if the trace matrix shows a message to add, see Standing Constraints; R14 and R16 do not apply: the plan changes test scope of `pm-workflow` only and reads `core.store` and `core.queue` without changing them; R17 because the plan runs the links check.)

Plan parameters (the values the rules ask for):

- R06 — developer documentation: `pm-workflow/doc/developer/workflow-scenario.adoc`; ADR directory: `pm-workflow/doc/adr/workflow-scenario/`; layout: workstream WS-10.
- R12 — log message holder: `WorkflowLogMessages` (`de.planmarshall.workflow.log`, prefix `PM_WORKFLOW`); none expected.

## Objective

Give the deterministic scenario harness `pm-scenario` (PM-TEST-4, package `workflow.scenario`, test scope of `pm-workflow`) the layer its worker faults need: a scripted worker that holds a generation and a lease on a real queue task and acts through the shipped `TaskProtocol` (`worker_wait`, `worker_do`), and the two supervision assertions `assert_worker_state` and `assert_refused`. Today the scripted worker settles the queue task directly ("it follows no links, holds no lease and has no generation"), the directives `worker_wait` and `worker_do` are refused with a work package that has shipped (`task-queue-and-protocol`), and nothing can say what a lease, a fence or a late call did. This is the first half of D1 of `doc/plans/supervision-scenarios.adoc` that PLAN-35 left undelivered (the other half, the fault directives, is PLAN-V2-49 (`scenario-worker-faults`)). Serves: Roadmap Milestone 4. Where: pm-workflow, test scope, package `workflow.scenario`.

## Deliverables

1. **The queue and the protocol under the replay.** The replay's stores in its temporary directory (ADR 003: no git) gain a real `QueueStore` and the lock manager `TaskProtocol` needs, and the fixture `QueueTaskCreator` writes real `QueueTaskRecord`s (today `ScenarioFixtures.RecordingQueueTaskCreator` records requests and answers `qt-%016x` without a store). A replay builds `TaskProtocol` with fences, pause, job state and skill delivery fixtures of its own (ADR 006: the scenario package has its own fixtures; `TaskFixtures` of package `workflow.task` is package-private and is not reused). Every instant comes from the manual clock, every generation id (`l-%016x`) and job id from a counter, so two replays of one script give byte-identical stores (the rule of ADR 002 and of the developer guide's "Nothing in the stored bytes may come from the machine").
2. **`worker_wait <role>` and `worker_do ack | submit:<kind> {…} | infeasible`.** A scripted worker of a role that the script declares with `worker_wait` holds its own generation, calls `TaskProtocol.wait` and receives the offer, then acts with `acknowledge`, `submit` or `infeasible` on its leased task entity, each call refused as the protocol refuses it (a `ScenarioFailure` at the step's line when a step the script expects accepted is refused). The existing direct form (`worker <role> <outcome>…`, ADR 001) keeps working for every existing scenario and for any role the script does not declare with `worker_wait`; the ADR of this plan says how the two forms are told apart and that one role never uses both. `assert_task_outcome` reads the closed outcome from the queue record for a protocol worker.
3. **`assert_worker_state` and `assert_refused`.** `assert_worker_state <role> <state>` asserts the state of the role's scripted worker's task from the queue record (`offered`, `acknowledged`, `closed` with its outcome, `queued` after a release, `fenced`), and `assert_refused <reason>` asserts that the last `worker_do` of a replaced generation was refused with that wire reason (`generation_fenced`, `job_scope_mismatch`, `stale_entity`, `link_not_available`; the closed set is `TaskRefusal`). Both fail with the line when the actual state differs, and a script that never made the call they assert on is refused when it is read, not run.
4. **The consultation scenario (regression scenario 14).** One scenario shows a consultation answered through the asker's next wait and the final submit refused when it omits the consultation's reference, through the shipped `ConsultationService` (`open`, `answer`, `validateSubmit`) over the same queue. Only the assertions the shipped service supports; where the specification asks for more (regression scenario 14 names no more than these two), the trace matrix says so and the plan does not invent it.
5. **The directive table, the documents and the package file.** `ScenarioScript.REFUSED` no longer names `worker_wait`, `worker_do`, `assert_worker_state` and `assert_refused` (the word list and `ScenarioScriptTest` change with it); the entries that stay refused name the work package that brings them (`kill_worker`, `stall_worker`, `end_turn`, `kill_relay`, `restart_runtime` name the sibling plan's package `supervision-scenarios` as today; `operator_cli`, `set_role_unqualified`, `assert_links`, `assert_skill`, `assert_rejected`, `do`, `assert_instance_version` unchanged). The developer guide `workflow-scenario.adoc` (its _What Follows_ section lists `worker_wait` and `worker_do` as later) and `package-info.java` of the scenario package describe the protocol worker. One ADR for the choices of D1/D2 (queue under the replay, how the two worker forms are told apart, who decides a generation id). The specification sentence in Workflow DSL § Deterministic Scenario Replay Harness that lists the implemented directives names the new ones. `doc/plans/supervision-scenarios.adoc`: the bullet for the scenarios of the worker faults, recycling, guard, consultation, fencing and crash (D1) is reduced to what PLAN-V2-49 (`scenario-worker-faults`) still owes; D2, D3, D4 and D5 are already out of it or are external (see Dependencies).

**Done when:** a scenario of the protocol worker (wait, offer, acknowledge, submit, closed outcome read from the record) replays twice to byte-identical stores and the existing scenarios replay unchanged; `assert_worker_state` and `assert_refused` pass on a state the run produced and fail with their line on one it did not; the consultation scenario passes with the reference refusal; the directive table and the tests that pin it (`ScenarioScriptTest` asserts `REFUSED.get("worker_wait")` today) name only directives that are still unbuilt; the developer guide, `package-info.java`, one ADR and the specification sentence are written; the package file no longer lists the work this plan did. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready. **Build:** `verify -Ppre-commit` (its rewrites reviewed and committed), then `verify` of `pm-workflow` (where a full run does not finish inside the host's bound, the targeted `-Dtest` runs of the changed classes and the per-module run, each reported in the pull request body), `verify -Pcoverage` and the integration tests of every changed module with tests (none of the changed code is in `pm-core` or `pm-runtime`) are green. **Links:** run the links check (R17) from the main checkout against the documentation checkout at its `origin/main` (`git -C ../plan-marshall-documentation fetch origin main`, then `trace.py` over a checkout of that revision, or fast-forward the sibling first): a `BROKEN LINKS` count that names an item deleted in the sibling is the sibling's own links, not a defect of this change.

**Trace starting set:**

- Requirements: PM-TEST-4
- Specification: Workflow DSL § Deterministic Scenario Replay Harness (`workflow-dsl/07-loop-back-diagrams-verification.adoc`, the directive list and regression scenario 14); Model Work § Supervision & Recycling; Store Schemas § Task Queue (`store-schemas/16-task-queue.adoc`)
- Watch: none anchors on the scenario harness (grep of `doc/implementation-watch/` for `pm-scenario` and `Deterministic Scenario` finds nothing)

## Standing Constraints

- **Log messages: none expected.** The plan is test scope. If the trace matrix shows one, it goes into the holder of the module that logs it, at the next free identifier (ADR `doc/adr/005-one-log-message-holder-per-module.adoc`), and the plan says so in its inbox message.
- **Test-first evidence (found by PLAN-35, whose lane captured no failing run for D2 to D4):** for each of D2 and D3 the progress record names the failing run (the assertion message, before the production of the code under test) and the passing run; where the order cannot be shown, the landing says so.
- **No production code.** Nothing under `src/main` changes. A defect found in `TaskProtocol`, `QueueStore` or `ConsultationService` while building the harness is reported as a lead in the inbox message, not fixed here.
- **Disjoint from PLAN-V2-49 (`scenario-worker-faults`) by order, not by file:** both edit the same scenario classes; this plan merges first, the sibling starts on its merge.

## Claim Labels

- OBSERVED: the scripted worker settles the queue task directly and has no lease or generation — `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScriptedFixtureWorker.java` class Javadoc line 29 (read at `a8da4104`)
- OBSERVED: `ScenarioScript.REFUSED` maps `worker_wait` and `worker_do` to `task-queue-and-protocol` and the seven fault and assertion words to `supervision-scenarios` — `ScenarioScript.java` lines 74 to 79; `ScenarioScriptTest.java` lines 219 to 220 pin the first and the fault word (read at `a8da4104`)
- OBSERVED: `task-queue-and-protocol` has shipped: `TaskProtocol` offers `wait` (line 195), `acknowledge` (350), `submit` (371, 398), `infeasible` (409), `report` (428), `fence` (806) and `pausePlan` (773) — `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java`
- OBSERVED: the harness fixture task creator keeps no store: `ScenarioFixtures.RecordingQueueTaskCreator` (line 512) records requests and returns `qt-%016x` ids; the lock transaction is `HoldingEverything` (line 544)
- OBSERVED: the protocol tests build their queue with `TaskFixtures.queued(...)` (package-private, `pm-workflow/src/test/java/de/planmarshall/workflow/task/TaskFixtures.java`) and `TaskProtocolSubmitTest` proves a fenced generation's late call is refused `generation_fenced` (line 90), exactly one submit closes the task (151), a lease survives a restart (117)
- OBSERVED: `ConsultationService` offers `open` (line 132), `answer` (193), `lost` (213), `facts` (351) and `validateSubmit` (364) — `pm-workflow/src/main/java/de/planmarshall/workflow/consult/ConsultationService.java`
- OBSERVED: ADR 001 of `workflow-scenario` states "`worker_wait` and `worker_do` stay refused by name until the task protocol lands" (line 43); ADR 008 states they still name `task-queue-and-protocol` and that this package does not take them (the stale text)
- OBSERVED: the package file `doc/plans/supervision-scenarios.adoc` and its index row (`open`, depends on `worker-activity` and `question-continuation`) are on `main`; ADR 007 of `workflow-scenario` moves D6 and D7 out of this repository
- HYPOTHESIS: `QueueStore` and the lock manager can run in the replay's temporary directory without git and without a real clock — confirm/refute at `pm-core/src/main/java/de/planmarshall/core/queue/QueueStore.java` and `TestLocks.java` of `workflow.task` (verify-at-outline)
- HYPOTHESIS: the engine's `QueueTaskCreator` port can write the same `QueueTaskRecord` shape `TaskFixtures.queued` builds (kind WORK, class WRITER, scope PLAN, attempts empty) from the `CreateRequest` the engine sends — confirm/refute at `WorkflowEngine.Ports` and `QueueTaskCreator.CreateRequest` (verify-at-outline)
- HYPOTHESIS: the closed set of refusal wire names for `assert_refused` equals the closed set of `TaskRefusal` — confirm/refute at `pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskRefusal.java` (verify-at-outline)
- HYPOTHESIS: regression scenario 14 needs no more of `ConsultationService` than `open`, `answer` and `validateSubmit` — confirm/refute at Workflow DSL 07 § mandatory CI regression suite item (14) (verify-at-outline)
- Verify-first clause: before scoping, list the open pull requests for one that names `supervision-scenarios` or touches `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/`, re-read the Expected Surface against `origin/main`, and re-read `ScenarioScript.REFUSED`; a refutation of any claim above loops back to re-scope.

## Expected Surface

- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScriptedFixtureWorker.java` — the protocol worker (generation, lease, calls)
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioFixtures.java` — the queue, the lock manager and the fixture task creator under the replay
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioDirective.java` — the directives `worker_wait`, `worker_do`, `assert_worker_state`, `assert_refused`
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioScript.java` — the reader and the `REFUSED` table
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunner.java` — runs the new directives
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioScriptTest.java` — the `REFUSED` assertions that change
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioReplayRunnerTest.java` — the protocol-worker, assertion and consultation scenarios
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/ScenarioDeterminismTest.java` — the byte comparison of a protocol-worker replay
- OBSERVED: `pm-workflow/src/test/java/de/planmarshall/workflow/scenario/package-info.java` — Javadoc of the package
- OBSERVED: `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` — specification part; § Deterministic Scenario Replay Harness (the sentence that lists the implemented directives); not the other sections
- OBSERVED: `doc/plans/supervision-scenarios.adoc` — the package file, reduced; deleted by no plan of this repository until D6 and D7 are delivered elsewhere
- OBSERVED: `pm-workflow/doc/developer/workflow-scenario.adoc` — developer guide of an existing package (additive; shared bookkeeping under R19, listed because two plans edit it in sequence)
- HYPOTHESIS: `pm-workflow/doc/adr/workflow-scenario/` — one ADR, the next free number of the directory (008 is the last at `a8da4104`; renumber against `origin/main`, R11)
- Not surface (R19): `doc/plans/README.adoc`, `doc/Traceability.adoc`, `doc/LogMessages.adoc`.

## Dependencies and Sequencing

- Depends on: nothing among the live plans. The shipped plans it builds on: PLAN-28 `task-queue-and-protocol` (#160), PLAN-32 `consultation` (#169), PLAN-35 D2 to D4 (#229).
- Overlaps with: PLAN-46 and PLAN-105 (landed #233 and #230): no shared file (`corpus cross-check` at emit time decides; by the declared surfaces: 0). PLAN-V2-13 (`plan-instance-versions`, staged) declares `doc/implementation-watch/workflow-dsl.adoc`, which this plan does not touch; no staged plan declares a file of `workflow/scenario`. `doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc` is also edited by the plan PLAN-V2-44 (`positioned-layout`) (a different section, § Positioned Layout) and PLAN-V2-26 and PLAN-V2-78 name it (parked or staged): 1 shared specification file with each, different sections; whichever merges second merges the base branch before the merge gate.
- Followed by: PLAN-V2-49 (`scenario-worker-faults`) (depends on this plan; same classes, strictly after the merge).
- Package bookkeeping (R01/R05): the work package `supervision-scenarios` stays open after this plan (D6 and D7 are external, ADR 007 of `workflow-scenario`); this plan edits its file and does not delete it or its index row.
- Adjacent to: `plan-marshall-mcp` / `worker-integration-tests` (the same faults against real processes; the former D5 of the package), the Quarkus assembly `pm-mcp-server` and the client contract `pm-api`, which stay untouched.

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-33-scenario-task-protocol.md
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

- **#169 changed TaskProtocol outside PLAN-32's declaration, and a staged spec declares it (2026-10** (Wa52) — #169 made workflow.task.TaskProtocol refuse a consultation task's generic submit and infeasible with link_not_available (only submit:answer); read the changed protocol and ADR workflow-consult/004 before outlining
